import { assertEquals } from "jsr:@std/assert@1";
import { embeddingInput, isValidText, mapDatabaseError, normalizeDraft, vectorLiteral } from "./requirement.ts";

const now = new Date("2026-09-26T12:00:00Z");
const valid = {
  title: "  Basketbol   maçı ",
  description: "Kampüs sahasında akşam maç.",
  category: "SPORTS",
  tags: ["Spor", "spor", " Basketbol ", "", "x".repeat(31)],
  location_text: "Kampüs sahası",
  starts_at: "2026-09-27T18:00:00+03:00",
  participants_needed: 2,
};

Deno.test("normalises a valid draft", () => {
  assertEquals(normalizeDraft(valid, now), {
    title: "Basketbol maçı",
    description: "Kampüs sahasında akşam maç.",
    category: "SPORTS",
    tags: ["spor", "basketbol"],
    location_text: "Kampüs sahası",
    starts_at: "2026-09-27T15:00:00.000Z",
    participants_needed: 2,
  });
});

Deno.test("nullable fields may be null", () => {
  const draft = normalizeDraft({ ...valid, location_text: null, starts_at: null, participants_needed: null }, now);
  assertEquals([draft?.location_text, draft?.starts_at, draft?.participants_needed], [null, null, null]);
});

Deno.test("rejects anything outside the rules", () => {
  const bad: Record<string, unknown>[] = [
    { ...valid, title: "ab" },
    { ...valid, category: "PARTY" },
    { ...valid, tags: "spor" },
    { ...valid, starts_at: "yarın" },
    { ...valid, starts_at: "2020-01-01T00:00:00Z" },
    { ...valid, starts_at: "2028-01-01T00:00:00Z" },
    { ...valid, participants_needed: 0 },
    { ...valid, participants_needed: 2.5 },
    { ...valid, location_text: "   " },
  ];
  for (const draft of bad) assertEquals(normalizeDraft(draft, now), null, JSON.stringify(draft));
  assertEquals(normalizeDraft(null, now), null);
  assertEquals(normalizeDraft("x", now), null);
});

Deno.test("keeps at most eight tags", () => {
  const draft = normalizeDraft({ ...valid, tags: Array.from({ length: 12 }, (_, i) => `etiket${i}`) }, now);
  assertEquals(draft?.tags.length, 8);
});

Deno.test("text length limits", () => {
  assertEquals(isValidText("kısa"), false);
  assertEquals(isValidText("  on karakterlik metin  "), true);
  assertEquals(isValidText("a".repeat(1001)), false);
  assertEquals(isValidText(42), false);
});

Deno.test("embedding input describes the need only", () => {
  const draft = normalizeDraft(valid, now)!;
  assertEquals(embeddingInput(draft), "Basketbol maçı\nKampüs sahasında akşam maç.\nKategori: SPORTS\nEtiketler: spor, basketbol");
  assertEquals(embeddingInput({ ...draft, tags: [] }).includes("Etiketler"), false);
});

Deno.test("vector literal and database error mapping", () => {
  assertEquals(vectorLiteral([0.5, -1, 0]), "[0.5,-1,0]");
  assertEquals(mapDatabaseError("ai_quota_exceeded"), { code: "ai_quota_exceeded", status: 429 });
  assertEquals(mapDatabaseError("approved_student_required"), { code: "approved_student_required", status: 403 });
  assertEquals(mapDatabaseError("boom"), { code: "server_error", status: 500 });
});
