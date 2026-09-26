// POST /functions/v1/analyze-requirement  {"text": "<the student's need>"}
//
// Turns free text into a structured draft with OpenAI (strict JSON schema),
// validates the model output again and returns it. Nothing is stored except the
// usage record that enforces the daily AI quota.

import { authenticate, readJson } from "../_shared/auth.ts";
import { errorResponse, json } from "../_shared/http.ts";
import { OpenAiError, openAiConfigFromEnv, structuredCompletion } from "../_shared/openai.ts";
import { DRAFT_SCHEMA, isValidText, mapDatabaseError, normalizeDraft, systemPrompt } from "../_shared/requirement.ts";

Deno.serve(async (request) => {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const caller = await authenticate(request);
  if (!caller) return errorResponse("not_authenticated", 401);

  const body = await readJson(request);
  if (!body || !isValidText(body.text)) return errorResponse("invalid_request", 400);
  const text = body.text.trim();

  const config = openAiConfigFromEnv((name) => Deno.env.get(name));
  if (!config) {
    console.error("OpenAI is not configured (OPENAI_API_KEY / OPENAI_CHAT_MODEL / OPENAI_EMBEDDING_MODEL)");
    return errorResponse("ai_not_configured", 503);
  }

  const { error: quotaError } = await caller.admin.rpc("consume_ai_quota", { p_user_id: caller.userId, p_kind: "analyze" });
  if (quotaError) {
    const mapped = mapDatabaseError(quotaError.message);
    if (mapped.status >= 500) console.error("consume_ai_quota failed", quotaError.message);
    return errorResponse(mapped.code, mapped.status);
  }

  const now = new Date();
  let output: unknown;
  try {
    output = await structuredCompletion(fetch, config, systemPrompt(now), text, "requirement", DRAFT_SCHEMA);
  } catch (error) {
    if (error instanceof OpenAiError && error.kind === "refused") return errorResponse("ai_refused", 422);
    console.error("OpenAI completion failed", error instanceof Error ? error.message : String(error));
    return errorResponse("ai_failed", 502);
  }

  const draft = normalizeDraft(output, now);
  if (!draft) {
    console.error("OpenAI returned a draft that failed validation");
    return errorResponse("ai_failed", 502);
  }
  return json({ draft });
});
