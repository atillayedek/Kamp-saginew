// Pure validation rules for student documents, shared by the Edge Function
// and its tests. The database repeats the path check (defence in depth).

import { checkPdf, type PdfCheck } from "../_shared/pdf.ts";

export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;

const PATH = /^([0-9a-f-]{36})\/[0-9a-f-]{36}\.pdf$/;

/** The object must sit in the caller's own folder with a generated name. */
export function isOwnDocumentPath(path: unknown, userId: string): path is string {
  if (typeof path !== "string") return false;
  const match = PATH.exec(path);
  return match !== null && match[1] === userId;
}

export type DocumentCheck = PdfCheck;

/** A real PDF of at most [MAX_DOCUMENT_BYTES]. */
export function checkDocument(bytes: Uint8Array): DocumentCheck {
  return checkPdf(bytes, MAX_DOCUMENT_BYTES);
}

/** Maps the database's P0001 messages to HTTP responses. */
export function mapSubmitError(message: string | undefined): { code: string; status: number } {
  switch (message) {
    case "invalid_document_path":
      return { code: "invalid_document_path", status: 400 };
    case "document_not_found":
      return { code: "document_not_found", status: 404 };
    case "verification_not_allowed":
    case "verification_already_pending":
      return { code: "verification_not_allowed", status: 409 };
    case "profile_not_found":
      return { code: "profile_not_found", status: 404 };
    default:
      return { code: "server_error", status: 500 };
  }
}
