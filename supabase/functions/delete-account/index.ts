// POST /functions/v1/delete-account
//   {"confirm": "DELETE"}   -> schedules the deletion of the caller's account
//   {"action": "cancel"}    -> cancels a scheduled deletion
//   {"action": "status"}    -> when the account will be deleted (null when not scheduled)
//
// Deletion has a grace period (compliance setting deletion_grace_days, default 30 days):
// the person can sign in and cancel. When it ends, the daily retention-run job removes
// every uploaded file and the auth user; the database deletes the rest through
// ON DELETE CASCADE, keeps group/channel messages as "deleted user" and records the
// destruction in deletion_logs. Logs kept by law (5651 access logs, consent records)
// have no link to the account and stay only for their retention period.
// A Google Play subscription is billed by Google and must be cancelled in Google Play;
// the app and the web page say so before the person confirms.

import { authenticate, readJson } from "../_shared/auth.ts";
import { errorResponse, json, preflight, withCors } from "../_shared/http.ts";
import { isConfirmed } from "../_shared/user-files.ts";

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return preflight();
  return withCors(await handle(request));
});

async function handle(request: Request): Promise<Response> {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const caller = await authenticate(request);
  if (!caller) return errorResponse("not_authenticated", 401);

  const body = await readJson(request);
  if (body?.action === "cancel") {
    const { error } = await caller.client.rpc("cancel_account_deletion");
    if (error) {
      console.error("cancel_account_deletion failed", error.message);
      return errorResponse("server_error", 500);
    }
    return json({ scheduled_for: null });
  }
  if (body?.action === "status") {
    const { data, error } = await caller.client.rpc("my_account_deletion");
    if (error) {
      console.error("my_account_deletion failed", error.message);
      return errorResponse("server_error", 500);
    }
    return json({ scheduled_for: data ?? null });
  }
  if (!isConfirmed(body)) return errorResponse("confirmation_required", 400);

  const { data, error } = await caller.client.rpc("request_account_deletion");
  if (error) {
    console.error("request_account_deletion failed", error.message);
    return errorResponse("account_deletion_failed", 500);
  }
  return json({ scheduled_for: data });
}
