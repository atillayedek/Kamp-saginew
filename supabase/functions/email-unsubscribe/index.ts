// POST /functions/v1/email-unsubscribe?u=<user id>&s=<signature>
//
// Turns off marketing e-mail for one person. Called by the web page behind the
// link in every marketing mail, and directly by mail clients (RFC 8058
// one-click unsubscribe). No session: the HMAC signature proves the link came
// from us. Deployed with verify_jwt = false.

import { verifyUnsubscribe } from "../_shared/broadcast.ts";
import { errorResponse, json, preflight, requireEnv, withCors } from "../_shared/http.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return preflight();
  return withCors(await handle(request));
});

async function handle(request: Request): Promise<Response> {
  if (request.method !== "POST") return errorResponse("method_not_allowed", 405);

  const secret = Deno.env.get("UNSUBSCRIBE_SECRET");
  if (!secret) {
    console.error("UNSUBSCRIBE_SECRET is not set");
    return errorResponse("email_not_configured", 503);
  }

  const url = new URL(request.url);
  const userId = url.searchParams.get("u") ?? "";
  const signature = url.searchParams.get("s") ?? "";
  if (!UUID.test(userId) || !(await verifyUnsubscribe(secret, userId, signature))) {
    return errorResponse("invalid_link", 400);
  }

  const admin = createClient(requireEnv("SUPABASE_URL"), requireEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false },
  });
  const { error } = await admin.rpc("unsubscribe_marketing", { p_user_id: userId });
  if (error) {
    console.error("unsubscribe_marketing failed", error.message);
    return errorResponse("server_error", 500);
  }
  return json({ unsubscribed: true });
}
