// Public configuration, read at build time from NEXT_PUBLIC_* variables.
// Only client-safe values live here; the Supabase key must be the publishable
// or anon key (checked below), never a secret or service_role key.

export interface PublicConfig {
  supabaseUrl: string;
  supabaseAnonKey: string;
  playStoreUrl: string;
  privacyPolicyUrl: string;
  /** Data controller shown on the legal pages (person or company name). */
  legalName: string;
  /** Where users and Google Play reach the team. */
  contactEmail: string;
}

export const config: PublicConfig = {
  supabaseUrl: process.env.NEXT_PUBLIC_SUPABASE_URL?.trim() ?? "",
  supabaseAnonKey: process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY?.trim() ?? "",
  playStoreUrl: process.env.NEXT_PUBLIC_PLAY_STORE_URL?.trim() ?? "",
  privacyPolicyUrl: process.env.NEXT_PUBLIC_PRIVACY_POLICY_URL?.trim() ?? "",
  legalName: process.env.NEXT_PUBLIC_LEGAL_NAME?.trim() ?? "",
  contactEmail: process.env.NEXT_PUBLIC_CONTACT_EMAIL?.trim() ?? "",
};

/** True for sb_publishable_ keys and JWTs whose role claim is anon. */
export function isClientKey(key: string): boolean {
  if (key.startsWith("sb_publishable_")) return true;
  if (key.startsWith("sb_secret_")) return false;
  const payload = key.split(".")[1];
  if (!payload) return false;
  try {
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const claims = JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "="))) as { role?: unknown };
    return claims.role === "anon";
  } catch {
    return false;
  }
}

export type BackendState = "ready" | "missing" | "unsafe_key";

export function backendState(c: PublicConfig = config): BackendState {
  if (!c.supabaseUrl || !c.supabaseAnonKey) return "missing";
  if (!isClientKey(c.supabaseAnonKey)) return "unsafe_key";
  return "ready";
}
