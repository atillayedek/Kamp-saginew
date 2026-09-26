// Requirement rules shared by analyze-requirement and publish-requirement.
// The database checks the same limits again (defence in depth).

export const CATEGORIES = ["SPORTS", "STUDY", "PROJECT", "TRANSPORT", "ITEM", "EVENT", "HOUSING", "OTHER"] as const;
export type Category = typeof CATEGORIES[number];

export const EMBEDDING_DIMENSIONS = 1536;
export const TEXT_MIN = 10;
export const TEXT_MAX = 1000;

export interface RequirementDraft {
  title: string;
  description: string;
  category: Category;
  tags: string[];
  location_text: string | null;
  starts_at: string | null;
  participants_needed: number | null;
}

/** JSON schema for OpenAI structured output (strict mode: every field required, nullables explicit). */
export const DRAFT_SCHEMA: Record<string, unknown> = {
  type: "object",
  additionalProperties: false,
  required: ["title", "description", "category", "tags", "location_text", "starts_at", "participants_needed"],
  properties: {
    title: { type: "string", description: "Short Turkish title, 3-120 characters." },
    description: { type: "string", description: "One or two Turkish sentences describing the need, max 1000 characters." },
    category: { type: "string", enum: [...CATEGORIES] },
    tags: { type: "array", items: { type: "string" }, description: "Up to 8 short lowercase Turkish keywords." },
    location_text: { type: ["string", "null"], description: "Place mentioned by the student, or null." },
    starts_at: { type: ["string", "null"], description: "ISO-8601 date-time with offset if a time is mentioned, else null." },
    participants_needed: { type: ["integer", "null"], description: "Number of people needed (1-50), or null." },
  },
};

export function systemPrompt(now: Date): string {
  return [
    "Sen KampüsAğı adlı üniversite öğrencileri uygulamasında ihtiyaç ilanlarını yapılandıran bir asistansın.",
    "Öğrencinin yazdığı metinden yalnızca metinde gerçekten geçen bilgileri çıkar; bilgi yoksa alanı null bırak, uydurma.",
    "Başlık ve açıklama Türkçe, kısa ve saygılı olsun; kişisel iletişim bilgilerini (telefon, e-posta) açıklamaya koyma.",
    `Şu anki zaman: ${now.toISOString()} (UTC). Göreli tarihleri (yarın, cuma akşamı) Europe/Istanbul saat dilimine göre çöz ve +03:00 ofsetiyle yaz.`,
  ].join("\n");
}

export function isValidText(value: unknown): value is string {
  return typeof value === "string" && value.trim().length >= TEXT_MIN && value.trim().length <= TEXT_MAX;
}

function cleanString(value: unknown, min: number, max: number): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim().replace(/\s+/g, " ");
  return trimmed.length >= min && trimmed.length <= max ? trimmed : null;
}

/**
 * Validates and normalises a draft coming from the model or from the app.
 * Returns null when it cannot be accepted. `now` bounds starts_at to a sane window.
 */
export function normalizeDraft(input: unknown, now: Date): RequirementDraft | null {
  if (typeof input !== "object" || input === null) return null;
  const raw = input as Record<string, unknown>;

  const title = cleanString(raw.title, 3, 120);
  const description = cleanString(raw.description, 1, 1000);
  const category = CATEGORIES.includes(raw.category as Category) ? raw.category as Category : null;
  if (!title || !description || !category) return null;

  if (!Array.isArray(raw.tags)) return null;
  const tags = [...new Set(
    raw.tags
      .filter((t): t is string => typeof t === "string")
      .map((t) => t.trim().toLocaleLowerCase("tr-TR"))
      .filter((t) => t.length > 0 && t.length <= 30),
  )].slice(0, 8);

  let location: string | null = null;
  if (raw.location_text !== null && raw.location_text !== undefined) {
    location = cleanString(raw.location_text, 1, 120);
    if (location === null) return null;
  }

  let startsAt: string | null = null;
  if (raw.starts_at !== null && raw.starts_at !== undefined) {
    if (typeof raw.starts_at !== "string") return null;
    const time = Date.parse(raw.starts_at);
    const day = 24 * 60 * 60 * 1000;
    if (Number.isNaN(time) || time < now.getTime() - day || time > now.getTime() + 365 * day) return null;
    startsAt = new Date(time).toISOString();
  }

  let participants: number | null = null;
  if (raw.participants_needed !== null && raw.participants_needed !== undefined) {
    const n = raw.participants_needed;
    if (typeof n !== "number" || !Number.isInteger(n) || n < 1 || n > 50) return null;
    participants = n;
  }

  return { title, description, category, tags, location_text: location, starts_at: startsAt, participants_needed: participants };
}

/** Text that is embedded: what the need is about, not who wrote it or when. */
export function embeddingInput(draft: RequirementDraft): string {
  return [draft.title, draft.description, `Kategori: ${draft.category}`, draft.tags.length ? `Etiketler: ${draft.tags.join(", ")}` : ""]
    .filter((line) => line.length > 0)
    .join("\n");
}

/** pgvector text format. */
export function vectorLiteral(vector: number[]): string {
  return `[${vector.join(",")}]`;
}

/** Maps the database's P0001 messages to HTTP responses. */
export function mapDatabaseError(message: string | undefined): { code: string; status: number } {
  switch (message) {
    case "approved_student_required":
      return { code: "approved_student_required", status: 403 };
    case "ai_quota_exceeded":
      return { code: "ai_quota_exceeded", status: 429 };
    case "invalid_requirement":
      return { code: "invalid_requirement", status: 422 };
    case "too_many_active_requirements":
      return { code: "too_many_active_requirements", status: 409 };
    default:
      return { code: "server_error", status: 500 };
  }
}
