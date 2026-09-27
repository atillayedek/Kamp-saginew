import { assertEquals, assertStringIncludes } from "jsr:@std/assert@1";
import {
  BATCH_SIZE,
  broadcastStatus,
  chunk,
  type OutgoingEmail,
  parseBroadcastRequest,
  renderHtml,
  renderText,
  sendBatches,
  signUnsubscribe,
  unsubscribeUrl,
  verifyUnsubscribe,
} from "./broadcast.ts";

const USER = "0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10";
const valid = { subject: " Bakım ", body: "Yarın bakım var.", audience: "ALL", marketing: false };

Deno.test("accepts a well-formed request and trims it", () => {
  assertEquals(parseBroadcastRequest(valid), {
    subject: "Bakım", body: "Yarın bakım var.", audience: "ALL", marketing: false, testOnly: false,
  });
  assertEquals(parseBroadcastRequest({ ...valid, test_only: true })?.testOnly, true);
});

Deno.test("refuses malformed requests", () => {
  assertEquals(parseBroadcastRequest(null), null);
  assertEquals(parseBroadcastRequest({ ...valid, subject: "   " }), null);
  assertEquals(parseBroadcastRequest({ ...valid, subject: "a\r\nBcc: x@y.z" }), null);
  assertEquals(parseBroadcastRequest({ ...valid, subject: "x".repeat(151) }), null);
  assertEquals(parseBroadcastRequest({ ...valid, body: "x".repeat(20001) }), null);
  assertEquals(parseBroadcastRequest({ ...valid, audience: "EVERYONE" }), null);
  assertEquals(parseBroadcastRequest({ ...valid, marketing: "yes" }), null);
  assertEquals(parseBroadcastRequest({ ...valid, test_only: "true" }), null);
});

Deno.test("the admin's text is escaped, never rendered as HTML", () => {
  const html = renderHtml("<script>x</script>\n\nİkinci & son", null);
  assertStringIncludes(html, "&lt;script&gt;x&lt;/script&gt;");
  assertStringIncludes(html, "İkinci &amp; son");
  assertEquals(html.includes("<script>"), false);
  assertStringIncludes(html, "hizmet duyurusudur");
});

Deno.test("marketing mail carries the unsubscribe link", () => {
  const link = unsubscribeUrl("https://kampusagi.example", USER, "ab".repeat(32));
  assertEquals(link, `https://kampusagi.example/abonelik-iptal?u=${USER}&s=${"ab".repeat(32)}`);
  assertStringIncludes(renderHtml("Merhaba", link), `href="${link.replace(/&/g, "&amp;")}"`);
  assertStringIncludes(renderText("Merhaba", link), link);
});

Deno.test("unsubscribe signatures verify only for the same user and secret", async () => {
  const signature = await signUnsubscribe("secret-1", USER);
  assertEquals(await verifyUnsubscribe("secret-1", USER, signature), true);
  assertEquals(await verifyUnsubscribe("secret-2", USER, signature), false);
  assertEquals(await verifyUnsubscribe("secret-1", "1b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10", signature), false);
  assertEquals(await verifyUnsubscribe("secret-1", USER, "not-hex"), false);
  assertEquals(await verifyUnsubscribe("secret-1", USER, ""), false);
});

function mail(i: number): OutgoingEmail {
  return { from: "KampüsAğı <duyuru@example.com>", to: [`u${i}@example.edu.tr`], subject: "S", html: "<p>B</p>", text: "B" };
}

Deno.test("sends in batches of 100 and counts a failed batch", async () => {
  const bodies: unknown[][] = [];
  let call = 0;
  const errors: string[] = [];
  const sleeps: number[] = [];
  const fetchFn = ((_url: string, init: RequestInit) => {
    bodies.push(JSON.parse(init.body as string));
    call++;
    return Promise.resolve(call === 2 ? new Response("rate limited", { status: 429 }) : new Response("{}", { status: 200 }));
  }) as typeof fetch;
  const emails = Array.from({ length: BATCH_SIZE * 2 + 5 }, (_, i) => mail(i));
  const result = await sendBatches(fetchFn, "key", emails, (ms) => {
    sleeps.push(ms);
    return Promise.resolve();
  }, (m) => errors.push(m));
  assertEquals(bodies.map((b) => b.length), [100, 100, 5]);
  assertEquals(result, { sent: 105, failed: 100 });
  assertEquals(sleeps.length, 2);
  assertEquals(errors.length, 1);
  assertStringIncludes(errors[0], "HTTP 429");
  assertEquals(broadcastStatus(result), "PARTIAL");
});

Deno.test("status and chunking edge cases", async () => {
  assertEquals(broadcastStatus({ sent: 3, failed: 0 }), "SENT");
  assertEquals(broadcastStatus({ sent: 0, failed: 3 }), "FAILED");
  assertEquals(chunk([], 100), []);
  const failing = (() => Promise.reject(new Error("network down"))) as typeof fetch;
  const errors: string[] = [];
  const result = await sendBatches(failing, "key", [mail(1)], () => Promise.resolve(), (m) => errors.push(m));
  assertEquals(result, { sent: 0, failed: 1 });
  assertStringIncludes(errors[0], "network down");
});
