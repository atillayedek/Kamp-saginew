// POST /functions/v1/admin-push   (staff in an MFA session; the database checks the role)
//   {"action": "status"}            -> is push (and the daily retention job) set up?
//   {"action": "setup"}             -> records this project's functions URL so the
//                                      database can call dispatch-push (D56) and
//                                      retention-run (D57); superadmin or compliance
//   {"announcement_id": "<uuid>", "marketing": false}
//                                   -> superadmin: sends an active announcement to the phones of
//                                      the approved students it is meant for, once. A marketing
//                                      announcement goes only to people with the push consent.
// Tokens FCM no longer knows are removed.

import { auditReason, authenticate, readJson, recordStaffAction, staffRoles } from "../_shared/auth.ts";
import { announcementText, fcmAccessToken, sendToDevices } from "../_shared/fcm.ts";
import { parseServiceAccount } from "../_shared/google-auth.ts";
import { errorResponse, json, preflight, requireEnv, withCors } from "../_shared/http.ts";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return preflight();
  return withCors(await handle(request));
});

async function handle(request: Request): Promise<Response> {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const caller = await authenticate(request);
  if (!caller) return errorResponse("not_authenticated", 401);
  const input = await readJson(request);
  const fcmConfigured = parseServiceAccount(Deno.env.get("FCM_SERVICE_ACCOUNT")) !== null;

  if (input?.action === "status" || input?.action === "setup") {
    const roles = await staffRoles(caller, input.action === "setup" ? ["compliance"] : ["verifier", "moderator", "compliance"]);
    if (!roles) return errorResponse("admin_required", 403);
    if (input.action === "setup") {
      const url = `${requireEnv("SUPABASE_URL").replace(/\/+$/, "")}/functions/v1`;
      const { error } = await caller.admin.rpc("set_push_functions_url", { p_url: url });
      if (error) {
        console.error("set_push_functions_url failed", error.message);
        return errorResponse("server_error", 500);
      }
    }
    const { data, error } = await caller.admin.rpc("push_status");
    if (error) {
      console.error("push_status failed", error.message);
      return errorResponse("server_error", 500);
    }
    const status = (data as { trigger_ready: boolean; pg_net_installed: boolean }[])[0];
    return json({
      fcm_configured: fcmConfigured,
      trigger_ready: status?.trigger_ready ?? false,
      pg_net_installed: status?.pg_net_installed ?? false,
    });
  }

  const roles = await staffRoles(caller, ["superadmin"]);
  if (!roles) return errorResponse("admin_required", 403);
  const announcementId = input?.announcement_id;
  if (typeof announcementId !== "string" || !UUID.test(announcementId)) return errorResponse("invalid_request", 400);
  const marketing = input?.marketing === true;
  const reason = auditReason(request);
  if (!reason) return errorResponse("audit_reason_required", 400);

  const account = parseServiceAccount(Deno.env.get("FCM_SERVICE_ACCOUNT"));
  if (!account) {
    console.error("FCM is not configured (FCM_SERVICE_ACCOUNT)");
    return errorResponse("push_not_configured", 503);
  }

  let accessToken: string;
  try {
    accessToken = (await fcmAccessToken(fetch, account, Math.floor(Date.now() / 1000))).token;
  } catch (tokenError) {
    console.error("FCM OAuth failed", tokenError instanceof Error ? tokenError.message : String(tokenError));
    return errorResponse("push_failed", 502);
  }

  // Claimed only after FCM is reachable, so a configuration error does not use up the one push.
  const { data, error } = await caller.admin.rpc("claim_announcement_push", {
    p_announcement_id: announcementId,
    p_marketing: marketing,
  });
  if (error) {
    for (const code of ["announcement_already_pushed", "announcement_not_found"]) {
      if (error.message.includes(code)) return errorResponse(code, 409);
    }
    console.error("claim_announcement_push failed", error.message);
    return errorResponse("server_error", 500);
  }
  const target = (data as { title: string; body: string; tokens: string[] }[])[0];
  const tokens = target?.tokens ?? [];
  await recordStaffAction(caller, roles, request, "push.announcement", { type: "announcements", id: announcementId }, reason, {
    marketing,
    devices: tokens.length,
  });

  const results = await sendToDevices(fetch, account.projectId, accessToken, tokens, {
    ...announcementText(target.title, target.body),
    // The app shows marketing on its own channel with a way to turn it off (6563).
    data: { kind: "ANNOUNCEMENT", announcement_id: announcementId, channel: marketing ? "marketing" : "service" },
  });
  const sent = results.filter((r) => r === "sent").length;
  const unregistered = tokens.filter((_, i) => results[i] === "unregistered");
  if (unregistered.length > 0) {
    const { error: deleteError } = await caller.admin.rpc("delete_device_tokens", { p_tokens: unregistered });
    if (deleteError) console.error("delete_device_tokens failed", deleteError.message);
  }
  const { error: recordError } = await caller.admin.rpc("record_announcement_push", {
    p_announcement_id: announcementId,
    p_sent: sent,
  });
  if (recordError) console.error("record_announcement_push failed", recordError.message);

  const failed = results.filter((r) => r === "failed").length;
  if (failed > 0) console.error(`FCM send failed for ${failed} device(s)`);
  return json({ devices: tokens.length, sent, failed, unregistered: unregistered.length });
}
