// E-mail broadcasts from the admin panel through Resend, and the signed
// unsubscribe links that marketing mail carries. Pure logic; `fetchFn` and
// `sleep` are injectable for tests.

type Fetch = typeof fetch;

export const AUDIENCES = ["ALL", "APPROVED", "PENDING_REVIEW", "DOCUMENT_REQUIRED", "PREMIUM"] as const;
export type Audience = typeof AUDIENCES[number];

export interface BroadcastRequest {
  subject: string;
  body: string;
  audience: Audience;
  marketing: boolean;
  /** When set, only this address receives the mail (a test send to the admin). */
  testOnly: boolean;
}

export const MAX_SUBJECT = 150;
export const MAX_BODY = 20000;
export const BATCH_SIZE = 100;

export function parseBroadcastRequest(raw: Record<string, unknown> | null): BroadcastRequest | null {
  if (!raw) return null;
  const { subject, body, audience, marketing, test_only: testOnly } = raw;
  if (typeof subject !== "string" || typeof body !== "string") return null;
  const s = subject.trim(), b = body.trim();
  if (s.length < 1 || s.length > MAX_SUBJECT || /[\r\n]/.test(s)) return null;
  if (b.length < 1 || b.length > MAX_BODY) return null;
  if (typeof audience !== "string" || !(AUDIENCES as readonly string[]).includes(audience)) return null;
  if (typeof marketing !== "boolean") return null;
  if (testOnly !== undefined && typeof testOnly !== "boolean") return null;
  return { subject: s, body: b, audience: audience as Audience, marketing, testOnly: testOnly === true };
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

/** Plain text body → HTML paragraphs. The admin's text is never treated as HTML. */
export function renderHtml(body: string, unsubscribeUrl: string | null): string {
  const paragraphs = body.split(/\n{2,}/).map((p) => `<p style="margin:0 0 16px">${escapeHtml(p).replace(/\n/g, "<br>")}</p>`);
  const footer = unsubscribeUrl
    ? `<p style="margin:24px 0 0;font-size:12px;color:#6b7280">Bu e-postayı pazarlama iletilerine izin verdiğiniz için aldınız. ` +
      `<a href="${escapeHtml(unsubscribeUrl)}" style="color:#6b7280">Abonelikten çık</a></p>`
    : `<p style="margin:24px 0 0;font-size:12px;color:#6b7280">Bu bir KampüsAğı hizmet duyurusudur.</p>`;
  return `<!doctype html><html><body style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;font-size:15px;line-height:1.6;color:#111827;max-width:600px;margin:0 auto;padding:24px">` +
    paragraphs.join("") + footer + `</body></html>`;
}

export function renderText(body: string, unsubscribeUrl: string | null): string {
  return unsubscribeUrl
    ? `${body}\n\n—\nAbonelikten çıkmak için: ${unsubscribeUrl}`
    : `${body}\n\n—\nBu bir KampüsAğı hizmet duyurusudur.`;
}

/** RESEND_FROM must be `email@domain` or `Name <email@domain>`, as Resend requires. */
export function isValidSender(from: string): boolean {
  const address = /^[^\s@<>"]+@[^\s@<>"]+\.[^\s@<>"]+$/;
  const trimmed = from.trim();
  if (address.test(trimmed)) return true;
  const named = trimmed.match(/^(.+?)\s*<([^<>]+)>$/);
  return named !== null && named[1].trim().length > 0 && !/[<>]/.test(named[1]) && address.test(named[2]);
}

// Unsubscribe signatures ---------------------------------------------------------------

async function hmacKey(secret: string): Promise<CryptoKey> {
  return await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, [
    "sign",
    "verify",
  ]);
}

function toHex(bytes: ArrayBuffer): string {
  return Array.from(new Uint8Array(bytes), (b) => b.toString(16).padStart(2, "0")).join("");
}

function fromHex(hex: string): Uint8Array<ArrayBuffer> | null {
  if (!/^[0-9a-f]{64}$/.test(hex)) return null;
  const out = new Uint8Array(new ArrayBuffer(32));
  for (let i = 0; i < 32; i++) out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return out;
}

export async function signUnsubscribe(secret: string, userId: string): Promise<string> {
  const key = await hmacKey(secret);
  return toHex(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`unsubscribe:${userId}`)));
}

/** Constant-time check through WebCrypto. */
export async function verifyUnsubscribe(secret: string, userId: string, signature: string): Promise<boolean> {
  const bytes = fromHex(signature);
  if (!bytes) return false;
  const key = await hmacKey(secret);
  return await crypto.subtle.verify("HMAC", key, bytes, new TextEncoder().encode(`unsubscribe:${userId}`));
}

export function unsubscribeUrl(siteUrl: string, userId: string, signature: string): string {
  const url = new URL("/abonelik-iptal", siteUrl);
  url.searchParams.set("u", userId);
  url.searchParams.set("s", signature);
  return url.toString();
}

// Resend ---------------------------------------------------------------------------------

export interface Recipient {
  userId: string;
  email: string;
}

export interface OutgoingEmail {
  from: string;
  to: string[];
  subject: string;
  html: string;
  text: string;
  headers?: Record<string, string>;
}

export interface SendResult {
  sent: number;
  failed: number;
}

export function chunk<T>(items: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
  return out;
}

/**
 * Sends through Resend's batch endpoint, 100 mails per request, pausing
 * between requests to stay under the default rate limit. A failed batch is
 * counted and the rest continue.
 */
export async function sendBatches(
  fetchFn: Fetch,
  apiKey: string,
  emails: OutgoingEmail[],
  sleep: (ms: number) => Promise<void>,
  onError: (message: string) => void,
): Promise<SendResult> {
  let sent = 0, failed = 0;
  const batches = chunk(emails, BATCH_SIZE);
  for (let i = 0; i < batches.length; i++) {
    if (i > 0) await sleep(600);
    const batch = batches[i];
    try {
      const response = await fetchFn("https://api.resend.com/emails/batch", {
        method: "POST",
        headers: { "Authorization": `Bearer ${apiKey}`, "Content-Type": "application/json" },
        body: JSON.stringify(batch),
      });
      if (response.ok) {
        await response.body?.cancel();
        sent += batch.length;
      } else {
        const detail = await response.text();
        onError(`Resend batch ${i + 1}/${batches.length} returned HTTP ${response.status}: ${detail.slice(0, 300)}`);
        failed += batch.length;
      }
    } catch (error) {
      onError(`Resend batch ${i + 1}/${batches.length} failed: ${error instanceof Error ? error.message : String(error)}`);
      failed += batch.length;
    }
  }
  return { sent, failed };
}

export function broadcastStatus(result: SendResult): "SENT" | "PARTIAL" | "FAILED" {
  if (result.failed === 0) return "SENT";
  return result.sent === 0 ? "FAILED" : "PARTIAL";
}
