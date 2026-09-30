// Pure rules for course notes, shared by the Edge Function and its tests. The
// database checks the same limits again (defence in depth).

import { checkPdf, type PdfCheck } from "../_shared/pdf.ts";

export const MAX_NOTE_BYTES = 20 * 1024 * 1024;

const PATH = /^([0-9a-f-]{36})\/[0-9a-f-]{36}\.pdf$/;

export interface NoteRequest {
  path: string;
  courseCode: string;
  courseName: string;
  title: string;
  description: string | null;
}

/** The request body, or null when a field is missing or out of range. The path must be in the caller's folder. */
export function parseNoteRequest(raw: Record<string, unknown> | null, userId: string): NoteRequest | null {
  if (!raw) return null;
  const { path, course_code: courseCode, course_name: courseName, title, description } = raw;
  if (typeof path !== "string" || PATH.exec(path)?.[1] !== userId) return null;
  if (typeof courseCode !== "string" || typeof courseName !== "string" || typeof title !== "string") return null;
  if (description !== undefined && description !== null && typeof description !== "string") return null;
  const code = courseCode.trim(), name = courseName.trim(), t = title.trim();
  const d = typeof description === "string" ? description.trim() : "";
  if (code.length < 2 || code.length > 20) return null;
  if (name.length < 2 || name.length > 120 || t.length < 2 || t.length > 120 || d.length > 500) return null;
  return { path, courseCode: code, courseName: name, title: t, description: d === "" ? null : d };
}

export function checkNote(bytes: Uint8Array): PdfCheck {
  return checkPdf(bytes, MAX_NOTE_BYTES);
}

/** Maps the database's P0001 messages to HTTP responses. */
export function mapNoteError(message: string | undefined): { code: string; status: number } {
  switch (message) {
    case "approved_student_required":
      return { code: "approved_student_required", status: 403 };
    case "invalid_document_path":
      return { code: "invalid_document_path", status: 400 };
    case "invalid_note":
      return { code: "invalid_note", status: 400 };
    case "rate_limited":
      return { code: "rate_limited", status: 429 };
    default:
      return { code: "server_error", status: 500 };
  }
}
