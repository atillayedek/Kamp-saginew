// Resolves the calling user from the request's JWT and builds a service-role
// client. Every function that acts for a user starts here.

import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { requireEnv } from "./http.ts";

export interface Caller {
  userId: string;
  email: string | null;
  /** Acts as the caller (their JWT): RLS and the database's own role/MFA checks apply. */
  client: SupabaseClient;
  admin: SupabaseClient;
}

/** Null when the request carries no valid session. */
export async function authenticate(request: Request): Promise<Caller | null> {
  const authorization = request.headers.get("Authorization");
  if (!authorization) return null;
  const url = requireEnv("SUPABASE_URL");
  const client = createClient(url, requireEnv("SUPABASE_ANON_KEY"), {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false },
  });
  const { data, error } = await client.auth.getUser();
  if (error || !data.user) return null;
  const admin = createClient(url, requireEnv("SUPABASE_SERVICE_ROLE_KEY"), { auth: { persistSession: false } });
  return { userId: data.user.id, email: data.user.email ?? null, client, admin };
}

/**
 * The caller's staff roles when they hold one of `roles` (superadmin holds all) in an MFA
 * session, otherwise null. The database decides (require_staff_role), not the JWT alone.
 */
export async function staffRoles(caller: Caller, roles: string[]): Promise<string[] | null> {
  const { data, error } = await caller.client.rpc("require_staff_role", { p_roles: roles });
  if (error) {
    if (!error.message.includes("admin_required")) console.error("require_staff_role failed", error.message);
    return null;
  }
  return Array.isArray(data) ? data as string[] : null;
}

/** The reason the panel sent for a staff action (x-audit-reason, base64 UTF-8), or null. */
export function auditReason(request: Request): string | null {
  const raw = request.headers.get("x-audit-reason");
  if (!raw) return null;
  try {
    const text = new TextDecoder().decode(Uint8Array.from(atob(raw), (c) => c.charCodeAt(0))).trim();
    return text.length >= 3 ? text.slice(0, 1000) : null;
  } catch (_error) {
    return null;
  }
}

/** Client address as seen by the platform's proxy, for audit records. */
export function clientIp(request: Request): string | null {
  return request.headers.get("cf-connecting-ip") ?? request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? null;
}

/** Records what a staff member did through an Edge Function in admin_audit_logs. */
export async function recordStaffAction(
  caller: Caller,
  roles: string[],
  request: Request,
  action: string,
  target: { type: string; id: string | null },
  reason: string,
  details: Record<string, unknown> = {},
): Promise<boolean> {
  const { error } = await caller.admin.rpc("service_record_admin_action", {
    p_admin_id: caller.userId,
    p_admin_roles: roles,
    p_action: action,
    p_target_type: target.type,
    p_target_id: target.id,
    p_reason: reason,
    p_details: { ...details, client_ip: clientIp(request) },
  });
  if (error) console.error("service_record_admin_action failed", error.message);
  return !error;
}

export async function readJson(request: Request): Promise<Record<string, unknown> | null> {
  try {
    const body = await request.json();
    return typeof body === "object" && body !== null && !Array.isArray(body) ? body as Record<string, unknown> : null;
  } catch (_error) {
    return null;
  }
}
