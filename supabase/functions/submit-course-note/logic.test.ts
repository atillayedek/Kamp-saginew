import { assertEquals } from "jsr:@std/assert@1";
import { checkNote, mapNoteError, MAX_NOTE_BYTES, parseNoteRequest } from "./logic.ts";

const USER = "0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10";
const PATH = `${USER}/1b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10.pdf`;
const valid = { path: PATH, course_code: " MAT 101 ", course_name: "Matematik I", title: "Vize özeti", description: "  " };

Deno.test("accepts a complete request and trims it", () => {
  assertEquals(parseNoteRequest(valid, USER), {
    path: PATH,
    courseCode: "MAT 101",
    courseName: "Matematik I",
    title: "Vize özeti",
    description: null,
  });
});

Deno.test("the file must be in the caller's own folder", () => {
  assertEquals(parseNoteRequest({ ...valid, path: `0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c11/x.pdf` }, USER), null);
  assertEquals(parseNoteRequest({ ...valid, path: `${USER}/../other.pdf` }, USER), null);
  assertEquals(parseNoteRequest({ ...valid, path: `${USER}/1b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10.png` }, USER), null);
});

Deno.test("rejects missing or oversized fields", () => {
  assertEquals(parseNoteRequest(null, USER), null);
  assertEquals(parseNoteRequest({ ...valid, title: "x" }, USER), null);
  assertEquals(parseNoteRequest({ ...valid, course_code: "M" }, USER), null);
  assertEquals(parseNoteRequest({ ...valid, course_name: 5 }, USER), null);
  assertEquals(parseNoteRequest({ ...valid, description: "a".repeat(501) }, USER), null);
  assertEquals(parseNoteRequest({ ...valid, description: 3 }, USER), null);
});

Deno.test("only real PDFs within the limit pass", () => {
  const pdf = new TextEncoder().encode("%PDF-1.7\n...");
  assertEquals(checkNote(pdf), "ok");
  assertEquals(checkNote(new TextEncoder().encode("<html>")), "not_pdf");
  assertEquals(checkNote(new Uint8Array()), "empty");
  const big = new Uint8Array(MAX_NOTE_BYTES + 1);
  big.set(pdf);
  assertEquals(checkNote(big), "too_large");
});

Deno.test("maps database errors to stable codes", () => {
  assertEquals(mapNoteError("invalid_note"), { code: "invalid_note", status: 400 });
  assertEquals(mapNoteError("rate_limited"), { code: "rate_limited", status: 429 });
  assertEquals(mapNoteError("approved_student_required"), { code: "approved_student_required", status: 403 });
  assertEquals(mapNoteError("boom"), { code: "server_error", status: 500 });
});
