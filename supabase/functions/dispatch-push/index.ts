// POST /functions/v1/dispatch-push
//
// Called by a Supabase Database Webhook on INSERT into public.notifications
// with header `x-webhook-secret: <PUSH_WEBHOOK_SECRET>`. Sends the notification
// to the person's devices through FCM and forgets tokens FCM no longer knows.

import { createClient } from "npm:@supabase/supabase-js@2";
import { accessToken, parseServiceAccount, pushText, safeEqual, sendToDevice } from "../_shared/fcm.ts";
import { errorResponse, json, requireEnv } from "../_shared/http.ts";

let cachedToken: { token: string; expiresAt: number } | null = null;

Deno.serve(async (request) => {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const secret = Deno.env.get("PUSH_WEBHOOK_SECRET");
  const provided = request.headers.get("x-webhook-secret") ?? "";
  if (!secret || !safeEqual(provided, secret)) return errorResponse("not_authenticated", 401);

  let notificationId: unknown;
  try {
    const body = await request.json() as { type?: unknown; table?: unknown; record?: { id?: unknown } };
    if (body.type !== "INSERT" || body.table !== "notifications") return json({ skipped: true });
    notificationId = body.record?.id;
  } catch (_error) {
    return errorResponse("invalid_request", 400);
  }
  if (typeof notificationId !== "string") return errorResponse("invalid_request", 400);

  const account = parseServiceAccount(Deno.env.get("FCM_SERVICE_ACCOUNT"));
  if (!account) {
    console.error("FCM is not configured (FCM_SERVICE_ACCOUNT)");
    return errorResponse("push_not_configured", 503);
  }

  const admin = createClient(requireEnv("SUPABASE_URL"), requireEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false },
  });
  const { data, error } = await admin.rpc("push_payload", { p_notification_id: notificationId });
  if (error) {
    console.error("push_payload failed", error.message);
    return errorResponse("server_error", 500);
  }
  const payload = (data as { kind: string; actor_name: string | null; conversation_id: string | null; post_id: string | null; tokens: string[] }[])[0];
  // Already read (or deleted) before the webhook ran, or no device registered.
  if (!payload || payload.tokens.length === 0) return json({ sent: 0 });

  const text = pushText(payload.kind, payload.actor_name);
  if (!text) return json({ sent: 0 });

  const now = Math.floor(Date.now() / 1000);
  try {
    if (!cachedToken || cachedToken.expiresAt - 60 < now) cachedToken = await accessToken(fetch, account, now);
  } catch (tokenError) {
    console.error("FCM OAuth failed", tokenError instanceof Error ? tokenError.message : String(tokenError));
    return errorResponse("push_failed", 502);
  }

  const data_: Record<string, string> = { kind: payload.kind, notification_id: notificationId };
  if (payload.conversation_id) data_.conversation_id = payload.conversation_id;
  if (payload.post_id) data_.post_id = payload.post_id;

  const results = await Promise.all(
    payload.tokens.map((token) => sendToDevice(fetch, account.projectId, cachedToken!.token, token, { ...text, data: data_ })),
  );
  const unregistered = payload.tokens.filter((_, i) => results[i] === "unregistered");
  if (unregistered.length > 0) {
    const { error: deleteError } = await admin.rpc("delete_device_tokens", { p_tokens: unregistered });
    if (deleteError) console.error("delete_device_tokens failed", deleteError.message);
  }
  const failed = results.filter((r) => r === "failed").length;
  if (failed > 0) console.error(`FCM send failed for ${failed} device(s)`);
  return json({ sent: results.filter((r) => r === "sent").length, unregistered: unregistered.length, failed });
});
