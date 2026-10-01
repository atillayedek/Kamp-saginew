// POST /functions/v1/retention-run
//
// Daily destruction job (KVKK periodic destruction, D57). Called by the database itself
// (pg_cron -> run_daily_retention -> pg_net) with the x-webhook-secret only the database
// and the service role know, after the database has purged expired log rows.
//   * student documents whose retention after the verdict ended: file removed, verdict kept
//   * data exports older than their retention: files removed
//   * accounts whose deletion grace period ended, or inactive accounts warned long enough
//     ago: files removed, group messages anonymised, auth user deleted (CASCADE)
//   * inactive accounts: warning e-mail (Resend), deletion after inactive_notice_days
// Every destruction is recorded in deletion_logs; the run itself in retention_runs.

import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { isValidSender, sendBatches } from "../_shared/broadcast.ts";
import { safeEqual } from "../_shared/fcm.ts";
import { errorResponse, json, requireEnv } from "../_shared/http.ts";
import { removeUserFiles } from "../_shared/user-files.ts";
import { inactiveNoticeText, type RetentionDue, runRetention } from "./logic.ts";

Deno.serve(async (request) => {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const provided = request.headers.get("x-webhook-secret") ?? "";
  if (!provided) return errorResponse("not_authenticated", 401);
  const admin = createClient(requireEnv("SUPABASE_URL"), requireEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false },
  });
  const { data: secret, error: secretError } = await admin.rpc("push_webhook_secret");
  if (secretError || typeof secret !== "string") {
    console.error("push_webhook_secret failed", secretError?.message ?? "no secret");
    return errorResponse("server_error", 500);
  }
  if (!safeEqual(provided, secret)) return errorResponse("not_authenticated", 401);

  const startedAt = new Date().toISOString();
  let sqlSummary: unknown = null;
  try {
    sqlSummary = (await request.json() as { sql?: unknown }).sql ?? null;
  } catch (_error) {
    sqlSummary = null;
  }

  const { data: due, error: dueError } = await admin.rpc("retention_due");
  if (dueError || !due) {
    console.error("retention_due failed", dueError?.message);
    await record(admin, startedAt, { sql: sqlSummary }, `retention_due failed: ${dueError?.message}`);
    return errorResponse("server_error", 500);
  }

  const summary = await runRetention(due as RetentionDue, {
    removeFiles: async (bucket, paths) => {
      const { error } = await admin.storage.from(bucket).remove(paths);
      if (error) throw new Error(error.message);
    },
    recordDocumentPurged: (id) => call(admin, "record_document_purged", { p_verification_id: id }),
    recordExportDeleted: (id) => call(admin, "record_export_deleted", { p_export_id: id }),
    recordInactiveNotice: (id) => call(admin, "record_inactive_notice", { p_user_id: id }),
    sendInactiveNotice: (email, days) => sendNotice(email, days),
    deleteAccount: async (userId, reason) => {
      await removeUserFiles(admin, userId);
      await call(admin, "prepare_account_deletion", { p_user_id: userId, p_reason: reason });
      const { error } = await admin.auth.admin.deleteUser(userId);
      if (error) throw new Error(error.message);
    },
  });

  if (summary.errors.length > 0) console.error(`Retention run finished with ${summary.errors.length} error(s)`);
  await record(admin, startedAt, { sql: sqlSummary, ...summary }, summary.errors.length > 0 ? summary.errors.join("\n") : null);
  return json(summary);
});

async function call(admin: SupabaseClient, fn: string, args: Record<string, unknown>): Promise<void> {
  const { error } = await admin.rpc(fn, args);
  if (error) throw new Error(`${fn}: ${error.message}`);
}

async function record(admin: SupabaseClient, startedAt: string, summary: Record<string, unknown>, error: string | null) {
  const { error: recordError } = await admin.rpc("record_retention_run", {
    p_started_at: startedAt,
    p_summary: summary,
    p_error: error,
  });
  if (recordError) console.error("record_retention_run failed", recordError.message);
}

async function sendNotice(email: string, days: number): Promise<boolean> {
  const apiKey = Deno.env.get("RESEND_API_KEY");
  const from = Deno.env.get("RESEND_FROM");
  if (!apiKey || !from || !isValidSender(from)) return false;
  const text = inactiveNoticeText(days, Deno.env.get("PUBLIC_SITE_URL") ?? null);
  const result = await sendBatches(
    fetch,
    apiKey,
    [{ from, to: [email], subject: text.subject, html: text.html, text: text.text }],
    (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
    (message) => console.error(message),
  );
  if (result.failed > 0) throw new Error("e-mail not sent");
  return true;
}
