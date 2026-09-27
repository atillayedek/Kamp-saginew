// Typed calls to the admin RPCs and Edge Functions. Every call runs with the
// signed-in admin's token; the database checks is_admin() on each one, so this
// file grants nothing by itself.

import { FunctionsHttpError, type SupabaseClient } from "@supabase/supabase-js";

export interface Overview {
  total_users: number;
  new_users_7_days: number;
  new_users_30_days: number;
  status_counts: Record<string, number>;
  pending_verifications: number;
  open_reports: number;
  active_premium: number;
  purchase_count: number;
  purchases_30_days: number;
  purchases_without_price: number;
  marketing_opt_in: number;
  revenue: Record<string, { total_micros: number; last_30_days_micros: number }>;
  daily: { day: string; signups: number; purchases: number }[];
}

export interface AdminUser {
  id: string;
  email: string;
  full_name: string | null;
  username: string | null;
  university_name: string | null;
  department: string | null;
  account_status: string;
  is_premium: boolean;
  marketing_opt_in: boolean;
  created_at: string;
  total_count: number;
}

export interface Purchase {
  id: string;
  user_id: string | null;
  email: string | null;
  username: string | null;
  plan_name: string;
  order_id: string | null;
  amount_micros: number | null;
  currency: string | null;
  period_ends_at: string;
  created_at: string;
  total_count: number;
}

export interface UniversityStat {
  university_id: string;
  name: string;
  city: string;
  students: number;
  approved: number;
  premium: number;
}

export interface Broadcast {
  id: string;
  sent_by_email: string | null;
  audience: string;
  marketing: boolean;
  subject: string;
  recipient_count: number;
  sent_count: number;
  failed_count: number;
  status: "SENDING" | "SENT" | "PARTIAL" | "FAILED";
  created_at: string;
  finished_at: string | null;
}

export interface Report {
  report_id: string;
  target_kind: string;
  target_id: string;
  target_excerpt: string | null;
  reason: string;
  details: string | null;
  created_at: string;
  report_count: number;
  reporter_username: string | null;
  target_user_id: string;
  target_full_name: string | null;
  target_username: string | null;
  target_account_status: string;
}

export interface PendingVerification {
  verification_id: string;
  user_id: string;
  email: string;
  full_name: string | null;
  username: string | null;
  university_name: string | null;
  department: string | null;
  document_path: string;
  submitted_at: string;
}

export type ReportAction = "DISMISS" | "REMOVE_CONTENT" | "SUSPEND_USER";

export interface BroadcastInput {
  subject: string;
  body: string;
  audience: string;
  marketing: boolean;
  test_only: boolean;
}

export interface BroadcastResult {
  recipient_count: number;
  sent_count: number;
  failed_count: number;
  status: string;
}

async function rpc<T>(client: SupabaseClient, name: string, args: Record<string, unknown> = {}): Promise<T> {
  const { data, error } = await client.rpc(name, args);
  if (error) throw new Error(error.message);
  return data as T;
}

/** Edge Function errors carry {"error": "<code>"}; surface that code. */
async function invoke<T>(client: SupabaseClient, name: string, body: unknown, query = ""): Promise<T> {
  const { data, error } = await client.functions.invoke(`${name}${query}`, { body: body as Record<string, unknown> });
  if (error) {
    if (error instanceof FunctionsHttpError) {
      const payload = await (error.context as Response).json().catch(() => null) as { error?: string } | null;
      throw new Error(payload?.error ?? "server_error");
    }
    throw new Error(error.message);
  }
  return data as T;
}

export const adminApi = {
  overview: (c: SupabaseClient) => rpc<Overview>(c, "admin_overview"),
  users: (c: SupabaseClient, search: string, status: string | null, limit: number, offset: number) =>
    rpc<AdminUser[]>(c, "admin_list_users", { p_search: search || null, p_status: status, p_limit: limit, p_offset: offset }),
  reinstate: (c: SupabaseClient, userId: string) => rpc<void>(c, "admin_reinstate_user", { p_user_id: userId }),
  purchases: (c: SupabaseClient, limit: number, offset: number) =>
    rpc<Purchase[]>(c, "admin_list_purchases", { p_limit: limit, p_offset: offset }),
  universities: (c: SupabaseClient) => rpc<UniversityStat[]>(c, "admin_university_stats"),
  broadcasts: (c: SupabaseClient) => rpc<Broadcast[]>(c, "admin_list_broadcasts", { p_limit: 50 }),
  audienceSize: (c: SupabaseClient, audience: string, marketing: boolean) =>
    rpc<number>(c, "admin_broadcast_audience_size", { p_audience: audience, p_marketing: marketing }),
  sendBroadcast: (c: SupabaseClient, input: BroadcastInput) => invoke<BroadcastResult>(c, "admin-broadcast", input),
  pendingVerifications: (c: SupabaseClient) => rpc<PendingVerification[]>(c, "list_pending_verifications"),
  reviewVerification: (c: SupabaseClient, verificationId: string, approve: boolean, reason: string | null) =>
    rpc<void>(c, "review_student_verification", { p_verification_id: verificationId, p_approve: approve, p_reason: reason }),
  /** Short-lived link to the private PDF; storage RLS lets only the owner and admins read it. */
  documentUrl: async (c: SupabaseClient, path: string): Promise<string> => {
    const { data, error } = await c.storage.from("student-documents").createSignedUrl(path, 300);
    if (error || !data) throw new Error(error?.message ?? "document_unavailable");
    return data.signedUrl;
  },
  openReports: (c: SupabaseClient) => rpc<Report[]>(c, "list_open_reports"),
  resolveReport: (c: SupabaseClient, reportId: string, action: ReportAction) =>
    rpc<void>(c, "resolve_report", { p_report_id: reportId, p_action: action }),
};

export async function unsubscribe(client: SupabaseClient, userId: string, signature: string): Promise<void> {
  const query = `?u=${encodeURIComponent(userId)}&s=${encodeURIComponent(signature)}`;
  await invoke<{ unsubscribed: boolean }>(client, "email-unsubscribe", {}, query);
}
