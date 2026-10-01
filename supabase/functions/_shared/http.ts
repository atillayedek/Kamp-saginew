// Shared HTTP helpers for Edge Functions. Every error response has the same
// shape, {"error": "<stable_snake_case_code>"}, which the Android app maps to a
// domain error. Internal details are logged, never returned.

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function errorResponse(code: string, status: number): Response {
  return json({ error: code }, status);
}

export function requireEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing environment variable ${name}`);
  return value;
}

// Browser callers (the web admin panel). Access is by bearer token, never
// cookies, so any origin may call; the token decides what is allowed.
export const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info, x-audit-reason",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export function withCors(response: Response): Response {
  for (const [name, value] of Object.entries(CORS_HEADERS)) response.headers.set(name, value);
  return response;
}

export function preflight(): Response {
  return new Response(null, { status: 204, headers: CORS_HEADERS });
}
