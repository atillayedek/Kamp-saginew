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
