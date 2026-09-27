// Resolves the calling user from the request's JWT and builds a service-role
// client. Every function that acts for a user starts here.

import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { requireEnv } from "./http.ts";

export interface Caller {
  userId: string;
  email: string | null;
  /** app_metadata.role = 'admin'; only the service role can set it. */
  isAdmin: boolean;
  admin: SupabaseClient;
}

/** Null when the request carries no valid session. */
export async function authenticate(request: Request): Promise<Caller | null> {
  const authorization = request.headers.get("Authorization");
  if (!authorization) return null;
  const url = requireEnv("SUPABASE_URL");
  const caller = createClient(url, requireEnv("SUPABASE_ANON_KEY"), {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false },
  });
  const { data, error } = await caller.auth.getUser();
  if (error || !data.user) return null;
  const admin = createClient(url, requireEnv("SUPABASE_SERVICE_ROLE_KEY"), { auth: { persistSession: false } });
  return { userId: data.user.id, email: data.user.email ?? null, isAdmin: data.user.app_metadata?.role === "admin", admin };
}

export async function readJson(request: Request): Promise<Record<string, unknown> | null> {
  try {
    const body = await request.json();
    return typeof body === "object" && body !== null && !Array.isArray(body) ? body as Record<string, unknown> : null;
  } catch (_error) {
    return null;
  }
}
