// POST /functions/v1/submit-course-note
//   {"path": "<user id>/<uuid>.pdf", "course_code": "MAT101", "course_name": "...", "title": "...", "description": "...",
//    "rights_declared": true}
//
// Called by the app after it uploaded a PDF to the private `course-notes`
// bucket. Checks that the stored bytes are a real PDF within 20 MB, then
// records the note with the service role; it is then visible to students of
// the uploader's university. Invalid uploads are deleted. The uploader must declare that
// they hold the rights to the content (rights_declared, recorded with the note).

import { authenticate, readJson } from "../_shared/auth.ts";
import { errorResponse, json } from "../_shared/http.ts";
import { checkNote, mapNoteError, parseNoteRequest } from "./logic.ts";

const BUCKET = "course-notes";

Deno.serve(async (request) => {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const caller = await authenticate(request);
  if (!caller) return errorResponse("not_authenticated", 401);
  const { userId, admin } = caller;

  const input = parseNoteRequest(await readJson(request), userId);
  if (!input) return errorResponse("invalid_request", 400);

  const { data: file, error: downloadError } = await admin.storage.from(BUCKET).download(input.path);
  if (downloadError || !file) return errorResponse("document_not_found", 404);

  const bytes = new Uint8Array(await file.arrayBuffer());
  const check = checkNote(bytes);
  if (check !== "ok") {
    const { error: removeError } = await admin.storage.from(BUCKET).remove([input.path]);
    if (removeError) console.error("Could not remove rejected upload", removeError.message);
    return errorResponse(check === "too_large" ? "document_too_large" : "invalid_document", 422);
  }

  const { data: noteId, error } = await admin.rpc("create_course_note", {
    p_user_id: userId,
    p_course_code: input.courseCode,
    p_course_name: input.courseName,
    p_title: input.title,
    p_description: input.description,
    p_path: input.path,
    p_size: bytes.length,
    p_rights_declared: input.rightsDeclared,
  });
  if (error) {
    const mapped = mapNoteError(error.message);
    if (mapped.status >= 500) console.error("create_course_note failed", error.message);
    // The note was not recorded; the file would never be shown.
    const { error: removeError } = await admin.storage.from(BUCKET).remove([input.path]);
    if (removeError) console.error("Could not remove unrecorded upload", removeError.message);
    return errorResponse(mapped.code, mapped.status);
  }

  return json({ note_id: noteId }, 201);
});
