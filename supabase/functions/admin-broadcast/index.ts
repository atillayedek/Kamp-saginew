// POST /functions/v1/admin-broadcast
//   {"subject": "...", "body": "...", "audience": "ALL", "marketing": false, "test_only": false}
//
// Sends an e-mail to a group of students through Resend. Superadmins only, in an
// MFA session, with a reason (x-audit-reason); every send is recorded in
// admin_audit_logs. Marketing mail goes only to people who opted in and
// carries a signed unsubscribe link; suspended accounts never receive mail.
// With test_only the mail goes to the calling admin alone and is not logged.

import { auditReason, authenticate, readJson, recordStaffAction, staffRoles } from "../_shared/auth.ts";
import {
  broadcastStatus,
  isValidSender,
  type OutgoingEmail,
  parseBroadcastRequest,
  type Recipient,
  renderHtml,
  renderText,
  sendBatches,
  signUnsubscribe,
  unsubscribeUrl,
} from "../_shared/broadcast.ts";
import { errorResponse, json, preflight, requireEnv, withCors } from "../_shared/http.ts";

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return preflight();
  return withCors(await handle(request));
});

async function handle(request: Request): Promise<Response> {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const caller = await authenticate(request);
  if (!caller) return errorResponse("not_authenticated", 401);
  const roles = await staffRoles(caller, ["superadmin"]);
  if (!roles) return errorResponse("admin_required", 403);

  const input = parseBroadcastRequest(await readJson(request));
  if (!input) return errorResponse("invalid_request", 400);
  const reason = auditReason(request);
  if (!reason && !input.testOnly) return errorResponse("audit_reason_required", 400);

  const apiKey = Deno.env.get("RESEND_API_KEY");
  const from = Deno.env.get("RESEND_FROM");
  const siteUrl = Deno.env.get("PUBLIC_SITE_URL");
  const unsubscribeSecret = Deno.env.get("UNSUBSCRIBE_SECRET");
  if (!apiKey || !from || (input.marketing && (!siteUrl || !unsubscribeSecret))) {
    console.error("E-mail is not configured (RESEND_API_KEY / RESEND_FROM, and PUBLIC_SITE_URL / UNSUBSCRIBE_SECRET for marketing)");
    return errorResponse("email_not_configured", 503);
  }

  if (!isValidSender(from)) {
    console.error("RESEND_FROM is not `email@domain` or `Name <email@domain>`");
    return errorResponse("email_sender_invalid", 503);
  }

  let recipients: Recipient[];
  if (input.testOnly) {
    if (!caller.email) return errorResponse("invalid_request", 400);
    recipients = [{ userId: caller.userId, email: caller.email }];
  } else {
    const { data, error } = await caller.admin.rpc("broadcast_recipients", {
      p_audience: input.audience,
      p_marketing: input.marketing,
    });
    if (error) {
      console.error("broadcast_recipients failed", error.message);
      return errorResponse("server_error", 500);
    }
    recipients = (data as { user_id: string; email: string }[]).map((r) => ({ userId: r.user_id, email: r.email }));
  }
  if (recipients.length === 0) return errorResponse("no_recipients", 422);

  const emails: OutgoingEmail[] = [];
  for (const recipient of recipients) {
    let link: string | null = null;
    const email: OutgoingEmail = { from, to: [recipient.email], subject: input.subject, html: "", text: "" };
    if (input.marketing) {
      const signature = await signUnsubscribe(unsubscribeSecret!, recipient.userId);
      link = unsubscribeUrl(siteUrl!, recipient.userId, signature);
      const oneClick = new URL(`${requireEnv("SUPABASE_URL")}/functions/v1/email-unsubscribe`);
      oneClick.searchParams.set("u", recipient.userId);
      oneClick.searchParams.set("s", signature);
      email.headers = { "List-Unsubscribe": `<${oneClick}>`, "List-Unsubscribe-Post": "List-Unsubscribe=One-Click" };
    }
    email.html = renderHtml(input.body, link);
    email.text = renderText(input.body, link);
    emails.push(email);
  }

  if (input.testOnly) {
    const result = await sendBatches(fetch, apiKey, emails, sleep, (m) => console.error(m));
    if (result.failed > 0) return errorResponse("email_send_failed", 502);
    return json({ recipient_count: 1, sent_count: 1, failed_count: 0, status: "SENT" });
  }

  const { data: row, error: insertError } = await caller.admin.from("email_broadcasts").insert({
    sent_by: caller.userId,
    audience: input.audience,
    marketing: input.marketing,
    subject: input.subject,
    body: input.body,
    recipient_count: recipients.length,
  }).select("id").single();
  if (insertError || !row) {
    console.error("Could not log the broadcast", insertError?.message);
    return errorResponse("server_error", 500);
  }

  if (!await recordStaffAction(caller, roles, request, "email.broadcast", { type: "email_broadcasts", id: row.id }, reason!, {
    audience: input.audience,
    marketing: input.marketing,
    recipients: recipients.length,
  })) {
    return errorResponse("server_error", 500);
  }

  const result = await sendBatches(fetch, apiKey, emails, sleep, (m) => console.error(m));
  const status = broadcastStatus(result);
  const { error: updateError } = await caller.admin.from("email_broadcasts").update({
    sent_count: result.sent,
    failed_count: result.failed,
    status,
    finished_at: new Date().toISOString(),
  }).eq("id", row.id);
  if (updateError) console.error("Could not record the broadcast result", updateError.message);

  return json({ id: row.id, recipient_count: recipients.length, sent_count: result.sent, failed_count: result.failed, status });
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
