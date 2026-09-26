// Minimal OpenAI client over fetch. `fetchFn` is injectable so the request and
// response handling can be tested without the network; production passes the
// global fetch. The API key never leaves the Edge Function.

export interface OpenAiConfig {
  apiKey: string;
  chatModel: string;
  embeddingModel: string;
}

/** Null when any setting is missing: callers answer 503 instead of guessing a model. */
export function openAiConfigFromEnv(get: (name: string) => string | undefined): OpenAiConfig | null {
  const apiKey = get("OPENAI_API_KEY");
  const chatModel = get("OPENAI_CHAT_MODEL");
  const embeddingModel = get("OPENAI_EMBEDDING_MODEL");
  if (!apiKey || !chatModel || !embeddingModel) return null;
  return { apiKey, chatModel, embeddingModel };
}

export class OpenAiError extends Error {
  constructor(readonly kind: "refused" | "failed", message: string) {
    super(message);
  }
}

type Fetch = typeof fetch;
const BASE_URL = "https://api.openai.com/v1";

async function post(fetchFn: Fetch, config: OpenAiConfig, path: string, body: unknown): Promise<unknown> {
  const response = await fetchFn(`${BASE_URL}${path}`, {
    method: "POST",
    headers: { "Authorization": `Bearer ${config.apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    // The body can echo the request; only the status is logged by callers.
    await response.body?.cancel();
    throw new OpenAiError("failed", `OpenAI ${path} returned HTTP ${response.status}`);
  }
  return await response.json();
}

/** Chat completion constrained to a strict JSON schema; returns the parsed object. */
export async function structuredCompletion(
  fetchFn: Fetch,
  config: OpenAiConfig,
  system: string,
  user: string,
  schemaName: string,
  schema: Record<string, unknown>,
): Promise<unknown> {
  const data = await post(fetchFn, config, "/chat/completions", {
    model: config.chatModel,
    messages: [
      { role: "system", content: system },
      { role: "user", content: user },
    ],
    response_format: { type: "json_schema", json_schema: { name: schemaName, strict: true, schema } },
  }) as { choices?: { message?: { content?: string | null; refusal?: string | null } }[] };

  const message = data.choices?.[0]?.message;
  if (message?.refusal) throw new OpenAiError("refused", "Model refused the request");
  if (typeof message?.content !== "string") throw new OpenAiError("failed", "No content in completion");
  try {
    return JSON.parse(message.content);
  } catch (_error) {
    throw new OpenAiError("failed", "Completion was not valid JSON");
  }
}

/** One embedding with exactly `dimensions` numbers. */
export async function embed(fetchFn: Fetch, config: OpenAiConfig, input: string, dimensions: number): Promise<number[]> {
  const data = await post(fetchFn, config, "/embeddings", {
    model: config.embeddingModel,
    input,
    dimensions,
  }) as { data?: { embedding?: unknown }[] };
  const vector = data.data?.[0]?.embedding;
  if (!Array.isArray(vector) || vector.length !== dimensions || !vector.every((v) => typeof v === "number" && Number.isFinite(v))) {
    throw new OpenAiError("failed", "Embedding has an unexpected shape");
  }
  return vector as number[];
}
