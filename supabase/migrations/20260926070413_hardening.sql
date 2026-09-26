-- Findings of the Supabase security and performance advisors on the live project.
--
-- Supabase grants EXECUTE on new functions to anon and authenticated directly,
-- so revoking from PUBLIC alone left the auth trigger function exposed as an
-- RPC endpoint. Trigger functions are never called through the API.

revoke all on function public.handle_new_user() from public, anon, authenticated;

create index if not exists reports_resolved_by_idx on public.reports (resolved_by);
