// Google Pub/Sub authenticated push. Supabase JWT verification is disabled:
// Google signature, issuer, audience, expiry and sender identity are checked here.
import { createClient } from "npm:@supabase/supabase-js@2";
import { accessToken, parseServiceAccount } from "../_shared/google-auth.ts";
import { errorResponse, json, requireEnv } from "../_shared/http.ts";
import { acknowledgeSubscription, fetchSubscription, PLAY_SCOPE, PlayApiError } from "../_shared/play.ts";
import { parseNotification, subscriptionExpiry } from "./logic.ts";
import { verifyGooglePush } from "./auth.ts";

Deno.serve(async (request) => {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);
  const audience = Deno.env.get("PLAY_RTDN_AUDIENCE");
  const sender = Deno.env.get("PLAY_RTDN_SERVICE_ACCOUNT_EMAIL");
  const packageName = Deno.env.get("ANDROID_PACKAGE_NAME");
  const account = parseServiceAccount(Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT"));
  if (!audience || !sender || !packageName || !account) return errorResponse("billing_not_configured", 503);
  const bearer = request.headers.get("authorization")?.match(/^Bearer (.+)$/i)?.[1];
  if (!bearer) return errorResponse("not_authenticated", 401);
  if (!await verifyGooglePush(bearer, audience, sender)) return errorResponse("not_authenticated", 401);
  let body: unknown;
  try { body = await request.json(); } catch { return errorResponse("invalid_request", 400); }
  const notification = parseNotification(body, packageName);
  if (!notification || notification.eventTime > Date.now() + 900000) return errorResponse("invalid_request", 400);
  if (!notification.token) return json({ received: true });
  const admin = createClient(requireEnv("SUPABASE_URL"), requireEnv("SUPABASE_SERVICE_ROLE_KEY"), { auth: { persistSession: false } });
  const { data: owners, error: lookupError } = await admin.rpc("billing_owner_for_token", { p_purchase_token: notification.token });
  if (lookupError) return errorResponse("server_error", 500);
  const owner = owners?.[0];
  // New purchases are granted through verify-purchase after the app's consent
  // flow. RTDN only reconciles tokens already bound to an account.
  if (!owner) return json({ received: true });
  let expiry: string | null;
  let oauthToken = "";
  let needsAcknowledge = false;
  try {
    const oauth = await accessToken(fetch, account, PLAY_SCOPE, Math.floor(Date.now() / 1000));
    oauthToken = oauth.token;
    const subscription = await fetchSubscription(fetch, oauth.token, packageName, notification.token);
    expiry = subscriptionExpiry(subscription, owner.product_id, owner.user_id, new Date());
    needsAcknowledge = expiry !== null && (subscription as { acknowledgementState?: string }).acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING";
  } catch (error) {
    if (error instanceof PlayApiError && [404, 410].includes(error.status)) expiry = null;
    else return errorResponse("billing_unavailable", 502); // Pub/Sub retries transient failures.
  }
  const { error } = await admin.rpc("reconcile_billing_entitlement", {
    p_purchase_token: notification.token, p_expires_at: expiry, p_event_time_ms: notification.eventTime,
  });
  if (error) return errorResponse("server_error", 500);
  if (needsAcknowledge) {
    try {
      await acknowledgeSubscription(fetch, oauthToken, packageName, owner.product_id, notification.token);
    } catch {
      return errorResponse("billing_unavailable", 502);
    }
  }
  return json({ received: true });
});
