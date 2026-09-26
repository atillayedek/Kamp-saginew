// POST /functions/v1/publish-requirement
//   {"original_text": "...", "draft": {title, description, category, tags, location_text, starts_at, participants_needed}}
//
// Validates the (possibly edited) draft, embeds it with OpenAI and stores it
// with the service role. The embedding is what matching compares.

import { authenticate, readJson } from "../_shared/auth.ts";
import { errorResponse, json } from "../_shared/http.ts";
import { embed, openAiConfigFromEnv } from "../_shared/openai.ts";
import {
  EMBEDDING_DIMENSIONS,
  embeddingInput,
  isValidText,
  mapDatabaseError,
  normalizeDraft,
  vectorLiteral,
} from "../_shared/requirement.ts";

Deno.serve(async (request) => {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const caller = await authenticate(request);
  if (!caller) return errorResponse("not_authenticated", 401);

  const body = await readJson(request);
  if (!body || !isValidText(body.original_text)) return errorResponse("invalid_request", 400);
  const draft = normalizeDraft(body.draft, new Date());
  if (!draft) return errorResponse("invalid_requirement", 422);

  const config = openAiConfigFromEnv((name) => Deno.env.get(name));
  if (!config) {
    console.error("OpenAI is not configured (OPENAI_API_KEY / OPENAI_CHAT_MODEL / OPENAI_EMBEDDING_MODEL)");
    return errorResponse("ai_not_configured", 503);
  }

  const { error: quotaError } = await caller.admin.rpc("consume_ai_quota", { p_user_id: caller.userId, p_kind: "publish" });
  if (quotaError) {
    const mapped = mapDatabaseError(quotaError.message);
    if (mapped.status >= 500) console.error("consume_ai_quota failed", quotaError.message);
    return errorResponse(mapped.code, mapped.status);
  }

  let vector: number[];
  try {
    vector = await embed(fetch, config, embeddingInput(draft), EMBEDDING_DIMENSIONS);
  } catch (error) {
    console.error("OpenAI embedding failed", error instanceof Error ? error.message : String(error));
    return errorResponse("ai_failed", 502);
  }

  const { data: id, error: insertError } = await caller.admin.rpc("insert_requirement", {
    p_user_id: caller.userId,
    p_original_text: (body.original_text as string).trim(),
    p_title: draft.title,
    p_description: draft.description,
    p_category: draft.category,
    p_tags: draft.tags,
    p_location_text: draft.location_text,
    p_starts_at: draft.starts_at,
    p_participants_needed: draft.participants_needed,
    p_embedding: vectorLiteral(vector),
    p_embedding_model: config.embeddingModel,
  });
  if (insertError) {
    const mapped = mapDatabaseError(insertError.message);
    if (mapped.status >= 500) console.error("insert_requirement failed", insertError.message);
    return errorResponse(mapped.code, mapped.status);
  }
  return json({ id }, 201);
});
