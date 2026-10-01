// POST /functions/v1/submit-student-document  {"path": "<user id>/<uuid>.pdf"}
//
// Called by the app after it uploaded the PDF to the private
// `student-documents` bucket. Verifies the caller, checks the stored bytes are
// a real PDF within the size limit, then records the request with the service
// role. Invalid uploads are deleted so they never reach an admin. The document's SHA-256
// is kept so the verdict stays verifiable after the file is destroyed.

import { authenticate, readJson } from "../_shared/auth.ts";
import { errorResponse, json } from "../_shared/http.ts";
import { checkDocument, isOwnDocumentPath, mapSubmitError } from "./logic.ts";

const BUCKET = "student-documents";

Deno.serve(async (request) => {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const caller = await authenticate(request);
  if (!caller) return errorResponse("not_authenticated", 401);
  const { userId, admin } = caller;

  const body = await readJson(request);
  if (!body) return errorResponse("invalid_request", 400);
  if (!isOwnDocumentPath(body.path, userId)) return errorResponse("invalid_document_path", 400);
  const path = body.path;

  const { data: file, error: downloadError } = await admin.storage.from(BUCKET).download(path);
  if (downloadError || !file) return errorResponse("document_not_found", 404);

  const bytes = new Uint8Array(await file.arrayBuffer());
  const check = checkDocument(bytes);
  if (check !== "ok") {
    const { error: removeError } = await admin.storage.from(BUCKET).remove([path]);
    if (removeError) console.error("Could not remove rejected upload", removeError.message);
    return errorResponse(check === "too_large" ? "document_too_large" : "invalid_document", 422);
  }

  const { data: verificationId, error: submitError } = await admin.rpc("submit_student_document", {
    p_user_id: userId,
    p_path: path,
  });
  if (submitError) {
    const mapped = mapSubmitError(submitError.message);
    if (mapped.status >= 500) console.error("submit_student_document failed", submitError.message);
    return errorResponse(mapped.code, mapped.status);
  }

  // Only the fingerprint outlives the file (KVKK: the PDF is destroyed after the verdict).
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
  const sha256 = Array.from(digest, (b) => b.toString(16).padStart(2, "0")).join("");
  const { error: hashError } = await admin.rpc("set_student_document_sha256", { p_user_id: userId, p_path: path, p_sha256: sha256 });
  if (hashError) console.error("set_student_document_sha256 failed", hashError.message);

  return json({ verification_id: verificationId }, 201);
});
