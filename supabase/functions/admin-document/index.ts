// POST /functions/v1/admin-document   {"verification_id": "<uuid>"}
//
// The only way to open a student document. Verifiers (or superadmins) in an MFA session
// get a 5-minute signed link; every opening is recorded in admin_audit_logs with the
// reason the panel sends (x-audit-reason). Documents already destroyed after the
// retention period return 410.

import { auditReason, authenticate, readJson, recordStaffAction, staffRoles } from "../_shared/auth.ts";
import { errorResponse, json, preflight, withCors } from "../_shared/http.ts";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const LINK_SECONDS = 300;

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return preflight();
  return withCors(await handle(request));
});

async function handle(request: Request): Promise<Response> {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const caller = await authenticate(request);
  if (!caller) return errorResponse("not_authenticated", 401);
  const roles = await staffRoles(caller, ["verifier"]);
  if (!roles) return errorResponse("admin_required", 403);

  const body = await readJson(request);
  const id = body?.verification_id;
  if (typeof id !== "string" || !UUID.test(id)) return errorResponse("invalid_request", 400);
  const reason = auditReason(request);
  if (!reason) return errorResponse("audit_reason_required", 400);

  const { data: rows, error } = await caller.admin
    .from("student_verifications")
    .select("id, user_id, document_path, document_purged_at")
    .eq("id", id)
    .limit(1);
  if (error) {
    console.error("Reading the verification failed", error.message);
    return errorResponse("server_error", 500);
  }
  const verification = rows?.[0];
  if (!verification) return errorResponse("verification_not_found", 404);
  if (verification.document_purged_at) return errorResponse("document_destroyed", 410);

  // Record first: no audit row, no document.
  if (!await recordStaffAction(caller, roles, request, "view.student_document", { type: "student_verifications", id }, reason, {
    subject_user_id: verification.user_id,
    link_seconds: LINK_SECONDS,
  })) {
    return errorResponse("server_error", 500);
  }

  const { data: signed, error: signError } = await caller.admin.storage
    .from("student-documents")
    .createSignedUrl(verification.document_path, LINK_SECONDS);
  if (signError || !signed) {
    console.error("Signing the document link failed", signError?.message);
    return errorResponse("document_not_found", 404);
  }
  return json({ url: signed.signedUrl, expires_in: LINK_SECONDS });
}
