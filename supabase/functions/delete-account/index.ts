// POST /functions/v1/delete-account  {"confirm": "DELETE"}
//
// Permanently deletes the caller's account. Uploaded student documents are
// removed from storage first, then the auth user is deleted; everything in
// the database that belongs to the person (profile, posts, comments, likes,
// requirements, chats, notifications, device tokens, blocks, reports,
// entitlements) goes with it through ON DELETE CASCADE. A Google Play
// subscription is billed by Google and must be cancelled in Google Play; the
// app says so before the person confirms.

import { authenticate, readJson } from "../_shared/auth.ts";
import { errorResponse, json } from "../_shared/http.ts";
import { collectUserFiles, isConfirmed } from "./logic.ts";

const BUCKET = "student-documents";

Deno.serve(async (request) => {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const caller = await authenticate(request);
  if (!caller) return errorResponse("not_authenticated", 401);
  const { userId, admin } = caller;

  if (!isConfirmed(await readJson(request))) return errorResponse("confirmation_required", 400);

  let paths: string[];
  try {
    paths = await collectUserFiles(userId, async (folder, offset, limit) => {
      const { data, error } = await admin.storage.from(BUCKET).list(folder, { offset, limit });
      if (error) throw new Error(error.message);
      return (data ?? []).map((item) => item.name);
    });
  } catch (error) {
    console.error("Listing documents failed", error instanceof Error ? error.message : String(error));
    return errorResponse("account_deletion_failed", 500);
  }

  if (paths.length > 0) {
    const { error: removeError } = await admin.storage.from(BUCKET).remove(paths);
    if (removeError) {
      console.error("Removing documents failed", removeError.message);
      return errorResponse("account_deletion_failed", 500);
    }
  }

  const { error: deleteError } = await admin.auth.admin.deleteUser(userId);
  if (deleteError) {
    console.error("Deleting the auth user failed", deleteError.message);
    return errorResponse("account_deletion_failed", 500);
  }

  return json({ deleted: true });
});
