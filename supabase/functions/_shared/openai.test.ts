import { assertEquals, assertRejects } from "jsr:@std/assert@1";
import { embed, OpenAiError, openAiConfigFromEnv, structuredCompletion } from "./openai.ts";

const config = { apiKey: "test-key", chatModel: "chat-model", embeddingModel: "embed-model" };

/** Test double for fetch: records the request and answers with a scripted response. */
function scriptedFetch(status: number, body: unknown) {
  const calls: { url: string; init: RequestInit }[] = [];
  const fn = (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(input), init: init ?? {} });
    return Promise.resolve(new Response(JSON.stringify(body), { status }));
  };
  return { fn: fn as typeof fetch, calls };
}

Deno.test("config requires every setting", () => {
  const env: Record<string, string> = { OPENAI_API_KEY: "k", OPENAI_CHAT_MODEL: "c", OPENAI_EMBEDDING_MODEL: "e" };
  assertEquals(openAiConfigFromEnv((n) => env[n]), { apiKey: "k", chatModel: "c", embeddingModel: "e" });
  delete env.OPENAI_CHAT_MODEL;
  assertEquals(openAiConfigFromEnv((n) => env[n]), null);
});

Deno.test("structured completion sends a strict schema and parses the content", async () => {
  const { fn, calls } = scriptedFetch(200, { choices: [{ message: { content: '{"title":"Basketbol"}' } }] });
  const result = await structuredCompletion(fn, config, "system", "user text", "requirement", { type: "object" });
  assertEquals(result, { title: "Basketbol" });
  assertEquals(calls[0].url, "https://api.openai.com/v1/chat/completions");
  assertEquals((calls[0].init.headers as Record<string, string>)["Authorization"], "Bearer test-key");
  const sent = JSON.parse(calls[0].init.body as string);
  assertEquals(sent.model, "chat-model");
  assertEquals(sent.response_format.json_schema.strict, true);
  assertEquals(sent.messages[1], { role: "user", content: "user text" });
});

Deno.test("refusals, HTTP errors and invalid JSON are typed errors", async () => {
  const refused = scriptedFetch(200, { choices: [{ message: { content: null, refusal: "no" } }] });
  const e1 = await assertRejects(() => structuredCompletion(refused.fn, config, "s", "u", "n", {}), OpenAiError);
  assertEquals(e1.kind, "refused");

  const http = scriptedFetch(429, { error: { message: "rate limit" } });
  const e2 = await assertRejects(() => structuredCompletion(http.fn, config, "s", "u", "n", {}), OpenAiError);
  assertEquals(e2.kind, "failed");

  const garbage = scriptedFetch(200, { choices: [{ message: { content: "not json" } }] });
  const e3 = await assertRejects(() => structuredCompletion(garbage.fn, config, "s", "u", "n", {}), OpenAiError);
  assertEquals(e3.kind, "failed");
});

Deno.test("embedding asks for the dimensions and checks the shape", async () => {
  const ok = scriptedFetch(200, { data: [{ embedding: [0.1, 0.2, 0.3] }] });
  assertEquals(await embed(ok.fn, config, "metin", 3), [0.1, 0.2, 0.3]);
  const sent = JSON.parse(ok.calls[0].init.body as string);
  assertEquals(sent, { model: "embed-model", input: "metin", dimensions: 3 });

  const wrongSize = scriptedFetch(200, { data: [{ embedding: [0.1, 0.2] }] });
  await assertRejects(() => embed(wrongSize.fn, config, "metin", 3), OpenAiError);
  const notNumbers = scriptedFetch(200, { data: [{ embedding: [0.1, "x", 0.3] }] });
  await assertRejects(() => embed(notNumbers.fn, config, "metin", 3), OpenAiError);
});
