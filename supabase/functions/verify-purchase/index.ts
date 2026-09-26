// POST /functions/v1/verify-purchase  {"product_id": "...", "purchase_token": "..."}
//
// Called by the app after a Google Play purchase (and on restore). Verifies
// the token with the Google Play Developer API, records the entitlement with
// the service role and acknowledges the purchase. Nothing is granted without
// Google's confirmation.

import { authenticate, readJson } from "../_shared/auth.ts";
import { accessToken, parseServiceAccount } from "../_shared/google-auth.ts";
import { errorResponse, json } from "../_shared/http.ts";
import { acknowledgeSubscription, evaluateSubscription, fetchSubscription, PLAY_SCOPE, PlayApiError } from "../_shared/play.ts";

Deno.serve(async (request) => {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const caller = await authenticate(request);
  if (!caller) return errorResponse("not_authenticated", 401);

  const body = await readJson(request);
  const productId = body?.product_id, purchaseToken = body?.purchase_token;
  if (typeof productId !== "string" || typeof purchaseToken !== "string" || purchaseToken.length < 10 || purchaseToken.length > 4096) {
    return errorResponse("invalid_request", 400);
  }

  const account = parseServiceAccount(Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT"));
  const packageName = Deno.env.get("ANDROID_PACKAGE_NAME");
  if (!account || !packageName) {
    console.error("Google Play verification is not configured (GOOGLE_PLAY_SERVICE_ACCOUNT / ANDROID_PACKAGE_NAME)");
    return errorResponse("billing_not_configured", 503);
  }

  const { data: planId, error: planError } = await caller.admin.rpc("active_plan_for_product", { p_product_id: productId });
  if (planError) {
    console.error("active_plan_for_product failed", planError.message);
    return errorResponse("server_error", 500);
  }
  if (!planId) return errorResponse("plan_not_available", 404);

  let token: string;
  let subscription: unknown;
  try {
    token = (await accessToken(fetch, account, PLAY_SCOPE, Math.floor(Date.now() / 1000))).token;
    subscription = await fetchSubscription(fetch, token, packageName, purchaseToken);
  } catch (error) {
    if (error instanceof PlayApiError && (error.status === 400 || error.status === 404 || error.status === 410)) {
      return errorResponse("purchase_not_active", 422);
    }
    console.error("Google Play verification failed", error instanceof Error ? error.message : String(error));
    return errorResponse("billing_unavailable", 502);
  }

  const check = evaluateSubscription(subscription, productId, caller.userId, new Date());
  if (!check.ok) return errorResponse(check.code, check.code === "purchase_not_for_account" ? 403 : 422);

  const { error: recordError } = await caller.admin.rpc("record_entitlement", {
    p_user_id: caller.userId,
    p_product_id: productId,
    p_purchase_token: purchaseToken,
    p_expires_at: check.expiresAt,
  });
  if (recordError) {
    const code = recordError.message;
    if (code === "purchase_belongs_to_another_account") return errorResponse("purchase_not_for_account", 403);
    if (code === "plan_not_available" || code === "purchase_not_active") return errorResponse(code, 422);
    console.error("record_entitlement failed", code);
    return errorResponse("server_error", 500);
  }

  // Only after the entitlement is stored, so a failure here is retried by the next restore.
  if (check.needsAcknowledge) {
    try {
      await acknowledgeSubscription(fetch, token, packageName, productId, purchaseToken);
    } catch (error) {
      console.error("Acknowledge failed; the next verification retries it", error instanceof Error ? error.message : String(error));
    }
  }
  return json({ expires_at: check.expiresAt });
});
