import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { backendState, config } from "./config";

let client: SupabaseClient | null = null;

/** Null when the site was built without a usable Supabase configuration. */
export function supabase(): SupabaseClient | null {
  if (backendState() !== "ready") return null;
  client ??= createClient(config.supabaseUrl, config.supabaseAnonKey, {
    auth: { persistSession: true, autoRefreshToken: true, storageKey: "kampusagi-admin" },
  });
  return client;
}
