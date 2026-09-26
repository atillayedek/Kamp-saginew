// Google service-account authentication: an RS256-signed JWT is exchanged for
// an OAuth access token with the requested scope. Used for the Google Play
// Developer API. `fetchFn` is injectable for tests.

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

export async function signedAssertion(account: ServiceAccount, scope: string, nowSeconds: number): Promise<string> {
  const encoder = new TextEncoder();
  const header = base64Url(encoder.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = base64Url(encoder.encode(JSON.stringify({
    iss: account.clientEmail,
    scope,
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

export class GoogleAuthError extends Error {}

export async function accessToken(
  fetchFn: Fetch,
  account: ServiceAccount,
  scope: string,
  nowSeconds: number,
): Promise<{ token: string; expiresAt: number }> {
  const assertion = await signedAssertion(account, scope, nowSeconds);
  const response = await fetchFn(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }).toString(),
  });
  if (!response.ok) {
    await response.body?.cancel();
    throw new GoogleAuthError(`OAuth token request returned HTTP ${response.status}`);
  }
  const data = await response.json() as { access_token?: unknown; expires_in?: unknown };
  if (typeof data.access_token !== "string") throw new GoogleAuthError("OAuth response without access_token");
  const lifetime = typeof data.expires_in === "number" ? data.expires_in : 3600;
  return { token: data.access_token, expiresAt: nowSeconds + lifetime };
}

