import { assertEquals, assertRejects } from "jsr:@std/assert@1";
import { accessToken, parseServiceAccount, signedAssertion } from "./google-auth.ts";
import { PLAY_SCOPE } from "./play.ts";

async function testAccount() {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  let binary = "";
  for (const b of der) binary += String.fromCharCode(b);
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(binary)}\n-----END PRIVATE KEY-----\n`;
  return { account: { projectId: "kampusagi-test", clientEmail: "play@kampusagi-test.iam.gserviceaccount.com", privateKeyPem: pem }, publicKey: pair.publicKey };
}

function decode(part: string): ArrayBuffer {
  const b64 = part.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - part.length % 4) % 4);
  const binary = atob(b64);
  const buffer = new ArrayBuffer(binary.length);
  const bytes = new Uint8Array(buffer);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return buffer;
}

/** Test double for fetch. */
function scriptedFetch(status: number, body: string) {
  const calls: { url: string; init: RequestInit }[] = [];
  const fn = (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(input), init: init ?? {} });
    return Promise.resolve(new Response(body, { status }));
  };
  return { fn: fn as typeof fetch, calls };
}

Deno.test("service account parsing requires the three fields", () => {
  assertEquals(parseServiceAccount(JSON.stringify({ project_id: "p", client_email: "e", private_key: "k" })), {
    projectId: "p", clientEmail: "e", privateKeyPem: "k",
  });
  assertEquals(parseServiceAccount(JSON.stringify({ project_id: "p" })), null);
  assertEquals(parseServiceAccount("not json"), null);
  assertEquals(parseServiceAccount(undefined), null);
});

Deno.test("the assertion is a verifiable RS256 JWT with the requested scope", async () => {
  const { account, publicKey } = await testAccount();
  const jwt = await signedAssertion(account, PLAY_SCOPE, 1_800_000_000);
  const [header, claims, signature] = jwt.split(".");
  assertEquals(JSON.parse(new TextDecoder().decode(decode(header))), { alg: "RS256", typ: "JWT" });
  const payload = JSON.parse(new TextDecoder().decode(decode(claims)));
  assertEquals(payload.iss, account.clientEmail);
  assertEquals(payload.scope, "https://www.googleapis.com/auth/androidpublisher");
  assertEquals(payload.aud, "https://oauth2.googleapis.com/token");
  assertEquals(payload.exp - payload.iat, 3600);
  const valid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5", publicKey, decode(signature), new TextEncoder().encode(`${header}.${claims}`),
  );
  assertEquals(valid, true);
});

Deno.test("access token exchange", async () => {
  const { account } = await testAccount();
  const ok = scriptedFetch(200, JSON.stringify({ access_token: "ya29.token", expires_in: 3599 }));
  assertEquals(await accessToken(ok.fn, account, PLAY_SCOPE, 100), { token: "ya29.token", expiresAt: 3699 });
  const form = new URLSearchParams(ok.calls[0].init.body as string);
  assertEquals(form.get("grant_type"), "urn:ietf:params:oauth:grant-type:jwt-bearer");
  assertEquals(form.get("assertion")?.split(".").length, 3);

  await assertRejects(() => accessToken(scriptedFetch(401, "{}").fn, account, PLAY_SCOPE, 100));
  await assertRejects(() => accessToken(scriptedFetch(200, "{}").fn, account, PLAY_SCOPE, 100));
});
