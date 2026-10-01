-- KVKK md.11 rights, retention and destruction, moderation transparency (Phase 20, D57).
--
--   * data_subject_requests: md.11 applications with a 30-day due date; staff answers are audited.
--   * breach_register: data breach records with the 72-hour notification deadline.
--   * Account deletion has a grace period (deletion_grace_days); the retention job then deletes
--     the account. Group and channel messages are kept as "deleted user" (sender set to null);
--     access, consent and audit logs have no foreign key to the account, so they outlive it for
--     their legal retention without being part of the profile.
--   * Data export: export_my_data() builds everything about the person; the export-my-data
--     Edge Function stores it in the private data-exports bucket for a short-lived link.
--   * Retention: retention_due() tells the retention-run Edge Function which student documents,
--     exports and accounts to delete and which inactive people to warn; every destruction is
--     recorded in deletion_logs. Log tables are purged by retention_purge_sql().
--   * Student documents keep only the verdict and the document's SHA-256 after the file is gone.
--   * Moderation: new report reasons, removal/suspension notifications, appeals, a public
--     copyright notice form, and a logged, limited view of the context of a reported message.
--   * Data minimisation: surnames are hidden from other students unless the person shows them;
--     tags naming special categories of personal data (KVKK md.6) are refused; people can
--     object to an automatically suggested match.

-- Data subject requests ---------------------------------------------------------------------

create sequence public.data_subject_request_no_seq;

create table public.data_subject_requests (
    id uuid primary key default gen_random_uuid(),
    request_no text not null unique,
    user_id uuid references public.profiles (id) on delete set null,
    requester_email text,
    type text not null check (type in (
        'access', 'purpose', 'third_parties', 'rectification', 'erasure',
        'notify_third_parties', 'objection_automated', 'compensation', 'other'
    )),
    details text not null check (char_length(btrim(details)) between 10 and 4000),
    channel text not null check (channel in ('app', 'email', 'kep', 'mail')),
    status text not null default 'RECEIVED' check (status in ('RECEIVED', 'IN_PROGRESS', 'ANSWERED', 'REJECTED')),
    received_at timestamptz not null default now(),
    due_at timestamptz not null,
    responded_at timestamptz,
    response_summary text check (response_summary is null or char_length(btrim(response_summary)) between 3 and 4000),
    handled_by uuid,
    updated_at timestamptz not null default now(),
    constraint data_subject_requests_answer check ((status in ('ANSWERED', 'REJECTED')) = (responded_at is not null and response_summary is not null))
);

create index data_subject_requests_user_idx on public.data_subject_requests (user_id);
create index data_subject_requests_open_idx on public.data_subject_requests (due_at) where status in ('RECEIVED', 'IN_PROGRESS');

alter table public.data_subject_requests enable row level security;
revoke all on table public.data_subject_requests from public, anon, authenticated, service_role;

create trigger data_subject_requests_audit_staff after update on public.data_subject_requests
    for each row execute function public.audit_staff_change();

create function public.next_request_no()
returns text
language sql
volatile
set search_path = ''
as $$
    select 'KVKK-' || to_char(now() at time zone 'Europe/Istanbul', 'YYYY') || '-'
        || lpad(nextval('public.data_subject_request_no_seq')::text, 6, '0')
$$;

create function public.submit_data_subject_request(p_type text, p_details text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_no text;
begin
    if auth.uid() is null or not exists (select 1 from public.profiles where id = auth.uid()) then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;
    if p_type is null or p_type not in ('access', 'purpose', 'third_parties', 'rectification', 'erasure',
                                        'notify_third_parties', 'objection_automated', 'compensation', 'other')
        or p_details is null or char_length(btrim(p_details)) not between 10 and 4000 then
        raise exception using errcode = 'P0001', message = 'invalid_request';
    end if;
    if (select count(*) from public.data_subject_requests
        where user_id = auth.uid() and received_at > now() - interval '24 hours') >= 5 then
        raise exception using errcode = 'P0001', message = 'rate_limited';
    end if;
    v_no := public.next_request_no();
    insert into public.data_subject_requests (request_no, user_id, requester_email, type, details, channel, due_at)
    values (v_no, auth.uid(), (select email from public.profiles where id = auth.uid()), p_type, btrim(p_details), 'app',
            now() + make_interval(days => public.compliance_int('dsr_response_days')));
    return v_no;
end;
$$;

create function public.my_data_subject_requests()
returns table (request_no text, type text, details text, status text, received_at timestamptz, due_at timestamptz,
               responded_at timestamptz, response_summary text)
language sql
stable
security definer
set search_path = ''
as $$
    select r.request_no, r.type, r.details, r.status, r.received_at, r.due_at, r.responded_at, r.response_summary
    from public.data_subject_requests r
    where r.user_id = auth.uid()
    order by r.received_at desc
$$;

-- Applications that arrive by e-mail, KEP or post are recorded by compliance staff.
create function public.admin_record_data_subject_request(
    p_type text, p_details text, p_channel text, p_requester_email text, p_user_id uuid default null,
    p_received_at timestamptz default null
)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_no text;
    v_received timestamptz := coalesce(p_received_at, now());
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if p_channel not in ('email', 'kep', 'mail') or v_received > now() + interval '5 minutes'
        or p_type is null or char_length(btrim(coalesce(p_details, ''))) not between 10 and 4000 then
        raise exception using errcode = 'P0001', message = 'invalid_request';
    end if;
    v_no := public.next_request_no();
    insert into public.data_subject_requests (request_no, user_id, requester_email, type, details, channel, received_at, due_at)
    values (v_no, p_user_id, nullif(btrim(p_requester_email), ''), p_type, btrim(p_details), p_channel, v_received,
            v_received + make_interval(days => public.compliance_int('dsr_response_days')));
    perform public.write_admin_audit('data_subject_requests.insert', 'data_subject_requests', v_no,
                                     public.request_audit_reason(), jsonb_build_object('channel', p_channel));
    return v_no;
end;
$$;

create function public.admin_list_data_subject_requests(p_status text default null)
returns table (id uuid, request_no text, user_id uuid, username text, requester_email text, type text, details text,
               channel text, status text, received_at timestamptz, due_at timestamptz, responded_at timestamptz,
               response_summary text, handled_by uuid)
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    perform public.write_admin_audit('view.data_subject_requests', 'data_subject_requests', null,
                                     coalesce(public.request_audit_reason(), 'Panel görüntüleme'));
    return query
    select r.id, r.request_no, r.user_id, p.username, r.requester_email, r.type, r.details, r.channel, r.status,
           r.received_at, r.due_at, r.responded_at, r.response_summary, r.handled_by
    from public.data_subject_requests r
    left join public.profiles p on p.id = r.user_id
    where p_status is null or r.status = p_status
    order by (r.status in ('RECEIVED', 'IN_PROGRESS')) desc, r.due_at
    limit 500;
end;
$$;

create function public.admin_update_data_subject_request(p_id uuid, p_status text, p_response_summary text default null)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_request public.data_subject_requests%rowtype;
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    select * into v_request from public.data_subject_requests where id = p_id for update;
    if not found then
        raise exception using errcode = 'P0001', message = 'request_not_found';
    end if;
    if p_status not in ('IN_PROGRESS', 'ANSWERED', 'REJECTED') or v_request.status in ('ANSWERED', 'REJECTED')
        or (p_status in ('ANSWERED', 'REJECTED') and char_length(btrim(coalesce(p_response_summary, ''))) < 3) then
        raise exception using errcode = 'P0001', message = 'invalid_request';
    end if;
    update public.data_subject_requests
    set status = p_status,
        responded_at = case when p_status in ('ANSWERED', 'REJECTED') then now() end,
        response_summary = case when p_status in ('ANSWERED', 'REJECTED') then btrim(p_response_summary) end,
        handled_by = auth.uid(),
        updated_at = now()
    where id = p_id;
    if p_status in ('ANSWERED', 'REJECTED') and v_request.user_id is not null then
        insert into public.notifications (user_id, kind, subject_id) values (v_request.user_id, 'DSR_ANSWERED', p_id);
    end if;
end;
$$;

-- Breach register --------------------------------------------------------------------------------

create table public.breach_register (
    id uuid primary key default gen_random_uuid(),
    detected_at timestamptz not null,
    description text not null check (char_length(btrim(description)) between 10 and 8000),
    affected_data_categories text[] not null default '{}',
    affected_user_count integer check (affected_user_count is null or affected_user_count >= 0),
    measures_taken text check (measures_taken is null or char_length(measures_taken) <= 8000),
    reported_to_board_at timestamptz,
    users_notified_at timestamptz,
    closed_at timestamptz,
    created_by uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index breach_register_open_idx on public.breach_register (detected_at) where closed_at is null;

alter table public.breach_register enable row level security;
revoke all on table public.breach_register from public, anon, authenticated, service_role;

create trigger breach_register_audit_staff after insert or update on public.breach_register
    for each row execute function public.audit_staff_change();

create function public.admin_save_breach(
    p_id uuid,
    p_detected_at timestamptz,
    p_description text,
    p_affected_data_categories text[],
    p_affected_user_count integer,
    p_measures_taken text,
    p_reported_to_board_at timestamptz,
    p_users_notified_at timestamptz,
    p_closed boolean
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_id uuid := p_id;
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if p_detected_at is null or p_detected_at > now() + interval '5 minutes'
        or char_length(btrim(coalesce(p_description, ''))) not between 10 and 8000 then
        raise exception using errcode = 'P0001', message = 'invalid_breach';
    end if;
    if v_id is null then
        insert into public.breach_register (detected_at, description, affected_data_categories, affected_user_count,
                                            measures_taken, reported_to_board_at, users_notified_at, closed_at, created_by)
        values (p_detected_at, btrim(p_description), coalesce(p_affected_data_categories, '{}'), p_affected_user_count,
                p_measures_taken, p_reported_to_board_at, p_users_notified_at,
                case when p_closed then now() end, auth.uid())
        returning id into v_id;
    else
        update public.breach_register
        set detected_at = p_detected_at, description = btrim(p_description),
            affected_data_categories = coalesce(p_affected_data_categories, '{}'),
            affected_user_count = p_affected_user_count, measures_taken = p_measures_taken,
            reported_to_board_at = p_reported_to_board_at, users_notified_at = p_users_notified_at,
            closed_at = case when p_closed then coalesce(closed_at, now()) end,
            updated_at = now()
        where id = v_id;
        if not found then
            raise exception using errcode = 'P0001', message = 'breach_not_found';
        end if;
    end if;
    return v_id;
end;
$$;

create function public.admin_list_breaches()
returns table (id uuid, detected_at timestamptz, description text, affected_data_categories text[],
               affected_user_count integer, measures_taken text, reported_to_board_at timestamptz,
               users_notified_at timestamptz, closed_at timestamptz, board_deadline timestamptz, created_at timestamptz)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    return query
    select b.id, b.detected_at, b.description, b.affected_data_categories, b.affected_user_count, b.measures_taken,
           b.reported_to_board_at, b.users_notified_at, b.closed_at,
           b.detected_at + make_interval(hours => public.compliance_int('breach_notify_hours')), b.created_at
    from public.breach_register b
    order by (b.closed_at is null) desc, b.detected_at desc;
end;
$$;

-- Account deletion with a grace period -----------------------------------------------------------------

alter table public.profiles
    add column deletion_requested_at timestamptz,
    add column inactive_notice_sent_at timestamptz,
    add column show_full_name boolean not null default false;

-- What other students see: "Ayşe Yılmaz" only if the person allows it, otherwise "Ayşe Y.".
alter table public.profiles
    add column public_name text generated always as (
        case when show_full_name or full_name is null then full_name
             else regexp_replace(btrim(full_name), '\s+(\S)\S*$', ' \1.') end
    ) stored;

create function public.request_account_deletion()
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;
    update public.profiles set deletion_requested_at = coalesce(deletion_requested_at, now()), updated_at = now()
    where id = auth.uid();
    return (select deletion_requested_at + make_interval(days => public.compliance_int('deletion_grace_days'))
            from public.profiles where id = auth.uid());
end;
$$;

create function public.cancel_account_deletion()
returns void
language sql
security definer
set search_path = ''
as $$
    update public.profiles set deletion_requested_at = null, updated_at = now()
    where id = auth.uid() and deletion_requested_at is not null
$$;

-- The deletion date the app shows; null when no deletion is pending.
create function public.my_account_deletion()
returns timestamptz
language sql
stable
security definer
set search_path = ''
as $$
    select deletion_requested_at + make_interval(days => public.compliance_int('deletion_grace_days'))
    from public.profiles where id = auth.uid()
$$;

create function public.set_show_full_name(p_show boolean)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.profiles set show_full_name = coalesce(p_show, false), updated_at = now() where id = auth.uid()
$$;

-- Group and channel messages outlive their author as "deleted user".
alter table public.group_messages alter column sender_id drop not null;
alter table public.group_messages drop constraint group_messages_sender_id_fkey;
alter table public.group_messages
    add constraint group_messages_sender_id_fkey foreign key (sender_id) references public.profiles (id) on delete set null;

-- Data export ---------------------------------------------------------------------------------------------

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('data-exports', 'data-exports', false, 52428800, array['application/json', 'text/html'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit,
                               allowed_mime_types = excluded.allowed_mime_types;

-- Read only through the short-lived signed link the Edge Function returns; no direct access.
create table public.data_exports (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    json_path text not null,
    html_path text not null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index data_exports_user_idx on public.data_exports (user_id, created_at desc);
create index data_exports_live_idx on public.data_exports (created_at) where deleted_at is null;

alter table public.data_exports enable row level security;
revoke all on table public.data_exports from public, anon, authenticated, service_role;

-- Called by the export-my-data Edge Function with the caller's id after the files are stored.
create function public.record_data_export(p_user_id uuid, p_json_path text, p_html_path text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_id uuid;
begin
    if (select count(*) from public.data_exports where user_id = p_user_id and created_at > now() - interval '24 hours') >= 3 then
        raise exception using errcode = 'P0001', message = 'rate_limited';
    end if;
    insert into public.data_exports (user_id, json_path, html_path) values (p_user_id, p_json_path, p_html_path)
    returning id into v_id;
    return v_id;
end;
$$;

create function public.can_export_data(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select (select count(*) from public.data_exports where user_id = p_user_id and created_at > now() - interval '24 hours') < 3
$$;

-- Student documents: verdict and fingerprint stay, the file goes --------------------------------------------

alter table public.student_verifications
    add column document_sha256 text check (document_sha256 is null or document_sha256 ~ '^[0-9a-f]{64}$'),
    add column document_purged_at timestamptz;

create index student_verifications_purge_idx on public.student_verifications (reviewed_at)
    where status <> 'PENDING' and document_purged_at is null;

create function public.set_student_document_sha256(p_user_id uuid, p_path text, p_sha256 text)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.student_verifications set document_sha256 = lower(p_sha256)
    where user_id = p_user_id and document_path = p_path and document_sha256 is null
$$;

-- Retention ---------------------------------------------------------------------------------------------------

create table public.retention_runs (
    id uuid primary key default gen_random_uuid(),
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    summary jsonb not null default '{}',
    error text
);

alter table public.retention_runs enable row level security;
revoke all on table public.retention_runs from public, anon, authenticated, service_role;

-- Last time the person was seen: sign-in or a day the app was opened.
create function public.last_activity_at(p_user_id uuid)
returns timestamptz
language sql
stable
security definer
set search_path = ''
as $$
    select greatest(
        (select u.last_sign_in_at from auth.users u where u.id = p_user_id),
        (select max(d.day)::timestamptz from public.user_activity_days d where d.user_id = p_user_id),
        (select p.created_at from public.profiles p where p.id = p_user_id)
    )
$$;

-- Work for the retention-run Edge Function (service role).
create function public.retention_due()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select jsonb_build_object(
        'documents', coalesce((select jsonb_agg(jsonb_build_object('id', v.id, 'user_id', v.user_id, 'path', v.document_path))
            from public.student_verifications v
            where v.status <> 'PENDING' and v.document_purged_at is null
              and v.reviewed_at < now() - make_interval(days => public.compliance_int('document_retention_days'))), '[]'),
        'exports', coalesce((select jsonb_agg(jsonb_build_object('id', e.id, 'user_id', e.user_id,
                                                                  'paths', jsonb_build_array(e.json_path, e.html_path)))
            from public.data_exports e
            where e.deleted_at is null
              and e.created_at < now() - make_interval(days => public.compliance_int('data_export_retention_days'))), '[]'),
        'accounts', coalesce((select jsonb_agg(x) from (
            select jsonb_build_object('user_id', p.id, 'reason', 'user_request') as x
            from public.profiles p
            where p.deletion_requested_at < now() - make_interval(days => public.compliance_int('deletion_grace_days'))
            union all
            select jsonb_build_object('user_id', p.id, 'reason', 'inactive_account')
            from public.profiles p
            where p.deletion_requested_at is null
              and p.inactive_notice_sent_at < now() - make_interval(days => public.compliance_int('inactive_notice_days'))
              and public.last_activity_at(p.id) < p.inactive_notice_sent_at
        ) a), '[]'),
        'inactive_to_warn', coalesce((select jsonb_agg(jsonb_build_object('user_id', p.id, 'email', p.email))
            from public.profiles p
            where p.deletion_requested_at is null and p.inactive_notice_sent_at is null and p.email <> ''
              and public.last_activity_at(p.id) < now() - make_interval(days => public.compliance_int('inactive_account_days'))), '[]'),
        'inactive_notice_days', public.compliance_int('inactive_notice_days')
    )
$$;

create function public.record_document_purged(p_verification_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid;
begin
    update public.student_verifications set document_purged_at = now()
    where id = p_verification_id and document_purged_at is null
    returning user_id into v_user;
    if found then
        insert into public.deletion_logs (subject_user_id, data_category, reason, method, details)
        values (v_user, 'student_document', 'document_reviewed', 'deleted',
                jsonb_build_object('verification_id', p_verification_id));
    end if;
end;
$$;

create function public.record_export_deleted(p_export_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid;
begin
    update public.data_exports set deleted_at = now() where id = p_export_id and deleted_at is null
    returning user_id into v_user;
    if found then
        insert into public.deletion_logs (subject_user_id, data_category, reason, method, item_count)
        values (v_user, 'data_export', 'export_expired', 'deleted', 2);
    end if;
end;
$$;

create function public.record_inactive_notice(p_user_id uuid)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.profiles set inactive_notice_sent_at = now() where id = p_user_id and inactive_notice_sent_at is null
$$;

-- A sign-in after the warning cancels it.
create function public.clear_inactive_notice()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.profiles set inactive_notice_sent_at = null where id = new.user_id and inactive_notice_sent_at is not null;
    return new;
end;
$$;

create trigger user_activity_days_clear_notice after insert on public.user_activity_days
    for each row execute function public.clear_inactive_notice();

-- Before the auth user is deleted: anonymise what outlives the account and record what goes.
create function public.prepare_account_deletion(p_user_id uuid, p_reason text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_counts jsonb;
    v_anonymized integer;
    v_group record;
    v_new_owner uuid;
    v_transferred integer := 0;
begin
    if p_reason not in ('user_request', 'inactive_account', 'admin_removal') then
        raise exception using errcode = 'P0001', message = 'invalid_reason';
    end if;
    if not exists (select 1 from public.profiles where id = p_user_id) then
        raise exception using errcode = 'P0001', message = 'profile_not_found';
    end if;
    v_counts := jsonb_build_object(
        'posts', (select count(*) from public.posts where author_id = p_user_id),
        'comments', (select count(*) from public.comments where author_id = p_user_id),
        'messages', (select count(*) from public.messages where sender_id = p_user_id),
        'requirements', (select count(*) from public.requirements where owner_id = p_user_id),
        'course_notes', (select count(*) from public.course_notes where author_id = p_user_id),
        'notifications', (select count(*) from public.notifications where user_id = p_user_id)
    );
    -- Groups and channels the person founded go to the longest-standing admin (or member)
    -- instead of disappearing for everyone; a group with no other member goes with the account.
    for v_group in select g.id from public.groups g where g.owner_id = p_user_id and g.deleted_at is null loop
        select m.user_id into v_new_owner from public.group_members m
        where m.group_id = v_group.id and m.user_id <> p_user_id
        order by (m.role = 'ADMIN') desc, m.joined_at
        limit 1;
        if v_new_owner is not null then
            update public.groups set owner_id = v_new_owner where id = v_group.id;
            update public.group_members set role = 'OWNER' where group_id = v_group.id and user_id = v_new_owner;
            v_transferred := v_transferred + 1;
        end if;
    end loop;

    update public.group_messages set sender_id = null where sender_id = p_user_id;
    get diagnostics v_anonymized = row_count;

    insert into public.deletion_logs (subject_user_id, data_category, reason, method, item_count, details)
    values (p_user_id, 'account', p_reason, 'deleted', 1, v_counts),
           (p_user_id, 'group_messages', p_reason, 'anonymized', v_anonymized,
            jsonb_build_object('groups_transferred', v_transferred));
    return v_counts || jsonb_build_object('group_messages_anonymized', v_anonymized, 'groups_transferred', v_transferred);
end;
$$;

create function public.record_retention_run(p_started_at timestamptz, p_summary jsonb, p_error text)
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.retention_runs (started_at, finished_at, summary, error)
    values (p_started_at, now(), coalesce(p_summary, '{}'), left(p_error, 2000))
$$;

-- Database-only part of the daily destruction: logs, crash reports, old applications and runs.
create function public.retention_purge_sql()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_result jsonb := '{}';
    v_count integer;
begin
    v_result := v_result || jsonb_build_object(
        'access_logs', public.purge_log('access_logs', now() - make_interval(days => public.compliance_int('access_log_retention_days'))),
        'consent_logs', public.purge_log('consent_logs', now() - make_interval(days => public.compliance_int('consent_log_retention_days'))),
        'admin_audit_logs', public.purge_log('admin_audit_logs', now() - make_interval(days => public.compliance_int('admin_audit_retention_days'))),
        'deletion_logs', public.purge_log('deletion_logs', now() - make_interval(days => public.compliance_int('deletion_log_retention_days'))));

    delete from public.client_errors
    where created_at < now() - make_interval(days => public.compliance_int('client_error_retention_days'));
    get diagnostics v_count = row_count;
    if v_count > 0 then
        insert into public.deletion_logs (data_category, reason, method, item_count)
        values ('client_errors', 'retention_expired', 'deleted', v_count);
    end if;
    v_result := v_result || jsonb_build_object('client_errors', v_count);

    delete from public.data_subject_requests
    where received_at < now() - make_interval(days => public.compliance_int('deletion_log_retention_days'))
      and status in ('ANSWERED', 'REJECTED');
    get diagnostics v_count = row_count;
    if v_count > 0 then
        insert into public.deletion_logs (data_category, reason, method, item_count)
        values ('data_subject_requests', 'retention_expired', 'deleted', v_count);
    end if;
    v_result := v_result || jsonb_build_object('data_subject_requests', v_count);

    delete from public.retention_runs where started_at < now() - interval '400 days';
    return v_result;
end;
$$;

-- pg_cron: SQL purge daily, then the Edge Function (storage, accounts, e-mail) via pg_net.
create function public.run_daily_retention()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_settings public.push_settings%rowtype;
    v_summary jsonb;
begin
    v_summary := public.retention_purge_sql();
    select * into v_settings from public.push_settings;
    if v_settings.functions_url is null or not exists (select 1 from pg_extension where extname = 'pg_net') then
        insert into public.retention_runs (finished_at, summary, error)
        values (now(), v_summary, 'retention-run not configured: run the setup from the admin panel');
        return;
    end if;
    execute 'select net.http_post(url := $1, body := $2, headers := $3, timeout_milliseconds := 60000)'
    using v_settings.functions_url || '/retention-run',
          jsonb_build_object('sql', v_summary),
          jsonb_build_object('Content-Type', 'application/json', 'x-webhook-secret', v_settings.webhook_secret);
end;
$$;

do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.schedule('kvkk-daily-retention', '17 3 * * *', 'select public.run_daily_retention()');
    end if;
end;
$$;

create function public.admin_retention_report(p_days integer default 30)
returns table (day date, data_category text, reason text, method text, item_count bigint)
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    perform public.write_admin_audit('view.retention_report', 'deletion_logs', null,
                                     coalesce(public.request_audit_reason(), 'Panel görüntüleme'));
    return query
    select (d.created_at at time zone 'Europe/Istanbul')::date, d.data_category, d.reason, d.method, sum(d.item_count)::bigint
    from public.deletion_logs d
    where d.created_at > now() - make_interval(days => least(greatest(coalesce(p_days, 30), 1), 400))
    group by 1, 2, 3, 4
    order by 1 desc, 2;
end;
$$;

-- Moderation transparency ---------------------------------------------------------------------------------------

alter table public.notifications add column subject_id uuid;
create unique index notifications_subject_once_idx on public.notifications (user_id, kind, subject_id)
    where subject_id is not null;

-- The person whose content was removed or who was suspended is told why (and can appeal).
create function public.notify_moderation_decision()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if old.status = 'OPEN' and new.status = 'RESOLVED' and new.target_user_id is not null
        and new.resolution in ('REMOVE_CONTENT', 'SUSPEND_USER') then
        insert into public.notifications (user_id, kind, subject_id)
        values (new.target_user_id,
                case when new.resolution = 'SUSPEND_USER' then 'ACCOUNT_SUSPENDED' else 'CONTENT_REMOVED' end::public.notification_kind,
                case when new.resolution = 'SUSPEND_USER' then new.target_user_id else new.target_id end)
        on conflict (user_id, kind, subject_id) where subject_id is not null do nothing;
    end if;
    return null;
end;
$$;

create trigger reports_notify_decision after update of status on public.reports
    for each row execute function public.notify_moderation_decision();

create table public.moderation_appeals (
    id uuid primary key default gen_random_uuid(),
    report_id uuid not null unique references public.reports (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    body text not null check (char_length(btrim(body)) between 10 and 2000),
    status text not null default 'OPEN' check (status in ('OPEN', 'UPHELD', 'REVERSED')),
    decision_note text check (decision_note is null or char_length(btrim(decision_note)) between 3 and 2000),
    decided_by uuid,
    decided_at timestamptz,
    created_at timestamptz not null default now()
);

create index moderation_appeals_user_idx on public.moderation_appeals (user_id);
create index moderation_appeals_open_idx on public.moderation_appeals (created_at) where status = 'OPEN';

alter table public.moderation_appeals enable row level security;
revoke all on table public.moderation_appeals from public, anon, authenticated, service_role;

create trigger moderation_appeals_audit_staff after update on public.moderation_appeals
    for each row execute function public.audit_staff_change();

-- Decisions about the person's own content, with the reason category and appeal status.
create function public.my_moderation_decisions()
returns table (report_id uuid, target_kind public.report_target, reason public.report_reason, resolution text,
               excerpt text, resolved_at timestamptz, appeal_status text, appeal_note text)
language sql
stable
security definer
set search_path = ''
as $$
    select distinct on (r.target_kind, r.target_id)
           r.id, r.target_kind, r.reason, r.resolution, r.target_excerpt, r.resolved_at, a.status, a.decision_note
    from public.reports r
    left join public.moderation_appeals a on a.report_id = r.id
    where r.target_user_id = auth.uid() and r.status = 'RESOLVED'
      and r.resolution in ('REMOVE_CONTENT', 'SUSPEND_USER')
    order by r.target_kind, r.target_id, a.created_at desc nulls last, r.resolved_at
$$;

create function public.submit_moderation_appeal(p_report_id uuid, p_body text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not exists (select 1 from public.reports r
                   where r.id = p_report_id and r.target_user_id = auth.uid() and r.status = 'RESOLVED'
                     and r.resolution in ('REMOVE_CONTENT', 'SUSPEND_USER')) then
        raise exception using errcode = 'P0001', message = 'report_not_found';
    end if;
    if p_body is null or char_length(btrim(p_body)) not between 10 and 2000 then
        raise exception using errcode = 'P0001', message = 'invalid_appeal';
    end if;
    if exists (select 1 from public.moderation_appeals where report_id = p_report_id) then
        raise exception using errcode = 'P0001', message = 'appeal_exists';
    end if;
    insert into public.moderation_appeals (report_id, user_id, body) values (p_report_id, auth.uid(), btrim(p_body));
end;
$$;

create function public.admin_list_appeals()
returns table (id uuid, report_id uuid, user_id uuid, username text, target_kind public.report_target,
               reason public.report_reason, resolution text, excerpt text, body text, status text,
               decision_note text, created_at timestamptz, decided_at timestamptz)
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
    if not public.has_staff_role('moderator') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    perform public.write_admin_audit('view.moderation_appeals', 'moderation_appeals', null,
                                     coalesce(public.request_audit_reason(), 'Panel görüntüleme'));
    return query
    select a.id, a.report_id, a.user_id, p.username, r.target_kind, r.reason, r.resolution, r.target_excerpt, a.body,
           a.status, a.decision_note, a.created_at, a.decided_at
    from public.moderation_appeals a
    join public.reports r on r.id = a.report_id
    left join public.profiles p on p.id = a.user_id
    order by (a.status = 'OPEN') desc, a.created_at
    limit 200;
end;
$$;

-- Reversing restores removed posts, comments, group messages, notes and groups, or the account.
create function public.admin_decide_appeal(p_appeal_id uuid, p_reverse boolean, p_note text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_appeal public.moderation_appeals%rowtype;
    v_report public.reports%rowtype;
begin
    if not public.has_staff_role('moderator') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    select * into v_appeal from public.moderation_appeals where id = p_appeal_id and status = 'OPEN' for update;
    if not found then
        raise exception using errcode = 'P0001', message = 'appeal_not_found';
    end if;
    if p_reverse is null or char_length(btrim(coalesce(p_note, ''))) < 3 then
        raise exception using errcode = 'P0001', message = 'invalid_appeal';
    end if;
    select * into v_report from public.reports where id = v_appeal.report_id;
    if p_reverse then
        if v_report.resolution = 'SUSPEND_USER' then
            update public.profiles set account_status = 'APPROVED', updated_at = now()
            where id = v_report.target_user_id and account_status = 'SUSPENDED';
        elsif v_report.target_kind = 'POST' then
            update public.posts set deleted_at = null where id = v_report.target_id;
        elsif v_report.target_kind = 'COMMENT' then
            update public.comments set deleted_at = null where id = v_report.target_id;
        elsif v_report.target_kind = 'GROUP_MESSAGE' then
            update public.group_messages set deleted_at = null where id = v_report.target_id;
        elsif v_report.target_kind = 'GROUP' then
            update public.groups set deleted_at = null where id = v_report.target_id;
        elsif v_report.target_kind = 'NOTE' then
            update public.course_notes set deleted_at = null where id = v_report.target_id;
        end if;
        -- Direct messages are deleted on removal and cannot be restored.
    end if;
    update public.moderation_appeals
    set status = case when p_reverse then 'REVERSED' else 'UPHELD' end, decision_note = btrim(p_note),
        decided_by = auth.uid(), decided_at = now()
    where id = p_appeal_id;
    insert into public.notifications (user_id, kind, subject_id) values (v_appeal.user_id, 'APPEAL_DECIDED', p_appeal_id)
    on conflict (user_id, kind, subject_id) where subject_id is not null do nothing;
end;
$$;

-- A moderator may see the reported message with a few messages around it; every look is audited.
create function public.admin_report_context(p_report_id uuid)
returns table (message_id uuid, sender_username text, body text, created_at timestamptz, is_reported boolean)
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_report public.reports%rowtype;
    v_container uuid;
    v_at timestamptz;
    v_n integer := public.compliance_int('report_context_messages');
begin
    if not public.has_staff_role('moderator') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    select * into v_report from public.reports where id = p_report_id;
    if not found or v_report.target_kind not in ('MESSAGE', 'GROUP_MESSAGE') then
        raise exception using errcode = 'P0001', message = 'report_not_found';
    end if;
    -- The reason is mandatory here: this is the only way staff can read private messages.
    perform public.write_admin_audit('view.report_context', 'reports', p_report_id::text, public.request_audit_reason(),
                                     jsonb_build_object('messages_each_side', v_n));
    if v_report.target_kind = 'MESSAGE' then
        select m.conversation_id, m.created_at into v_container, v_at from public.messages m where m.id = v_report.target_id;
        if not found then
            return; -- The message was already removed; the report keeps its excerpt.
        end if;
        return query
        (select m.id, p.username, m.body, m.created_at, m.id = v_report.target_id
         from public.messages m left join public.profiles p on p.id = m.sender_id
         where m.conversation_id = v_container and m.created_at <= v_at
         order by m.created_at desc limit v_n + 1)
        union all
        (select m.id, p.username, m.body, m.created_at, false
         from public.messages m left join public.profiles p on p.id = m.sender_id
         where m.conversation_id = v_container and m.created_at > v_at
         order by m.created_at limit v_n)
        order by 4;
    else
        select m.group_id, m.created_at into v_container, v_at from public.group_messages m where m.id = v_report.target_id;
        if not found then
            return;
        end if;
        return query
        (select m.id, p.username, m.body, m.created_at, m.id = v_report.target_id
         from public.group_messages m left join public.profiles p on p.id = m.sender_id
         where m.group_id = v_container and m.created_at <= v_at
         order by m.created_at desc limit v_n + 1)
        union all
        (select m.id, p.username, m.body, m.created_at, false
         from public.group_messages m left join public.profiles p on p.id = m.sender_id
         where m.group_id = v_container and m.created_at > v_at
         order by m.created_at limit v_n)
        order by 4;
    end if;
end;
$$;

-- Copyright notices (FSEK) from right holders, without an account --------------------------------------

create table public.copyright_notices (
    id uuid primary key default gen_random_uuid(),
    claimant_name text not null check (char_length(btrim(claimant_name)) between 3 and 200),
    claimant_email text not null check (claimant_email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$' and char_length(claimant_email) <= 254),
    organization text check (organization is null or char_length(organization) <= 200),
    work_description text not null check (char_length(btrim(work_description)) between 10 and 4000),
    content_location text not null check (char_length(btrim(content_location)) between 3 and 2000),
    statement_confirmed boolean not null check (statement_confirmed),
    ip inet,
    status text not null default 'OPEN' check (status in ('OPEN', 'REMOVED', 'REJECTED')),
    resolution_note text,
    resolved_by uuid,
    resolved_at timestamptz,
    created_at timestamptz not null default now()
);

create index copyright_notices_open_idx on public.copyright_notices (created_at) where status = 'OPEN';
create index copyright_notices_ip_idx on public.copyright_notices (ip, created_at desc);

alter table public.copyright_notices enable row level security;
revoke all on table public.copyright_notices from public, anon, authenticated, service_role;

create trigger copyright_notices_audit_staff after update on public.copyright_notices
    for each row execute function public.audit_staff_change();

create function public.submit_copyright_notice(
    p_claimant_name text, p_claimant_email text, p_organization text, p_work_description text,
    p_content_location text, p_statement_confirmed boolean
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_ip inet := public.request_ip();
    v_id uuid;
begin
    if coalesce(p_statement_confirmed, false) is false then
        raise exception using errcode = 'P0001', message = 'statement_required';
    end if;
    if (select count(*) from public.copyright_notices
        where created_at > now() - interval '1 hour' and (ip = v_ip or (v_ip is null and ip is null))) >= 5 then
        raise exception using errcode = 'P0001', message = 'rate_limited';
    end if;
    begin
        insert into public.copyright_notices (claimant_name, claimant_email, organization, work_description,
                                              content_location, statement_confirmed, ip)
        values (btrim(p_claimant_name), lower(btrim(p_claimant_email)), nullif(btrim(p_organization), ''),
                btrim(p_work_description), btrim(p_content_location), true, v_ip)
        returning id into v_id;
    exception when check_violation or not_null_violation then
        raise exception using errcode = 'P0001', message = 'invalid_notice';
    end;
    return v_id;
end;
$$;

create function public.admin_list_copyright_notices()
returns table (id uuid, claimant_name text, claimant_email text, organization text, work_description text,
               content_location text, status text, resolution_note text, created_at timestamptz, resolved_at timestamptz)
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
    if not public.has_staff_role('moderator') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    perform public.write_admin_audit('view.copyright_notices', 'copyright_notices', null,
                                     coalesce(public.request_audit_reason(), 'Panel görüntüleme'));
    return query
    select n.id, n.claimant_name, n.claimant_email, n.organization, n.work_description, n.content_location,
           n.status, n.resolution_note, n.created_at, n.resolved_at
    from public.copyright_notices n
    order by (n.status = 'OPEN') desc, n.created_at
    limit 200;
end;
$$;

-- Records the decision; removing the note itself is done from the note (or through a report).
create function public.admin_resolve_copyright_notice(p_id uuid, p_status text, p_note text, p_note_id uuid default null)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_author uuid;
begin
    if not public.has_staff_role('moderator') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if p_status not in ('REMOVED', 'REJECTED') or char_length(btrim(coalesce(p_note, ''))) < 3 then
        raise exception using errcode = 'P0001', message = 'invalid_action';
    end if;
    update public.copyright_notices
    set status = p_status, resolution_note = btrim(p_note), resolved_by = auth.uid(), resolved_at = now()
    where id = p_id and status = 'OPEN';
    if not found then
        raise exception using errcode = 'P0001', message = 'notice_not_found';
    end if;
    if p_status = 'REMOVED' and p_note_id is not null then
        update public.course_notes set deleted_at = now() where id = p_note_id and deleted_at is null
        returning author_id into v_author;
        if v_author is not null then
            insert into public.notifications (user_id, kind, subject_id) values (v_author, 'CONTENT_REMOVED', p_note_id)
            on conflict (user_id, kind, subject_id) where subject_id is not null do nothing;
        end if;
    end if;
end;
$$;

-- Course notes: the uploader's rights declaration ---------------------------------------------------------------

alter table public.course_notes add column rights_declared_at timestamptz;

create function public.create_course_note(
    p_user_id uuid, p_course_code text, p_course_name text, p_title text, p_description text, p_path text,
    p_size bigint, p_rights_declared boolean
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_id uuid;
begin
    if coalesce(p_rights_declared, false) is false then
        raise exception using errcode = 'P0001', message = 'rights_declaration_required';
    end if;
    v_id := public.create_course_note(p_user_id, p_course_code, p_course_name, p_title, p_description, p_path, p_size);
    update public.course_notes set rights_declared_at = now() where id = v_id;
    return v_id;
end;
$$;

-- Special categories of personal data are not matching criteria -------------------------------------------------

create table public.sensitive_terms (
    term text primary key check (term = lower(term))
);

alter table public.sensitive_terms enable row level security;
revoke all on table public.sensitive_terms from public, anon, authenticated, service_role;

-- Folded (ASCII, lower case) words and phrases for KVKK md.6 categories: religion and sect,
-- ethnicity, political opinion, health, sexual life, union membership, biometrics.
insert into public.sensitive_terms (term) values
    ('din'), ('dini'), ('dindar'), ('dinsiz'), ('mezhep'), ('sunni'), ('alevi'), ('sii'), ('caferi'), ('hristiyan'),
    ('musluman'), ('yahudi'), ('ateist'), ('deist'), ('tesettur'), ('tesetturlu'), ('turbanli'), ('basortulu'),
    ('namaz kilan'), ('oruc tutan'), ('kurt'), ('ermeni'), ('rum'), ('arap'), ('laz'), ('zaza'), ('cerkez'),
    ('suriyeli'), ('irk'), ('etnik'), ('siyasi'), ('akp'), ('akpli'), ('chp'), ('chpli'), ('mhp'), ('mhpli'),
    ('hdp'), ('hdpli'), ('dem parti'), ('iyi parti'), ('ulkucu'), ('solcu'), ('sagci'), ('escinsel'), ('gay'),
    ('lezbiyen'), ('lgbt'), ('lgbti'), ('trans'), ('biseksuel'), ('heteroseksuel'), ('hiv'), ('engelli'),
    ('hastalik'), ('sendika'), ('sendikali'), ('biyometrik');

create function public.has_sensitive_term(p_text text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.sensitive_terms s
        where position(' ' || s.term || ' ' in
                       ' ' || regexp_replace(public.search_fold(coalesce(p_text, '')), '[^a-z0-9]+', ' ', 'g') || ' ') > 0
    )
$$;

-- For warnings in the app (free text is never blocked, only tags are).
create function public.list_sensitive_terms()
returns setof text
language sql
stable
security definer
set search_path = ''
as $$
    select term from public.sensitive_terms order by term
$$;

-- Objections to automatically suggested matches (KVKK md.11/1-g) ------------------------------------------------

create table public.match_objections (
    user_id uuid not null references public.profiles (id) on delete cascade,
    requirement_id uuid not null references public.requirements (id) on delete cascade,
    matched_requirement_id uuid not null references public.requirements (id) on delete cascade,
    reason text check (reason is null or char_length(reason) <= 500),
    created_at timestamptz not null default now(),
    primary key (user_id, requirement_id, matched_requirement_id)
);

create index match_objections_requirement_idx on public.match_objections (requirement_id);
create index match_objections_matched_idx on public.match_objections (matched_requirement_id);

alter table public.match_objections enable row level security;
revoke all on table public.match_objections from public, anon, authenticated, service_role;

create function public.object_to_match(p_requirement_id uuid, p_matched_requirement_id uuid, p_reason text default null)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not exists (select 1 from public.requirements where id = p_requirement_id and owner_id = auth.uid())
        or not exists (select 1 from public.requirements where id = p_matched_requirement_id) then
        raise exception using errcode = 'P0001', message = 'requirement_not_found';
    end if;
    insert into public.match_objections (user_id, requirement_id, matched_requirement_id, reason)
    values (auth.uid(), p_requirement_id, p_matched_requirement_id, nullif(left(btrim(p_reason), 500), ''))
    on conflict do nothing;
end;
$$;

create function public.admin_list_match_objections()
returns table (username text, requirement_title text, matched_title text, reason text, created_at timestamptz)
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    perform public.write_admin_audit('view.match_objections', 'match_objections', null,
                                     coalesce(public.request_audit_reason(), 'Panel görüntüleme'));
    return query
    select p.username, r.title, m.title, o.reason, o.created_at
    from public.match_objections o
    join public.profiles p on p.id = o.user_id
    join public.requirements r on r.id = o.requirement_id
    join public.requirements m on m.id = o.matched_requirement_id
    order by o.created_at desc
    limit 500;
end;
$$;

-- Data export content ---------------------------------------------------------------------------------------------

-- Everything the platform holds about the person, for KVKK md.11 access requests.
create function public.export_user_data(p_user_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select jsonb_build_object(
        'generated_at', now(),
        'account', (select jsonb_build_object(
            'id', u.id, 'email', u.email, 'created_at', u.created_at
        ) from auth.users u where u.id = p_user_id),
        'profile', (select to_jsonb(p) - 'public_name' || jsonb_build_object('university', un.name)
                    from public.profiles p left join public.universities un on un.id = p.university_id
                    where p.id = p_user_id),
        'student_verifications', coalesce((select jsonb_agg(jsonb_build_object(
            'status', v.status, 'submitted_at', v.created_at, 'reviewed_at', v.reviewed_at,
            'rejection_reason', v.rejection_reason, 'document_sha256', v.document_sha256,
            'document_deleted_at', v.document_purged_at) order by v.created_at)
            from public.student_verifications v where v.user_id = p_user_id), '[]'),
        'posts', coalesce((select jsonb_agg(jsonb_build_object(
            'id', p.id, 'created_at', p.created_at, 'scope', p.scope, 'category', p.category, 'body', p.body,
            'deleted_at', p.deleted_at,
            'photos', (select coalesce(jsonb_agg(m.path order by m.position), '[]') from public.post_media m where m.post_id = p.id),
            'event', (select to_jsonb(e) - 'post_id' from public.post_events e where e.post_id = p.id),
            'listing', (select to_jsonb(l) - 'post_id' from public.post_listings l where l.post_id = p.id)
            ) order by p.created_at) from public.posts p where p.author_id = p_user_id), '[]'),
        'comments', coalesce((select jsonb_agg(jsonb_build_object('post_id', c.post_id, 'body', c.body,
            'created_at', c.created_at, 'deleted_at', c.deleted_at) order by c.created_at)
            from public.comments c where c.author_id = p_user_id), '[]'),
        'post_likes', coalesce((select jsonb_agg(jsonb_build_object('post_id', l.post_id, 'created_at', l.created_at))
            from public.post_likes l where l.user_id = p_user_id), '[]'),
        'saved_posts', coalesce((select jsonb_agg(jsonb_build_object('post_id', s.post_id, 'created_at', s.created_at))
            from public.saved_posts s where s.user_id = p_user_id), '[]'),
        'poll_votes', coalesce((select jsonb_agg(jsonb_build_object('poll_id', v.poll_id, 'option', o.label, 'created_at', v.created_at))
            from public.poll_votes v join public.poll_options o on o.id = v.option_id where v.user_id = p_user_id), '[]'),
        'event_attendance', coalesce((select jsonb_agg(jsonb_build_object('post_id', a.post_id, 'created_at', a.created_at))
            from public.event_attendees a where a.user_id = p_user_id), '[]'),
        'requirements', coalesce((select jsonb_agg(jsonb_build_object('title', r.title, 'description', r.description,
            'category', r.category, 'tags', r.tags, 'location', r.location_text, 'starts_at', r.starts_at,
            'participants_needed', r.participants_needed, 'status', r.status, 'created_at', r.created_at) order by r.created_at)
            from public.requirements r where r.owner_id = p_user_id), '[]'),
        'match_objections', coalesce((select jsonb_agg(jsonb_build_object('requirement_id', o.requirement_id,
            'matched_requirement_id', o.matched_requirement_id, 'reason', o.reason, 'created_at', o.created_at))
            from public.match_objections o where o.user_id = p_user_id), '[]'),
        'course_notes', coalesce((select jsonb_agg(jsonb_build_object('course_code', n.course_code, 'course_name', n.course_name,
            'title', n.title, 'description', n.description, 'file', n.file_path, 'rights_declared_at', n.rights_declared_at,
            'created_at', n.created_at, 'deleted_at', n.deleted_at) order by n.created_at)
            from public.course_notes n where n.author_id = p_user_id), '[]'),
        'groups', coalesce((select jsonb_agg(jsonb_build_object('group', g.name, 'kind', g.kind, 'role', m.role, 'joined_at', m.joined_at))
            from public.group_members m join public.groups g on g.id = m.group_id where m.user_id = p_user_id), '[]'),
        'group_messages', coalesce((select jsonb_agg(jsonb_build_object('group', g.name, 'body', m.body, 'photo', m.media_path,
            'created_at', m.created_at, 'deleted_at', m.deleted_at) order by m.created_at)
            from public.group_messages m join public.groups g on g.id = m.group_id where m.sender_id = p_user_id), '[]'),
        'conversations', coalesce((select jsonb_agg(jsonb_build_object(
            'with', (select o.username from public.profiles o where o.id = case when c.user_a = p_user_id then c.user_b else c.user_a end),
            'messages_i_sent', (select coalesce(jsonb_agg(jsonb_build_object('body', m.body, 'created_at', m.created_at) order by m.created_at), '[]')
                                from public.messages m where m.conversation_id = c.id and m.sender_id = p_user_id)))
            from public.conversations c where p_user_id in (c.user_a, c.user_b)), '[]'),
        'notifications', coalesce((select jsonb_agg(jsonb_build_object('kind', n.kind, 'created_at', n.created_at, 'read_at', n.read_at))
            from public.notifications n where n.user_id = p_user_id), '[]'),
        'blocked_users', coalesce((select jsonb_agg(jsonb_build_object('username', p.username, 'created_at', b.created_at))
            from public.user_blocks b join public.profiles p on p.id = b.blocked_id where b.blocker_id = p_user_id), '[]'),
        'reports_made', coalesce((select jsonb_agg(jsonb_build_object('target_kind', r.target_kind, 'reason', r.reason,
            'details', r.details, 'status', r.status, 'created_at', r.created_at))
            from public.reports r where r.reporter_id = p_user_id), '[]'),
        'premium', jsonb_build_object(
            'play_subscription', (select jsonb_build_object('plan', pl.name, 'expires_at', e.expires_at)
                                  from public.entitlements e join public.subscription_plans pl on pl.id = e.plan_id where e.user_id = p_user_id),
            'purchases', coalesce((select jsonb_agg(jsonb_build_object('order_id', x.order_id, 'amount_micros', x.amount_micros,
                'currency', x.currency, 'period_ends_at', x.period_ends_at, 'created_at', x.created_at))
                from public.purchase_events x where x.user_id = p_user_id), '[]'),
            'grants', coalesce((select jsonb_agg(jsonb_build_object('source', g.source, 'expires_at', g.expires_at,
                'created_at', g.created_at, 'revoked_at', g.revoked_at))
                from public.premium_grants g where g.user_id = p_user_id), '[]')),
        'devices', jsonb_build_object('push_tokens', (select count(*) from public.device_tokens t where t.user_id = p_user_id)),
        'activity_days', coalesce((select jsonb_agg(d.day order by d.day) from public.user_activity_days d where d.user_id = p_user_id), '[]'),
        'crash_reports', coalesce((select jsonb_agg(jsonb_build_object('app_version', e.app_version, 'device_model', e.device_model,
            'android_sdk', e.android_sdk, 'exception_type', e.exception_type, 'occurred_at', e.occurred_at))
            from public.client_errors e where e.user_id = p_user_id), '[]'),
        'consents', coalesce((select jsonb_agg(jsonb_build_object('document', c.document_type, 'version', c.document_version,
            'sha256', c.content_sha256, 'action', c.action, 'channel', c.channel, 'ip', c.ip, 'at', c.created_at) order by c.seq)
            from public.consent_logs c where c.user_id = p_user_id), '[]'),
        'access_logs', coalesce((select jsonb_agg(jsonb_build_object('event', a.event, 'ip', a.ip, 'device', a.device_info,
            'platform', a.platform, 'at', a.occurred_at) order by a.occurred_at)
            from public.access_logs a where a.user_id = p_user_id), '[]'),
        'data_subject_requests', coalesce((select jsonb_agg(jsonb_build_object('request_no', r.request_no, 'type', r.type,
            'status', r.status, 'received_at', r.received_at, 'response', r.response_summary))
            from public.data_subject_requests r where r.user_id = p_user_id), '[]')
    )
$$;

-- Patches to existing functions ------------------------------------------------------------------------------------
-- Each replacement must apply exactly; a missing anchor stops the migration.

create function pg_temp.patch(p_signature text, p_old text, p_new text)
returns void
language plpgsql
as $$
declare
    v_def text := pg_get_functiondef(p_signature::regprocedure);
begin
    if position(p_old in v_def) = 0 then
        raise exception 'patch anchor not found in %: %', p_signature, p_old;
    end if;
    execute replace(v_def, p_old, p_new);
end;
$$;

-- Objected matches disappear for the person who objected.
select pg_temp.patch('public.find_matches(uuid, integer)',
    'and not public.is_blocked_with(r.owner_id)',
    'and not public.is_blocked_with(r.owner_id)
      and not exists (select 1 from public.match_objections o
                      where o.user_id = auth.uid() and o.requirement_id = v_source.id and o.matched_requirement_id = r.id)');

-- Tags may not name special categories of personal data.
select pg_temp.patch('public.create_requirement(text, text, public.requirement_category, text[], text, timestamptz, integer)',
    '    -- Serialise one person''s writes so the limits cannot be raced.',
    '    if exists (select 1 from unnest(v_tags) t where public.has_sensitive_term(t)) then
        raise exception using errcode = ''P0001'', message = ''sensitive_tag'';
    end if;

    -- Serialise one person''s writes so the limits cannot be raced.');

-- Messages of deleted accounts stay as "deleted user" (sender null).
select pg_temp.patch('public.list_group_messages(uuid, timestamptz, uuid, integer)',
    'join public.profiles s on s.id = m.sender_id', 'left join public.profiles s on s.id = m.sender_id');

do $$
declare
    f record;
begin
    for f in
        select p.oid::regprocedure::text as sig from pg_proc p
        where p.pronamespace = 'public'::regnamespace and p.prosrc like '%x.sender_id <> auth.uid()%'
    loop
        perform pg_temp.patch(f.sig, 'x.sender_id <> auth.uid()', 'x.sender_id is distinct from auth.uid()');
    end loop;
end;
$$;

-- Other students see public_name ("Ayşe Y.") unless the person shows the full name. Staff
-- screens and the person's own profile keep the full name.
do $$
declare
    f record;
    v_def text;
begin
    for f in
        select p.oid, p.proname from pg_proc p
        where p.pronamespace = 'public'::regnamespace
          and p.proname in ('discover_groups', 'find_matches', 'get_group', 'get_user_profile', 'list_blocked_users',
                            'list_comments', 'list_conversations', 'list_course_notes', 'list_group_members',
                            'list_group_messages', 'list_notifications', 'post_details', 'push_payload', 'search_people',
                            'list_posts')
    loop
        v_def := pg_get_functiondef(f.oid);
        if v_def !~ '\m[a-z_][a-z0-9_]*\.full_name\M' then
            continue;
        end if;
        execute regexp_replace(v_def, '\m([a-z_][a-z0-9_]*)\.full_name\M', '\1.public_name', 'g');
    end loop;
end;
$$;

-- Grants ------------------------------------------------------------------------------------------------------------

do $$
declare
    f text;
begin
    foreach f in array array[
        'public.next_request_no()', 'public.last_activity_at(uuid)',
        'public.clear_inactive_notice()', 'public.notify_moderation_decision()', 'public.retention_purge_sql()',
        'public.run_daily_retention()', 'public.has_sensitive_term(text)'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated, service_role', f);
    end loop;

    foreach f in array array[
        'public.submit_data_subject_request(text, text)', 'public.my_data_subject_requests()',
        'public.admin_record_data_subject_request(text, text, text, text, uuid, timestamptz)',
        'public.admin_list_data_subject_requests(text)', 'public.admin_update_data_subject_request(uuid, text, text)',
        'public.admin_save_breach(uuid, timestamptz, text, text[], integer, text, timestamptz, timestamptz, boolean)',
        'public.admin_list_breaches()', 'public.request_account_deletion()', 'public.cancel_account_deletion()',
        'public.my_account_deletion()', 'public.set_show_full_name(boolean)', 'public.admin_retention_report(integer)',
        'public.my_moderation_decisions()', 'public.submit_moderation_appeal(uuid, text)', 'public.admin_list_appeals()',
        'public.admin_decide_appeal(uuid, boolean, text)', 'public.admin_report_context(uuid)',
        'public.admin_list_copyright_notices()', 'public.admin_resolve_copyright_notice(uuid, text, text, uuid)',
        'public.list_sensitive_terms()', 'public.object_to_match(uuid, uuid, text)', 'public.admin_list_match_objections()'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;

    foreach f in array array[
        'public.export_user_data(uuid)', 'public.record_data_export(uuid, text, text)', 'public.can_export_data(uuid)',
        'public.set_student_document_sha256(uuid, text, text)', 'public.retention_due()',
        'public.record_document_purged(uuid)', 'public.record_export_deleted(uuid)', 'public.record_inactive_notice(uuid)',
        'public.prepare_account_deletion(uuid, text)', 'public.record_retention_run(timestamptz, jsonb, text)',
        'public.create_course_note(uuid, text, text, text, text, text, bigint, boolean)'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated', f);
        execute format('grant execute on function %s to service_role', f);
    end loop;

    -- Uploads go through the declaring variant only.
    execute 'revoke all on function public.create_course_note(uuid, text, text, text, text, text, bigint) from public, anon, authenticated, service_role';

    execute 'revoke all on function public.submit_copyright_notice(text, text, text, text, text, boolean) from public';
    execute 'grant execute on function public.submit_copyright_notice(text, text, text, text, text, boolean) to anon, authenticated';
end;
$$;
