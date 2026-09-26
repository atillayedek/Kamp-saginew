// Firebase Cloud Messaging (HTTP v1) with a service account: an RS256-signed
// JWT is exchanged for an OAuth access token, which authorises the send call.
// `fetchFn` is injectable so the protocol is testable without the network.

type Fetch = typeof fetch;

export interface ServiceAccount {
  projectId: string;
  clientEmail: string;
  privateKeyPem: string;
}

/** Null when the JSON is missing fields; callers answer "not configured". */
export function parseServiceAccount(json: string | undefined): ServiceAccount | null {
  if (!json) return null;
  try {
    const raw = JSON.parse(json) as Record<string, unknown>;
    const projectId = raw.project_id, clientEmail = raw.client_email, privateKey = raw.private_key;
    if (typeof projectId !== "string" || typeof clientEmail !== "string" || typeof privateKey !== "string") return null;
    return { projectId, clientEmail, privateKeyPem: privateKey };
  } catch (_error) {
    return null;
  }
}

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): ArrayBuffer {
  const body = pem.replace(/-----BEGIN [^-]+-----/, "").replace(/-----END [^-]+-----/, "").replace(/\s+/g, "");
  const binary = atob(body);
  const buffer = new ArrayBuffer(binary.length);
  const bytes = new Uint8Array(buffer);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return buffer;
}

export async function signedAssertion(account: ServiceAccount, nowSeconds: number): Promise<string> {
  const encoder = new TextEncoder();
  const header = base64Url(encoder.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = base64Url(encoder.encode(JSON.stringify({
    iss: account.clientEmail,
    scope: SCOPE,
    aud: TOKEN_URL,
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  })));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(account.privateKeyPem),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(`${header}.${claims}`));
  return `${header}.${claims}.${base64Url(new Uint8Array(signature))}`;
}

export class FcmError extends Error {}

export async function accessToken(
  fetchFn: Fetch,
  account: ServiceAccount,
  nowSeconds: number,
): Promise<{ token: string; expiresAt: number }> {
  const assertion = await signedAssertion(account, nowSeconds);
  const response = await fetchFn(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }).toString(),
  });
  if (!response.ok) {
    await response.body?.cancel();
    throw new FcmError(`OAuth token request returned HTTP ${response.status}`);
  }
  const data = await response.json() as { access_token?: unknown; expires_in?: unknown };
  if (typeof data.access_token !== "string") throw new FcmError("OAuth response without access_token");
  const lifetime = typeof data.expires_in === "number" ? data.expires_in : 3600;
  return { token: data.access_token, expiresAt: nowSeconds + lifetime };
}

export const OPEN_NOTIFICATIONS_ACTION = "com.kampusagi.android.OPEN_NOTIFICATIONS";

export type SendResult = "sent" | "unregistered" | "failed";

export interface PushMessage {
  title: string;
  body: string;
  /** String-only data the app uses to open the right screen. */
  data: Record<string, string>;
}

export async function sendToDevice(
  fetchFn: Fetch,
  projectId: string,
  token: string,
  deviceToken: string,
  message: PushMessage,
): Promise<SendResult> {
  const response = await fetchFn(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      message: {
        token: deviceToken,
        notification: { title: message.title, body: message.body },
        data: message.data,
        android: {
          priority: "HIGH",
          // Matches the intent filter on MainActivity: a tap opens the notification list.
          notification: { channel_id: "kampusagi_default", click_action: OPEN_NOTIFICATIONS_ACTION },
        },
      },
    }),
  });
  if (response.ok) {
    await response.body?.cancel();
    return "sent";
  }
  const text = await response.text();
  // The token is no longer valid for this app: stop sending to it.
  if (response.status === 404 || text.includes("UNREGISTERED") || text.includes("registration-token-not-registered")) {
    return "unregistered";
  }
  return "failed";
}

/** Turkish push text. Message previews are not included: the lock screen is not private. */
export function pushText(kind: string, actorName: string | null): { title: string; body: string } | null {
  const who = actorName ?? "Bir öğrenci";
  switch (kind) {
    case "NEW_MESSAGE":
      return { title: "Yeni mesaj", body: `${who} sana mesaj gönderdi.` };
    case "NEW_COMMENT":
      return { title: "Yeni yorum", body: `${who} gönderine yorum yaptı.` };
    case "VERIFICATION_APPROVED":
      return { title: "Hesabın onaylandı", body: "Öğrenci doğrulaman tamamlandı. KampüsAğı'na hoş geldin!" };
    case "VERIFICATION_REJECTED":
      return { title: "Belgen onaylanmadı", body: "Nedenini görmek ve yeni belge yüklemek için uygulamayı aç." };
    default:
      return null;
  }
}

/** Constant-time comparison for the webhook secret. */
export function safeEqual(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a), y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}
