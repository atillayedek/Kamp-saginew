// Calls for the "KVKK ve Uyum" and moderation sections. The database checks the staff
// role and MFA on each call; reads of personal data and every change are audited there.

import type { SupabaseClient } from "@supabase/supabase-js";
import { rpc } from "./admin-api";

export type StaffRole = "superadmin" | "verifier" | "moderator" | "compliance";

export interface StaffAccess {
  roles: StaffRole[];
  mfa_required: boolean;
  mfa_satisfied: boolean;
  idle_minutes: number;
}

export type LogTable = "consent_logs" | "access_logs" | "admin_audit_logs" | "deletion_logs";

export interface ChainCheck {
  table_name: LogTable;
  rows_checked: number;
  ok: boolean;
  first_bad_seq: number | null;
  anchor_hash: string | null;
  head_matches: boolean;
}

export interface DataSubjectRequest {
  id: string;
  request_no: string;
  user_id: string | null;
  username: string | null;
  requester_email: string | null;
  type: string;
  details: string;
  channel: string;
  status: "RECEIVED" | "IN_PROGRESS" | "ANSWERED" | "REJECTED";
  received_at: string;
  due_at: string;
  responded_at: string | null;
  response_summary: string | null;
}

export interface Breach {
  id: string;
  detected_at: string;
  description: string;
  affected_data_categories: string[];
  affected_user_count: number | null;
  measures_taken: string | null;
  reported_to_board_at: string | null;
  users_notified_at: string | null;
  closed_at: string | null;
  board_deadline: string;
  created_at: string;
}

export interface BreachInput {
  id: string | null;
  detected_at: string;
  description: string;
  affected_data_categories: string[];
  affected_user_count: number | null;
  measures_taken: string | null;
  reported_to_board_at: string | null;
  users_notified_at: string | null;
  closed: boolean;
}

export interface LegalDocStat {
  doc_type: string;
  kind: string;
  version: number;
  published_at: string;
  users: number;
  acknowledged: number;
}

export interface LegalDocument {
  doc_type: string;
  kind: string;
  version: number;
  title: string;
  content: string;
  content_sha256: string;
  published_at: string;
  is_active: boolean;
}

export interface ComplianceSetting {
  key: string;
  value: unknown;
  is_public: boolean;
  description: string;
  updated_at: string;
  updated_by: string | null;
}

export interface RetentionRow {
  day: string;
  data_category: string;
  reason: string;
  method: string;
  item_count: number;
}

export interface RetentionStatus {
  pg_cron_installed: boolean;
  pg_net_installed: boolean;
  functions_url_set: boolean;
  documents_awaiting_destruction: number;
  last_run: { started_at: string; finished_at: string | null; summary: Record<string, unknown>; error: string | null } | null;
}

export interface ScheduledJob {
  name: string;
  schedule: string;
  active: boolean;
}

export interface MatchObjection {
  username: string;
  requirement_title: string;
  matched_title: string;
  reason: string | null;
  created_at: string;
}

export interface Appeal {
  id: string;
  report_id: string;
  user_id: string;
  username: string | null;
  target_kind: string;
  reason: string;
  resolution: string;
  excerpt: string | null;
  body: string;
  status: "OPEN" | "UPHELD" | "REVERSED";
  decision_note: string | null;
  created_at: string;
  decided_at: string | null;
}

export interface CopyrightNotice {
  id: string;
  claimant_name: string;
  claimant_email: string;
  organization: string | null;
  work_description: string;
  content_location: string;
  status: "OPEN" | "REMOVED" | "REJECTED";
  resolution_note: string | null;
  created_at: string;
  resolved_at: string | null;
}

export interface CourseNoteHit {
  id: string;
  course_code: string;
  title: string;
  author_username: string | null;
  university_name: string | null;
  created_at: string;
  deleted_at: string | null;
}

export interface ContextMessage {
  message_id: string;
  sender_username: string | null;
  body: string | null;
  created_at: string;
  is_reported: boolean;
}

type C = SupabaseClient;

export const complianceApi = {
  access: (c: C) => rpc<StaffAccess>(c, "my_staff_access"),
  publicConfig: (c: C) => rpc<Record<string, unknown>>(c, "public_compliance_config"),
  logs: (c: C, table: LogTable, from: string | null, to: string | null, userId: string | null, action: string | null, limit = 500) =>
    rpc<Record<string, unknown>[]>(c, "admin_list_logs", {
      p_table: table,
      p_from: from,
      p_to: to,
      p_user_id: userId,
      p_action: action,
      p_limit: limit,
    }),
  recordAction: (c: C, action: string, targetType: string, targetId: string | null, details: Record<string, unknown>) =>
    rpc<void>(c, "record_admin_action", { p_action: action, p_target_type: targetType, p_target_id: targetId, p_details: details }),
  verifyChains: (c: C) => rpc<ChainCheck[]>(c, "admin_verify_log_chains"),
  requests: (c: C, status: string | null) => rpc<DataSubjectRequest[]>(c, "admin_list_data_subject_requests", { p_status: status }),
  updateRequest: (c: C, id: string, status: string, summary: string | null) =>
    rpc<void>(c, "admin_update_data_subject_request", { p_id: id, p_status: status, p_response_summary: summary }),
  recordRequest: (c: C, type: string, details: string, channel: string, email: string, receivedAt: string | null) =>
    rpc<string>(c, "admin_record_data_subject_request", {
      p_type: type,
      p_details: details,
      p_channel: channel,
      p_requester_email: email,
      p_user_id: null,
      p_received_at: receivedAt,
    }),
  breaches: (c: C) => rpc<Breach[]>(c, "admin_list_breaches"),
  saveBreach: (c: C, b: BreachInput) =>
    rpc<string>(c, "admin_save_breach", {
      p_id: b.id,
      p_detected_at: b.detected_at,
      p_description: b.description,
      p_affected_data_categories: b.affected_data_categories,
      p_affected_user_count: b.affected_user_count,
      p_measures_taken: b.measures_taken,
      p_reported_to_board_at: b.reported_to_board_at,
      p_users_notified_at: b.users_notified_at,
      p_closed: b.closed,
    }),
  legalStats: (c: C) => rpc<LegalDocStat[]>(c, "admin_legal_document_stats"),
  legalDocument: async (c: C, type: string, version: number | null = null) =>
    (await rpc<LegalDocument[]>(c, "get_legal_document", { p_doc_type: type, p_version: version }))[0] ?? null,
  publishLegal: (c: C, type: string, title: string, content: string) =>
    rpc<number>(c, "admin_publish_legal_document", { p_doc_type: type, p_title: title, p_content: content }),
  settings: (c: C) => rpc<ComplianceSetting[]>(c, "admin_list_compliance_settings"),
  setSetting: (c: C, key: string, value: unknown) => rpc<void>(c, "admin_set_compliance_setting", { p_key: key, p_value: value }),
  retentionReport: (c: C, days: number) => rpc<RetentionRow[]>(c, "admin_retention_report", { p_days: days }),
  retentionStatus: (c: C) => rpc<RetentionStatus>(c, "admin_retention_status"),
  scheduleStatus: (c: C) => rpc<{ jobs: ScheduledJob[] }>(c, "admin_schedule_status"),
  ensureSchedules: (c: C) => rpc<{ jobs: ScheduledJob[] }>(c, "admin_ensure_schedules"),
  matchObjections: (c: C) => rpc<MatchObjection[]>(c, "admin_list_match_objections"),
  appeals: (c: C) => rpc<Appeal[]>(c, "admin_list_appeals"),
  decideAppeal: (c: C, id: string, reverse: boolean, note: string) =>
    rpc<void>(c, "admin_decide_appeal", { p_appeal_id: id, p_reverse: reverse, p_note: note }),
  copyrightNotices: (c: C) => rpc<CopyrightNotice[]>(c, "admin_list_copyright_notices"),
  resolveCopyright: (c: C, id: string, status: "REMOVED" | "REJECTED", note: string, noteId: string | null) =>
    rpc<void>(c, "admin_resolve_copyright_notice", { p_id: id, p_status: status, p_note: note, p_note_id: noteId }),
  searchNotes: (c: C, query: string) => rpc<CourseNoteHit[]>(c, "admin_search_course_notes", { p_query: query }),
  reportContext: (c: C, reportId: string) => rpc<ContextMessage[]>(c, "admin_report_context", { p_report_id: reportId }),
};

/** CSV with a BOM so Excel opens Turkish text correctly; nested values become JSON. */
export function toCsv(rows: Record<string, unknown>[]): string {
  const keys = [...new Set(rows.flatMap((r) => Object.keys(r)))];
  const cell = (value: unknown) => {
    const text = value === null || value === undefined ? "" : typeof value === "object" ? JSON.stringify(value) : String(value);
    return /[",\n\r;]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
  };
  return "﻿" + [keys.join(","), ...rows.map((r) => keys.map((k) => cell(r[k])).join(","))].join("\r\n");
}
