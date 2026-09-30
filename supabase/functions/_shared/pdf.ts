// Checks stored bytes, not the declared MIME type: a real PDF starts with
// "%PDF-" (optionally after a UTF-8 BOM or whitespace in the first KB, which
// PDF readers accept).

export type PdfCheck = "ok" | "empty" | "too_large" | "not_pdf";

export function checkPdf(bytes: Uint8Array, maxBytes: number): PdfCheck {
  if (bytes.length === 0) return "empty";
  if (bytes.length > maxBytes) return "too_large";
  const head = new TextDecoder("latin1").decode(bytes.subarray(0, 1024));
  const index = head.indexOf("%PDF-");
  if (index < 0) return "not_pdf";
  // Only whitespace or a BOM may precede the header.
  const before = head.slice(0, index).replace(/^﻿|^\xEF\xBB\xBF/, "");
  return before.trim() === "" ? "ok" : "not_pdf";
}
