// Pure validation rules for student documents, shared by the Edge Function
// and its tests. The database repeats the path check (defence in depth).

export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;

const PATH = /^([0-9a-f-]{36})\/[0-9a-f-]{36}\.pdf$/;

/** The object must sit in the caller's own folder with a generated name. */
export function isOwnDocumentPath(path: unknown, userId: string): path is string {
  if (typeof path !== "string") return false;
  const match = PATH.exec(path);
  return match !== null && match[1] === userId;
}

export type DocumentCheck = "ok" | "empty" | "too_large" | "not_pdf";

/**
 * Checks the stored bytes, not the declared MIME type: a real PDF starts with
 * "%PDF-" (optionally after a UTF-8 BOM or whitespace in the first KB, which
 * PDF readers accept).
 */
export function checkDocument(bytes: Uint8Array): DocumentCheck {
  if (bytes.length === 0) return "empty";
  if (bytes.length > MAX_DOCUMENT_BYTES) return "too_large";
  const head = new TextDecoder("latin1").decode(bytes.subarray(0, 1024));
  const index = head.indexOf("%PDF-");
  if (index < 0) return "not_pdf";
  // Only whitespace or a BOM may precede the header.
  const before = head.slice(0, index).replace(/^﻿|^\xEF\xBB\xBF/, "");
  return before.trim() === "" ? "ok" : "not_pdf";
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
