-- KVKK / 5651 compliance foundation (Phase 20, D57).
--
--   * compliance_settings: every legal duration and limit in one table, editable
--     from the admin panel (every change is audited). Code never hard-codes them.
--   * Staff roles from JWT app_metadata: superadmin (legacy role = 'admin'),
--     verifier (documents), moderator (content), compliance (KVKK records).
--     Staff RPCs also require an MFA (aal2) session while admin_mfa_required.
--   * Append-only logs (consent_logs, access_logs, admin_audit_logs,
--     deletion_logs): no API role may insert, update or delete; rows are written
--     by SECURITY DEFINER functions only, updates are refused by a trigger and
--     deletes are allowed only to the retention purge. Each row carries
--     prev_hash + hash (SHA-256 chain) so later tampering is detectable.
--   * Every staff change to audited tables needs a reason, sent by the panel in
--     the x-audit-reason header (base64 UTF-8); the database refuses the change
--     without it and writes admin_audit_logs itself.
--   * legal_documents: versioned legal texts; old versions are never deleted so
--     every consent row points to the exact text (version + SHA-256) it was given to.
--   * Consents at sign-up are read from the sign-up metadata by auth triggers;
--     later acknowledgements and consent changes go through RPCs that record
--     the request IP and user agent from the API gateway headers.

-- Settings -------------------------------------------------------------------------------------

create table public.compliance_settings (
    key text primary key check (key ~ '^[a-z0-9_]{2,60}$'),
    value jsonb not null,
    is_public boolean not null default true,
    description text not null,
    updated_at timestamptz not null default now(),
    updated_by uuid
);

alter table public.compliance_settings enable row level security;
revoke all on table public.compliance_settings from public, anon, authenticated, service_role;

insert into public.compliance_settings (key, value, is_public, description) values
    ('min_age', '18', true, 'Kayıt için en küçük yaş.'),
    ('document_retention_days', '30', true, 'Öğrenci belgesinin onay/red kararından sonra Storage''da kalacağı en uzun gün sayısı.'),
    ('deletion_grace_days', '30', true, 'Hesap silme talebinden sonra geri alma süresi (gün).'),
    ('access_log_retention_days', '730', true, '5651 trafik kayıtlarının (access_logs) saklama süresi (gün).'),
    ('consent_log_retention_days', '3650', true, 'Rıza/aydınlatma kayıtlarının saklama süresi (gün).'),
    ('admin_audit_retention_days', '3650', true, 'Yönetici işlem kayıtlarının saklama süresi (gün).'),
    ('deletion_log_retention_days', '3650', true, 'İmha kayıtlarının saklama süresi (gün).'),
    ('client_error_retention_days', '180', true, 'Çökme raporlarının saklama süresi (gün).'),
    ('data_export_retention_days', '7', true, 'Hazırlanan veri dışa aktarma dosyasının saklama süresi (gün).'),
    ('data_export_link_minutes', '60', true, 'Veri dışa aktarma indirme bağlantısının geçerlilik süresi (dakika).'),
    ('dsr_response_days', '30', true, 'Veri sahibi başvurusunun yanıtlanma süresi (gün, KVKK md.13).'),
    ('dsr_warning_days', '7', true, 'Başvuru süresinin dolmasına bu kadar gün kala panelde uyarı.'),
    ('breach_notify_hours', '72', true, 'Veri ihlalinin Kurul''a bildirim süresi (saat).'),
    ('urgent_report_hours', '24', true, 'Kişilik hakkı / kişisel veri ifşası şikâyetleri için hedef süre (saat).'),
    ('inactive_account_days', '730', true, 'Bu kadar gün giriş yapmayan hesaba silme uyarısı gönderilir.'),
    ('inactive_notice_days', '30', true, 'Hareketsizlik uyarısından sonra hesabın silinmesine kadar geçen gün.'),
    ('destruction_interval_days', '180', true, 'Periyodik imha aralığının üst sınırı (gün). Otomatik imha günlük çalışır.'),
    ('admin_idle_minutes', '30', true, 'Yönetim paneli oturumunun hareketsizlikte kapanma süresi (dakika).'),
    ('admin_mfa_required', 'true', true, 'Yönetici işlemleri için iki adımlı doğrulama (aal2) zorunlu.'),
    ('report_context_messages', '5', true, 'Şikâyet edilen mesajın öncesinden ve sonrasından moderatöre gösterilen mesaj sayısı.'),
    ('register_log_notice', '"Hesabının güvenliği ve yasal yükümlülüklerimiz (5651 sayılı Kanun) gereği giriş-çıkış, IP adresi, cihaz bilgisi ve işlem kayıtların (log) tutulmaktadır. Bu kayıtlar yalnızca yetkili kişilerce, güvenlik ve yasal talepler için kullanılır. Ayrıntılar: Aydınlatma Metni."', true, 'Kayıt ekranındaki log bilgilendirmesi.'),
    ('login_log_notice', '"Giriş yaparak güvenlik amacıyla işlem kayıtlarının (log) tutulduğunu kabul edersin."', true, 'Giriş butonunun altındaki not.'),
    ('document_upload_notice', '"Öğrenci belgen yalnızca öğrenciliğini doğrulamak için alınır ve yalnızca yetkili doğrulama personeli tarafından görülür. Belge, onay veya red kararından sonra en geç {document_retention_days} gün içinde kalıcı olarak silinir; yalnızca doğrulandığın bilgisi, üniversiten ve bölümün saklanır."', true, 'Belge yükleme ekranındaki bilgilendirme ({anahtar} yer tutucuları ayardan doldurulur).');

create function public.compliance_value(p_key text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v jsonb;
begin
    select value into v from public.compliance_settings where key = p_key;
    if not found then
        raise exception using errcode = 'P0001', message = 'compliance_setting_missing';
    end if;
    return v;
end;
$$;

create function public.compliance_int(p_key text)
returns integer
language sql
stable
set search_path = ''
as $$
    select (public.compliance_value(p_key))::text::integer
$$;

-- Public, non-secret settings for the app and the website.
create function public.public_compliance_config()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(jsonb_object_agg(key, value), '{}') from public.compliance_settings where is_public
$$;

-- Request context (PostgREST copies the HTTP request headers into request.headers) -----------

create function public.request_header(p_name text)
returns text
language plpgsql
stable
set search_path = ''
as $$
begin
    return nullif(current_setting('request.headers', true), '')::json ->> p_name;
exception
    when others then
        return null;
end;
$$;

-- Cloudflare (in front of Supabase) sets cf-connecting-ip; x-forwarded-for is the fallback.
create function public.request_ip()
returns inet
language plpgsql
stable
set search_path = ''
as $$
declare
    v text := coalesce(
        public.request_header('cf-connecting-ip'),
        btrim(split_part(public.request_header('x-forwarded-for'), ',', 1)),
        public.request_header('x-real-ip')
    );
begin
    return nullif(btrim(v), '')::inet;
exception
    when others then
        return null;
end;
$$;

create function public.request_user_agent()
returns text
language sql
stable
set search_path = ''
as $$
    select left(public.request_header('user-agent'), 500)
$$;

-- The panel sends the reason of a staff action base64 encoded (HTTP headers are ASCII only).
create function public.request_audit_reason()
returns text
language plpgsql
stable
set search_path = ''
as $$
declare
    v text := public.request_header('x-audit-reason');
begin
    if v is null then
        return null;
    end if;
    return nullif(btrim(convert_from(decode(v, 'base64'), 'UTF8')), '');
exception
    when others then
        return null;
end;
$$;

-- Staff roles ----------------------------------------------------------------------------------

create function public.staff_roles()
returns text[]
language sql
stable
set search_path = ''
as $$
    select coalesce(array_agg(distinct r order by r), '{}')
    from (
        select case when (auth.jwt() -> 'app_metadata' ->> 'role') = 'admin' then 'superadmin' end as r
        union all
        select jsonb_array_elements_text(
            case when jsonb_typeof(auth.jwt() -> 'app_metadata' -> 'roles') = 'array'
                 then auth.jwt() -> 'app_metadata' -> 'roles' else '[]'::jsonb end
        )
    ) roles
    where r in ('superadmin', 'verifier', 'moderator', 'compliance')
$$;

create function public.staff_mfa_satisfied()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select not coalesce((public.compliance_value('admin_mfa_required'))::text::boolean, true)
        or coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2'
$$;

-- True when the caller holds one of the roles (superadmin holds all) in an MFA session.
create function public.has_staff_role(variadic p_roles text[])
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select cardinality(public.staff_roles()) > 0
       and ('superadmin' = any (public.staff_roles()) or public.staff_roles() && p_roles)
       and public.staff_mfa_satisfied()
$$;

-- What the panel needs to decide which sections to show and whether to ask for MFA.
create function public.my_staff_access()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select jsonb_build_object(
        'roles', to_jsonb(public.staff_roles()),
        'mfa_required', coalesce((public.compliance_value('admin_mfa_required'))::text::boolean, true),
        'mfa_satisfied', public.staff_mfa_satisfied(),
        'idle_minutes', public.compliance_int('admin_idle_minutes')
    )
$$;

-- Kept for compatibility: superadmin.
create or replace function public.is_admin()
returns boolean
language sql
stable
set search_path = ''
as $$
    select public.has_staff_role('superadmin')
$$;

-- Hash-chained, append-only logs ---------------------------------------------------------------

create table public.log_chain_heads (
    table_name text primary key,
    last_seq bigint not null,
    last_hash text not null
);

alter table public.log_chain_heads enable row level security;
revoke all on table public.log_chain_heads from public, anon, authenticated, service_role;

create function public.log_row_hash(p_prev text, p_row jsonb)
returns text
language sql
immutable
set search_path = ''
as $$
    select encode(sha256(convert_to(p_prev || '|' || (p_row - 'hash' - 'prev_hash')::text, 'UTF8')), 'hex')
$$;

-- UTC so the timestamp text inside the hash never depends on the session time zone.
create function public.log_chain_before_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
set timezone to 'UTC'
as $$
declare
    v_prev text;
begin
    insert into public.log_chain_heads (table_name, last_seq, last_hash)
    values (tg_table_name, 0, repeat('0', 64))
    on conflict (table_name) do nothing;
    select last_hash into v_prev from public.log_chain_heads where table_name = tg_table_name for update;

    new.created_at := clock_timestamp();
    new.prev_hash := v_prev;
    new.hash := public.log_row_hash(v_prev, to_jsonb(new));

    update public.log_chain_heads set last_seq = new.seq, last_hash = new.hash where table_name = tg_table_name;
    return new;
end;
$$;

create function public.log_append_only()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if tg_op = 'DELETE' and current_setting('kampusagi.log_purge', true) = 'on' then
        return old;
    end if;
    raise exception using errcode = 'P0001', message = 'log_is_append_only';
end;
$$;

create table public.consent_logs (
    seq bigint generated always as identity primary key,
    user_id uuid not null,
    document_type text not null,
    document_version integer,
    content_sha256 text,
    action text not null check (action in ('accepted', 'declined', 'withdrawn', 'informed')),
    channel text not null check (channel in ('register', 'login', 'settings', 'email_link', 'purchase', 'system')),
    ip inet,
    user_agent text,
    app_version text,
    platform text,
    created_at timestamptz not null default now(),
    prev_hash text not null default '',
    hash text not null default ''
);

create index consent_logs_user_idx on public.consent_logs (user_id, document_type, seq desc);
create index consent_logs_created_idx on public.consent_logs (created_at);

create table public.access_logs (
    seq bigint generated always as identity primary key,
    user_id uuid,
    event text not null check (event in (
        'signup', 'login', 'logout', 'login_failed', 'password_recovery', 'account_deleted'
    )),
    source text not null check (source in ('auth_server', 'app')),
    email_sha256 text,
    ip inet,
    port integer,
    device_info text,
    app_version text,
    platform text,
    auth_event_id uuid unique,
    occurred_at timestamptz not null,
    created_at timestamptz not null default now(),
    prev_hash text not null default '',
    hash text not null default ''
);

create index access_logs_user_idx on public.access_logs (user_id, occurred_at desc);
create index access_logs_occurred_idx on public.access_logs (occurred_at);
create index access_logs_ip_idx on public.access_logs (ip, occurred_at desc);

create table public.admin_audit_logs (
    seq bigint generated always as identity primary key,
    admin_id uuid,
    admin_roles text[] not null default '{}',
    action text not null check (char_length(action) between 3 and 100),
    target_type text not null,
    target_id text,
    reason text not null check (char_length(btrim(reason)) between 3 and 1000),
    ip inet,
    user_agent text,
    details jsonb not null default '{}',
    created_at timestamptz not null default now(),
    prev_hash text not null default '',
    hash text not null default ''
);

create index admin_audit_logs_admin_idx on public.admin_audit_logs (admin_id, created_at desc);
create index admin_audit_logs_created_idx on public.admin_audit_logs (created_at);
create index admin_audit_logs_target_idx on public.admin_audit_logs (target_type, target_id);

create table public.deletion_logs (
    seq bigint generated always as identity primary key,
    subject_user_id uuid,
    data_category text not null,
    reason text not null check (reason in (
        'user_request', 'retention_expired', 'document_reviewed', 'inactive_account', 'admin_removal', 'export_expired'
    )),
    method text not null check (method in ('deleted', 'anonymized')),
    item_count integer not null default 1 check (item_count >= 0),
    details jsonb not null default '{}',
    created_at timestamptz not null default now(),
    prev_hash text not null default '',
    hash text not null default ''
);

create index deletion_logs_created_idx on public.deletion_logs (created_at);
create index deletion_logs_subject_idx on public.deletion_logs (subject_user_id);

do $$
declare
    t text;
begin
    foreach t in array array['consent_logs', 'access_logs', 'admin_audit_logs', 'deletion_logs'] loop
        execute format('alter table public.%I enable row level security', t);
        execute format('revoke all on table public.%I from public, anon, authenticated, service_role', t);
        execute format('create trigger %I before insert on public.%I for each row execute function public.log_chain_before_insert()', t || '_chain', t);
        execute format('create trigger %I before update or delete on public.%I for each row execute function public.log_append_only()', t || '_append_only', t);
        execute format('create trigger %I before truncate on public.%I for each statement execute function public.log_append_only()', t || '_no_truncate', t);
    end loop;
end;
$$;

-- Recomputes a chain. The first remaining row's prev_hash is the anchor left by the last
-- retention purge (recorded in deletion_logs.details.anchor_hash).
create function public.verify_log_chain(p_table text)
returns table (table_name text, rows_checked bigint, ok boolean, first_bad_seq bigint, anchor_hash text, head_matches boolean)
language plpgsql
security definer
set search_path = ''
set timezone to 'UTC'
as $$
declare
    r record;
    v_expected text;
    v_count bigint := 0;
    v_bad bigint;
    v_anchor text;
    v_last_seq bigint;
    v_last_hash text;
begin
    if p_table not in ('consent_logs', 'access_logs', 'admin_audit_logs', 'deletion_logs') then
        raise exception using errcode = 'P0001', message = 'unknown_log';
    end if;
    for r in execute format('select seq, prev_hash, hash, to_jsonb(t) as j from public.%I t order by seq', p_table) loop
        if v_count = 0 then
            v_anchor := r.prev_hash;
            v_expected := r.prev_hash;
        end if;
        v_count := v_count + 1;
        if v_bad is null and (r.prev_hash <> v_expected or r.hash <> public.log_row_hash(r.prev_hash, r.j)) then
            v_bad := r.seq;
        end if;
        v_expected := r.hash;
        v_last_seq := r.seq;
        v_last_hash := r.hash;
    end loop;

    return query
    select p_table, v_count, v_bad is null, v_bad, v_anchor,
           coalesce((select h.last_hash = v_last_hash and h.last_seq = v_last_seq
                     from public.log_chain_heads h where h.table_name = p_table), v_count = 0);
end;
$$;

-- Deletes log rows older than p_before (retention only) and records the anchor.
create function public.purge_log(p_table text, p_before timestamptz)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_count integer;
    v_anchor text;
begin
    if p_table not in ('consent_logs', 'access_logs', 'admin_audit_logs', 'deletion_logs') then
        raise exception using errcode = 'P0001', message = 'unknown_log';
    end if;
    execute format('select hash from public.%I where created_at < $1 order by seq desc limit 1', p_table)
        into v_anchor using p_before;
    if v_anchor is null then
        return 0;
    end if;
    perform set_config('kampusagi.log_purge', 'on', true);
    execute format('delete from public.%I where created_at < $1', p_table) using p_before;
    get diagnostics v_count = row_count;
    perform set_config('kampusagi.log_purge', 'off', true);

    insert into public.deletion_logs (data_category, reason, method, item_count, details)
    values (p_table, 'retention_expired', 'deleted', v_count,
            jsonb_build_object('before', p_before, 'anchor_hash', v_anchor));
    return v_count;
end;
$$;

-- Audit ------------------------------------------------------------------------------------------

create function public.write_admin_audit(
    p_action text,
    p_target_type text,
    p_target_id text,
    p_reason text,
    p_details jsonb default '{}',
    p_admin_id uuid default null,
    p_admin_roles text[] default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_reason is null or char_length(btrim(p_reason)) < 3 then
        raise exception using errcode = 'P0001', message = 'audit_reason_required';
    end if;
    insert into public.admin_audit_logs (admin_id, admin_roles, action, target_type, target_id, reason, ip, user_agent, details)
    values (coalesce(p_admin_id, auth.uid()), coalesce(p_admin_roles, public.staff_roles()), p_action, p_target_type,
            p_target_id, left(btrim(p_reason), 1000), public.request_ip(), public.request_user_agent(),
            coalesce(p_details, '{}'));
end;
$$;

-- Staff actions on audited tables: the change and its reason are recorded together.
-- Changes a person makes to their own rows (e.g. own profile) are not staff actions.
create function public.audit_staff_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_row jsonb := case when tg_op = 'DELETE' then to_jsonb(old) else to_jsonb(new) end;
    v_target text := coalesce(v_row ->> 'id', v_row ->> 'key', v_row ->> 'user_id');
begin
    if auth.uid() is null or cardinality(public.staff_roles()) = 0 then
        return null;
    end if;
    if tg_table_name = 'profiles' and v_target = auth.uid()::text then
        return null;
    end if;
    perform public.write_admin_audit(
        tg_table_name || '.' || lower(tg_op),
        tg_table_name,
        v_target,
        public.request_audit_reason(),
        case when tg_op = 'UPDATE'
             then jsonb_build_object('changed', (
                 select coalesce(jsonb_object_agg(n.key, n.value), '{}')
                 from jsonb_each(to_jsonb(new)) n
                 where n.value is distinct from (to_jsonb(old) -> n.key)
                   and n.key not in ('updated_at', 'content', 'body')))
             else '{}'::jsonb end
    );
    return null;
end;
$$;

create trigger profiles_audit_staff after update of account_status on public.profiles
    for each row when (old.account_status is distinct from new.account_status)
    execute function public.audit_staff_change();
create trigger student_verifications_audit_staff after update on public.student_verifications
    for each row execute function public.audit_staff_change();
create trigger reports_audit_staff after update on public.reports
    for each row execute function public.audit_staff_change();
-- Promo redemptions are made by students themselves (possibly a staff member as a student).
create trigger premium_grants_audit_staff after insert or update on public.premium_grants
    for each row when (new.source = 'ADMIN') execute function public.audit_staff_change();
create trigger promo_codes_audit_staff_insert after insert on public.promo_codes
    for each row execute function public.audit_staff_change();
create trigger promo_codes_audit_staff_update after update of disabled_at on public.promo_codes
    for each row execute function public.audit_staff_change();
create trigger announcements_audit_staff_insert after insert on public.announcements
    for each row execute function public.audit_staff_change();
create trigger announcements_audit_staff_update after update of ends_at on public.announcements
    for each row execute function public.audit_staff_change();
create trigger compliance_settings_audit_staff after update on public.compliance_settings
    for each row execute function public.audit_staff_change();

-- Panel actions that change nothing in the database (CSV/JSON export, opening a record).
create function public.record_admin_action(p_action text, p_target_type text, p_target_id text, p_details jsonb default '{}')
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.has_staff_role('verifier', 'moderator', 'compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if p_action !~ '^[a-z_.]{3,60}$' then
        raise exception using errcode = 'P0001', message = 'invalid_action';
    end if;
    perform public.write_admin_audit(p_action, coalesce(p_target_type, 'panel'), p_target_id,
                                     public.request_audit_reason(), p_details);
end;
$$;

-- Edge Functions acting for a staff member (service role) record what they did.
create function public.service_record_admin_action(
    p_admin_id uuid, p_admin_roles text[], p_action text, p_target_type text, p_target_id text,
    p_reason text, p_details jsonb default '{}'
)
returns void
language sql
security definer
set search_path = ''
as $$
    select public.write_admin_audit(p_action, p_target_type, p_target_id, p_reason, p_details, p_admin_id, p_admin_roles)
$$;

-- Edge Functions check staff access with the caller's own JWT (roles + MFA).
create function public.require_staff_role(variadic p_roles text[])
returns text[]
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.has_staff_role(variadic p_roles) then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    return public.staff_roles();
end;
$$;

-- Settings changes from the panel (superadmin / compliance); audited by the trigger above.
create function public.admin_set_compliance_setting(p_key text, p_value jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_old jsonb;
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    select value into v_old from public.compliance_settings where key = p_key for update;
    if not found then
        raise exception using errcode = 'P0001', message = 'compliance_setting_missing';
    end if;
    if p_value is null or jsonb_typeof(p_value) <> jsonb_typeof(v_old)
        or (jsonb_typeof(p_value) = 'number' and ((p_value)::text::numeric < 0 or (p_value)::text::numeric <> trunc((p_value)::text::numeric)))
        or (jsonb_typeof(p_value) = 'string' and char_length(p_value #>> '{}') not between 1 and 2000) then
        raise exception using errcode = 'P0001', message = 'invalid_setting_value';
    end if;
    update public.compliance_settings set value = p_value, updated_at = now(), updated_by = auth.uid() where key = p_key;
end;
$$;

create function public.admin_list_compliance_settings()
returns table (key text, value jsonb, is_public boolean, description text, updated_at timestamptz, updated_by uuid)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.has_staff_role('verifier', 'moderator', 'compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    return query select s.key, s.value, s.is_public, s.description, s.updated_at, s.updated_by
                 from public.compliance_settings s order by s.key;
end;
$$;

-- Existing staff functions: each checks its own role now; reads of personal data are logged.
create function public.staff_gate(p_roles text[], p_function text, p_log_view boolean)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.has_staff_role(variadic p_roles) then
        return false;
    end if;
    if p_log_view then
        perform public.write_admin_audit('view.' || p_function, 'panel', null,
                                         coalesce(public.request_audit_reason(), 'Panel görüntüleme'));
    end if;
    return true;
end;
$$;

do $$
declare
    f record;
    v_roles text;
    v_view boolean;
    v_def text;
begin
    for f in
        select p.oid, p.proname, p.provolatile
        from pg_proc p
        where p.pronamespace = 'public'::regnamespace
          and p.prosrc like '%public.is_admin()%'
          and p.proname not in ('is_admin')
    loop
        v_view := f.proname in ('admin_list_users', 'list_pending_verifications', 'list_open_reports',
                                'admin_list_grants', 'admin_list_purchases');
        v_roles := case
            when f.proname in ('list_pending_verifications', 'review_student_verification') then 'verifier'
            when f.proname in ('list_open_reports', 'resolve_report', 'admin_reinstate_user', 'admin_list_users') then 'moderator'
            when f.proname in ('admin_overview') then 'verifier'',''moderator'',''compliance'
            when f.proname in (
                'admin_activity_stats', 'admin_broadcast_audience_size', 'admin_create_announcement',
                'admin_create_promo_code', 'admin_disable_promo_code', 'admin_end_announcement', 'admin_grant_premium',
                'admin_list_announcements', 'admin_list_broadcasts', 'admin_list_grants', 'admin_list_plans',
                'admin_list_promo_codes', 'admin_list_purchases', 'admin_list_universities', 'admin_revoke_grant',
                'admin_university_stats') then 'superadmin'
        end;
        if v_roles is null then
            raise exception 'staff role mapping missing for %', f.proname;
        end if;
        v_def := replace(pg_get_functiondef(f.oid), 'public.is_admin()',
                         format('public.staff_gate(array[''%s''], %L, %s)', v_roles, f.proname, case when v_view then 'true' else 'false' end));
        if v_view and f.provolatile <> 'v' then
            v_def := regexp_replace(v_def, '\m(STABLE|IMMUTABLE)\M', 'VOLATILE');
        end if;
        execute v_def;
    end loop;
end;
$$;

-- Documents are opened only through the audited admin-document Edge Function.
drop policy student_documents_select_own_or_admin on storage.objects;
create policy student_documents_select_own on storage.objects
    for select to authenticated
    using (bucket_id = 'student-documents' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- Legal documents ---------------------------------------------------------------------------------

create table public.legal_document_types (
    doc_type text primary key check (doc_type ~ '^[a-z_]{3,60}$'),
    kind text not null check (kind in ('notice', 'agreement', 'consent', 'purchase')),
    -- agreement: must be accepted to use the service; notice with notify_on_change: shown again
    -- after a change; consent: optional; purchase: accepted before buying Premium.
    notify_on_change boolean not null default false,
    sort_order integer not null default 0
);

alter table public.legal_document_types enable row level security;
revoke all on table public.legal_document_types from public, anon, authenticated, service_role;

insert into public.legal_document_types (doc_type, kind, notify_on_change, sort_order) values
    ('aydinlatma_metni', 'notice', true, 10),
    ('gizlilik_politikasi', 'notice', true, 20),
    ('kullanim_kosullari', 'agreement', false, 30),
    ('topluluk_kurallari', 'agreement', false, 40),
    ('acik_riza_pazarlama_eposta', 'consent', false, 50),
    ('acik_riza_pazarlama_bildirim', 'consent', false, 60),
    ('cerez_sdk_politikasi', 'notice', false, 70),
    ('telif_politikasi', 'notice', false, 80),
    ('premium_on_bilgilendirme', 'purchase', false, 90),
    ('mesafeli_sozlesme', 'purchase', false, 100),
    ('saklama_imha_politikasi', 'notice', false, 110),
    ('basvuru_formu', 'notice', false, 120),
    ('cocuk_guvenligi', 'notice', false, 130);

create table public.legal_documents (
    id uuid primary key default gen_random_uuid(),
    doc_type text not null references public.legal_document_types (doc_type),
    version integer not null check (version >= 1),
    title text not null check (char_length(btrim(title)) between 3 and 200),
    content text not null check (char_length(content) between 50 and 200000),
    content_sha256 text not null default '',
    is_active boolean not null default true,
    published_at timestamptz not null default now(),
    published_by uuid,
    unique (doc_type, version)
);

create unique index legal_documents_one_active_idx on public.legal_documents (doc_type) where is_active;

alter table public.legal_documents enable row level security;
revoke all on table public.legal_documents from public, anon, authenticated, service_role;

create function public.legal_documents_guard()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if tg_op = 'INSERT' then
        new.content_sha256 := encode(sha256(convert_to(new.content, 'UTF8')), 'hex');
        new.published_at := now();
        return new;
    end if;
    -- Published texts are evidence: only deactivation is allowed, never a change or a delete.
    if tg_op = 'UPDATE' and old.is_active and not new.is_active
        and (to_jsonb(new) - 'is_active') = (to_jsonb(old) - 'is_active') then
        return new;
    end if;
    raise exception using errcode = 'P0001', message = 'legal_document_immutable';
end;
$$;

create trigger legal_documents_guard before insert or update or delete on public.legal_documents
    for each row execute function public.legal_documents_guard();
create trigger legal_documents_audit_staff after insert on public.legal_documents
    for each row execute function public.audit_staff_change();

create function public.list_legal_documents()
returns table (doc_type text, kind text, notify_on_change boolean, version integer, title text,
               content_sha256 text, published_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
    select d.doc_type, t.kind, t.notify_on_change, d.version, d.title, d.content_sha256, d.published_at
    from public.legal_documents d
    join public.legal_document_types t on t.doc_type = d.doc_type
    where d.is_active
    order by t.sort_order
$$;

-- The active version, or an older one by number (old versions stay readable).
create function public.get_legal_document(p_doc_type text, p_version integer default null)
returns table (doc_type text, kind text, version integer, title text, content text, content_sha256 text,
               published_at timestamptz, is_active boolean)
language sql
stable
security definer
set search_path = ''
as $$
    select d.doc_type, t.kind, d.version, d.title, d.content, d.content_sha256, d.published_at, d.is_active
    from public.legal_documents d
    join public.legal_document_types t on t.doc_type = d.doc_type
    where d.doc_type = p_doc_type
      and (case when p_version is null then d.is_active else d.version = p_version end)
$$;

create function public.list_legal_document_versions(p_doc_type text)
returns table (version integer, title text, content_sha256 text, published_at timestamptz, is_active boolean)
language sql
stable
security definer
set search_path = ''
as $$
    select d.version, d.title, d.content_sha256, d.published_at, d.is_active
    from public.legal_documents d
    where d.doc_type = p_doc_type
    order by d.version desc
$$;

-- Publishes a new version (compliance); the previous version stays as evidence.
create function public.admin_publish_legal_document(p_doc_type text, p_title text, p_content text)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_version integer;
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if not exists (select 1 from public.legal_document_types where doc_type = p_doc_type) then
        raise exception using errcode = 'P0001', message = 'legal_document_not_found';
    end if;
    if p_content is null or char_length(p_content) not between 50 and 200000
        or p_title is null or char_length(btrim(p_title)) not between 3 and 200 then
        raise exception using errcode = 'P0001', message = 'invalid_legal_document';
    end if;
    perform pg_advisory_xact_lock(hashtext('legal_documents:' || p_doc_type));
    select coalesce(max(version), 0) + 1 into v_version from public.legal_documents where doc_type = p_doc_type;
    update public.legal_documents set is_active = false where doc_type = p_doc_type and is_active;
    insert into public.legal_documents (doc_type, version, title, content, published_by)
    values (p_doc_type, v_version, btrim(p_title), p_content, auth.uid());
    return v_version;
end;
$$;

-- Acceptance rates for the panel.
create function public.admin_legal_document_stats()
returns table (doc_type text, kind text, version integer, published_at timestamptz, users bigint, acknowledged bigint)
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
    select d.doc_type, t.kind, d.version, d.published_at,
           (select count(*) from public.profiles),
           (select count(distinct c.user_id) from public.consent_logs c
            join public.profiles p on p.id = c.user_id
            where c.document_type = d.doc_type and c.document_version = d.version
              and c.action in ('accepted', 'informed'))
    from public.legal_documents d
    join public.legal_document_types t on t.doc_type = d.doc_type
    where d.is_active
    order by t.sort_order;
end;
$$;

-- Consents ------------------------------------------------------------------------------------------

alter table public.profiles
    add column marketing_push_opt_in boolean not null default false;

create function public.insert_consent_log(
    p_user_id uuid, p_doc_type text, p_version integer, p_action text, p_channel text,
    p_ip inet, p_user_agent text, p_app_version text, p_platform text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_sha text;
begin
    select content_sha256 into v_sha from public.legal_documents where doc_type = p_doc_type and version = p_version;
    if not found then
        raise exception using errcode = 'P0001', message = 'legal_document_not_found';
    end if;
    insert into public.consent_logs (user_id, document_type, document_version, content_sha256, action, channel,
                                     ip, user_agent, app_version, platform)
    values (p_user_id, p_doc_type, p_version, v_sha, p_action, p_channel, p_ip,
            left(p_user_agent, 500), left(p_app_version, 40), left(p_platform, 20));
end;
$$;

-- Applies the profile flag that a consent controls.
create function public.apply_consent_flag(p_user_id uuid, p_doc_type text, p_granted boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_doc_type = 'acik_riza_pazarlama_eposta' then
        update public.profiles
        set marketing_opt_in = p_granted,
            marketing_opt_in_at = case when p_granted then coalesce(marketing_opt_in_at, now()) end
        where id = p_user_id;
    elsif p_doc_type = 'acik_riza_pazarlama_bildirim' then
        update public.profiles set marketing_push_opt_in = p_granted where id = p_user_id;
    end if;
end;
$$;

-- Sign-up: the app sends {"kvkk": {...}} in the sign-up metadata. Age is checked here and
-- the birth date is never stored; consents are recorded in the same transaction.
--   kvkk.birth_date   'YYYY-MM-DD'
--   kvkk.accepted     {doc_type: version} for agreements
--   kvkk.informed     {doc_type: version} for notices that were shown
--   kvkk.consents     {doc_type: {"version": n, "granted": bool}} for optional consents
--   kvkk.app_version, kvkk.platform, kvkk.user_agent
create function public.kvkk_before_signup()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_kvkk jsonb := new.raw_user_meta_data -> 'kvkk';
    v_birth date;
    v_age integer;
    v_type text;
    v_item jsonb;
    v_app text;
    v_platform text;
    v_ua text;
begin
    if v_kvkk is null or jsonb_typeof(v_kvkk) <> 'object' then
        return new; -- Sign-ups without the form (dashboard invites): consents are asked at first login.
    end if;

    begin
        v_birth := (v_kvkk ->> 'birth_date')::date;
    exception when others then
        v_birth := null;
    end;
    if v_birth is null then
        raise exception using errcode = 'P0001', message = 'birth_date_required';
    end if;
    v_age := extract(year from age((now() at time zone 'Europe/Istanbul')::date, v_birth))::integer;
    if v_age < public.compliance_int('min_age') then
        raise exception using errcode = 'P0001', message = 'underage';
    end if;

    v_app := v_kvkk ->> 'app_version';
    v_platform := v_kvkk ->> 'platform';
    v_ua := v_kvkk ->> 'user_agent';

    for v_type in select t.doc_type from public.legal_document_types t where t.kind = 'agreement' loop
        if not (coalesce(v_kvkk -> 'accepted', '{}') ? v_type) then
            raise exception using errcode = 'P0001', message = 'terms_required';
        end if;
        perform public.insert_consent_log(new.id, v_type, (v_kvkk -> 'accepted' ->> v_type)::integer, 'accepted',
                                          'register', null, v_ua, v_app, v_platform);
    end loop;

    for v_type, v_item in select * from jsonb_each(coalesce(v_kvkk -> 'informed', '{}')) loop
        if exists (select 1 from public.legal_document_types where doc_type = v_type and kind = 'notice') then
            perform public.insert_consent_log(new.id, v_type, v_item::text::integer, 'informed', 'register',
                                              null, v_ua, v_app, v_platform);
        end if;
    end loop;

    for v_type, v_item in select * from jsonb_each(coalesce(v_kvkk -> 'consents', '{}')) loop
        if exists (select 1 from public.legal_document_types where doc_type = v_type and kind = 'consent') then
            perform public.insert_consent_log(new.id, v_type, (v_item ->> 'version')::integer,
                                              case when (v_item ->> 'granted')::boolean then 'accepted' else 'declined' end,
                                              'register', null, v_ua, v_app, v_platform);
        end if;
    end loop;

    -- The birth date was only needed for the age check.
    new.raw_user_meta_data := new.raw_user_meta_data #- '{kvkk,birth_date}';
    return new;
end;
$$;

create trigger kvkk_before_signup
    before insert on auth.users
    for each row execute function public.kvkk_before_signup();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_consents jsonb := new.raw_user_meta_data -> 'kvkk' -> 'consents';
begin
    insert into public.profiles (id, email)
    values (new.id, coalesce(new.email, ''));
    if v_consents is not null and jsonb_typeof(v_consents) = 'object' then
        perform public.apply_consent_flag(new.id, 'acik_riza_pazarlama_eposta',
                                          coalesce((v_consents -> 'acik_riza_pazarlama_eposta' ->> 'granted')::boolean, false));
        perform public.apply_consent_flag(new.id, 'acik_riza_pazarlama_bildirim',
                                          coalesce((v_consents -> 'acik_riza_pazarlama_bildirim' ->> 'granted')::boolean, false));
    end if;
    return new;
end;
$$;

-- Texts the signed-in person still has to accept (agreements) or see (changed notices).
create function public.pending_legal_documents()
returns table (doc_type text, kind text, version integer, title text, content_sha256 text)
language sql
stable
security definer
set search_path = ''
as $$
    select d.doc_type, t.kind, d.version, d.title, d.content_sha256
    from public.legal_documents d
    join public.legal_document_types t on t.doc_type = d.doc_type
    where d.is_active
      and auth.uid() is not null
      and (t.kind = 'agreement' or (t.kind = 'notice' and t.notify_on_change))
      and not exists (
          select 1 from public.consent_logs c
          where c.user_id = auth.uid() and c.document_type = d.doc_type and c.document_version = d.version
            and c.action in ('accepted', 'informed')
      )
    order by t.sort_order
$$;

create function public.acknowledge_legal_document(
    p_doc_type text, p_version integer, p_channel text default 'login',
    p_app_version text default null, p_platform text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_kind text;
begin
    if auth.uid() is null then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;
    select t.kind into v_kind
    from public.legal_documents d join public.legal_document_types t on t.doc_type = d.doc_type
    where d.doc_type = p_doc_type and d.version = p_version and d.is_active;
    if v_kind is null or v_kind = 'consent' then
        raise exception using errcode = 'P0001', message = 'legal_document_not_found';
    end if;
    if p_channel not in ('register', 'login', 'settings', 'purchase') or (v_kind = 'purchase') <> (p_channel = 'purchase') then
        raise exception using errcode = 'P0001', message = 'invalid_channel';
    end if;
    perform public.insert_consent_log(auth.uid(), p_doc_type, p_version,
                                      case when v_kind in ('agreement', 'purchase') then 'accepted' else 'informed' end,
                                      p_channel, public.request_ip(), public.request_user_agent(), p_app_version, p_platform);
end;
$$;

-- Optional consents: granting, declining and withdrawing take effect immediately.
create function public.set_consent(
    p_doc_type text, p_granted boolean, p_channel text default 'settings',
    p_app_version text default null, p_platform text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_version integer;
    v_last text;
begin
    if auth.uid() is null then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;
    select d.version into v_version
    from public.legal_documents d join public.legal_document_types t on t.doc_type = d.doc_type
    where d.doc_type = p_doc_type and d.is_active and t.kind = 'consent';
    if v_version is null or p_granted is null then
        raise exception using errcode = 'P0001', message = 'legal_document_not_found';
    end if;
    if p_channel not in ('register', 'login', 'settings') then
        raise exception using errcode = 'P0001', message = 'invalid_channel';
    end if;
    select c.action into v_last from public.consent_logs c
    where c.user_id = auth.uid() and c.document_type = p_doc_type
    order by c.seq desc limit 1;

    perform public.insert_consent_log(
        auth.uid(), p_doc_type, v_version,
        case when p_granted then 'accepted' when v_last = 'accepted' then 'withdrawn' else 'declined' end,
        p_channel, public.request_ip(), public.request_user_agent(), p_app_version, p_platform);
    perform public.apply_consent_flag(auth.uid(), p_doc_type, p_granted);
end;
$$;

create function public.my_consents()
returns table (doc_type text, title text, active_version integer, granted boolean, granted_version integer,
               changed_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
    select d.doc_type, d.title, d.version,
           coalesce(l.action = 'accepted', false), case when l.action = 'accepted' then l.document_version end,
           l.created_at
    from public.legal_documents d
    join public.legal_document_types t on t.doc_type = d.doc_type
    left join lateral (
        select c.action, c.document_version, c.created_at from public.consent_logs c
        where c.user_id = auth.uid() and c.document_type = d.doc_type
        order by c.seq desc limit 1
    ) l on true
    where d.is_active and t.kind = 'consent' and auth.uid() is not null
    order by t.sort_order
$$;

create function public.my_consent_history()
returns table (document_type text, document_version integer, content_sha256 text, action text, channel text,
               created_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
    select c.document_type, c.document_version, c.content_sha256, c.action, c.channel, c.created_at
    from public.consent_logs c
    where c.user_id = auth.uid()
    order by c.seq desc
    limit 500
$$;

-- Older app versions keep working: the marketing switch is the e-mail consent.
create or replace function public.set_marketing_consent(p_opt_in boolean)
returns void
language sql
security definer
set search_path = ''
as $$
    select public.set_consent('acik_riza_pazarlama_eposta', p_opt_in, 'settings', null, null)
$$;

-- One-click unsubscribe from e-mail (service role, HMAC-checked link).
create or replace function public.unsubscribe_marketing(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_version integer;
begin
    if not exists (select 1 from public.profiles where id = p_user_id and marketing_opt_in) then
        return;
    end if;
    select version into v_version from public.legal_documents
    where doc_type = 'acik_riza_pazarlama_eposta' and is_active;
    if v_version is not null then
        perform public.insert_consent_log(p_user_id, 'acik_riza_pazarlama_eposta', v_version, 'withdrawn',
                                          'email_link', null, null, null, null);
    end if;
    perform public.apply_consent_flag(p_user_id, 'acik_riza_pazarlama_eposta', false);
end;
$$;

-- Access logs (5651) ----------------------------------------------------------------------------------

-- The app reports its own sign-in / sign-out with device details; the IP comes from the gateway.
create function public.log_access_event(
    p_event text, p_device_info text default null, p_app_version text default null, p_platform text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;
    if p_event not in ('login', 'logout') then
        raise exception using errcode = 'P0001', message = 'invalid_event';
    end if;
    if (select count(*) from public.access_logs
        where user_id = auth.uid() and source = 'app' and occurred_at > now() - interval '1 hour') >= 60 then
        raise exception using errcode = 'P0001', message = 'rate_limited';
    end if;
    insert into public.access_logs (user_id, event, source, ip, device_info, app_version, platform, occurred_at)
    values (auth.uid(), p_event, 'app', public.request_ip(), left(p_device_info, 300), left(p_app_version, 40),
            left(p_platform, 20), now());
end;
$$;

-- Failed sign-ins (no session yet). The e-mail is kept only as a hash.
create function public.log_failed_login(
    p_email text, p_device_info text default null, p_app_version text default null, p_platform text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_ip inet := public.request_ip();
    v_hash text := encode(sha256(convert_to(lower(btrim(coalesce(p_email, ''))), 'UTF8')), 'hex');
begin
    if (select count(*) from public.access_logs
        where event = 'login_failed' and occurred_at > now() - interval '1 hour'
          and (email_sha256 = v_hash or (v_ip is not null and ip = v_ip))) >= 30 then
        return; -- Enough evidence already; do not let one client fill the log.
    end if;
    insert into public.access_logs (event, source, email_sha256, ip, device_info, app_version, platform, occurred_at)
    values ('login_failed', 'app', v_hash, v_ip, left(p_device_info, 300), left(p_app_version, 40),
            left(p_platform, 20), now());
end;
$$;

-- Copies sign-in, sign-out, sign-up and recovery events from Supabase Auth's own audit log
-- (which has the server-side IP) into access_logs. Run every few minutes by pg_cron.
create function public.sync_auth_access_logs()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    r record;
    v_count integer := 0;
    v_since timestamptz;
    v_ip inet;
begin
    if to_regclass('auth.audit_log_entries') is null then
        return 0;
    end if;
    perform pg_advisory_xact_lock(hashtext('sync_auth_access_logs'));
    select coalesce(max(occurred_at), '-infinity'::timestamptz) - interval '1 hour' into v_since
    from public.access_logs where source = 'auth_server';

    for r in execute $q$
        select e.id, e.created_at, e.ip_address, e.payload ->> 'action' as action, e.payload ->> 'actor_id' as actor_id
        from auth.audit_log_entries e
        where e.created_at > $1
          and e.payload ->> 'action' in ('login', 'logout', 'user_signedup', 'user_recovery_requested', 'user_deleted')
        order by e.created_at, e.id
    $q$ using v_since loop
        if exists (select 1 from public.access_logs where auth_event_id = r.id) then
            continue;
        end if;
        begin
            v_ip := nullif(btrim(r.ip_address), '')::inet;
        exception when others then
            v_ip := null;
        end;
        insert into public.access_logs (user_id, event, source, ip, auth_event_id, occurred_at)
        values (
            case when r.actor_id ~ '^[0-9a-f-]{36}$' then r.actor_id::uuid end,
            case r.action
                when 'user_signedup' then 'signup'
                when 'user_recovery_requested' then 'password_recovery'
                when 'user_deleted' then 'account_deleted'
                else r.action end,
            'auth_server', v_ip, r.id, r.created_at);
        v_count := v_count + 1;
    end loop;
    return v_count;
end;
$$;

-- The person's own access records (shown in the app and included in the data export).
create function public.my_access_logs(p_limit integer default 100)
returns table (event text, ip inet, device_info text, platform text, occurred_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
    select a.event, a.ip, a.device_info, a.platform, a.occurred_at
    from public.access_logs a
    where a.user_id = auth.uid()
    order by a.occurred_at desc
    limit least(greatest(coalesce(p_limit, 100), 1), 500)
$$;

-- Panel log views (compliance) -------------------------------------------------------------------------

create function public.admin_list_logs(
    p_table text,
    p_from timestamptz default null,
    p_to timestamptz default null,
    p_user_id uuid default null,
    p_action text default null,
    p_limit integer default 200
)
returns setof jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_user_col text;
    v_action_col text;
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    case p_table
        when 'consent_logs' then v_user_col := 'user_id'; v_action_col := 'action';
        when 'access_logs' then v_user_col := 'user_id'; v_action_col := 'event';
        when 'admin_audit_logs' then v_user_col := 'admin_id'; v_action_col := 'action';
        when 'deletion_logs' then v_user_col := 'subject_user_id'; v_action_col := 'reason';
        else raise exception using errcode = 'P0001', message = 'unknown_log';
    end case;
    perform public.write_admin_audit('view.' || p_table, p_table, p_user_id::text,
                                     coalesce(public.request_audit_reason(), 'Panel görüntüleme'),
                                     jsonb_build_object('from', p_from, 'to', p_to, 'action', p_action));
    return query execute format(
        'select to_jsonb(t) from public.%I t
         where ($1 is null or t.created_at >= $1) and ($2 is null or t.created_at < $2)
           and ($3 is null or t.%I = $3) and ($4 is null or t.%I = $4)
         order by t.seq desc limit $5', p_table, v_user_col, v_action_col)
    using p_from, p_to, p_user_id, p_action, least(greatest(coalesce(p_limit, 200), 1), 5000);
end;
$$;

create function public.admin_verify_log_chains()
returns table (table_name text, rows_checked bigint, ok boolean, first_bad_seq bigint, anchor_hash text, head_matches boolean)
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    t text;
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    perform public.write_admin_audit('verify.log_chains', 'panel', null,
                                     coalesce(public.request_audit_reason(), 'Hash zinciri doğrulama'));
    foreach t in array array['consent_logs', 'access_logs', 'admin_audit_logs', 'deletion_logs'] loop
        return query select * from public.verify_log_chain(t);
    end loop;
end;
$$;

-- Scheduling (pg_cron where available) -----------------------------------------------------------------

do $$
begin
    if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
        create extension if not exists pg_cron;
        perform cron.schedule('kvkk-sync-access-logs', '*/5 * * * *', 'select public.sync_auth_access_logs()');
    end if;
end;
$$;

-- Grants ----------------------------------------------------------------------------------------------------

do $$
declare
    f text;
begin
    -- Internal only (called from other SECURITY DEFINER functions and triggers).
    foreach f in array array[
        'public.compliance_value(text)', 'public.compliance_int(text)', 'public.log_row_hash(text, jsonb)', 'public.log_chain_before_insert()',
        'public.log_append_only()', 'public.verify_log_chain(text)', 'public.purge_log(text, timestamptz)',
        'public.write_admin_audit(text, text, text, text, jsonb, uuid, text[])', 'public.audit_staff_change()',
        'public.staff_gate(text[], text, boolean)', 'public.legal_documents_guard()',
        'public.insert_consent_log(uuid, text, integer, text, text, inet, text, text, text)',
        'public.apply_consent_flag(uuid, text, boolean)', 'public.kvkk_before_signup()',
        'public.sync_auth_access_logs()'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated, service_role', f);
    end loop;

    -- Everyone, signed in or not (website, sign-up screen).
    foreach f in array array[
        'public.public_compliance_config()', 'public.list_legal_documents()',
        'public.get_legal_document(text, integer)', 'public.list_legal_document_versions(text)',
        'public.log_failed_login(text, text, text, text)'
    ] loop
        execute format('revoke all on function %s from public', f);
        execute format('grant execute on function %s to anon, authenticated, service_role', f);
    end loop;

    -- Signed-in people (each function checks its own rules).
    foreach f in array array[
        'public.staff_roles()', 'public.staff_mfa_satisfied()',
        'public.has_staff_role(text[])', 'public.my_staff_access()', 'public.require_staff_role(text[])',
        'public.record_admin_action(text, text, text, jsonb)', 'public.admin_set_compliance_setting(text, jsonb)',
        'public.admin_list_compliance_settings()', 'public.admin_publish_legal_document(text, text, text)',
        'public.admin_legal_document_stats()', 'public.pending_legal_documents()',
        'public.acknowledge_legal_document(text, integer, text, text, text)',
        'public.set_consent(text, boolean, text, text, text)', 'public.my_consents()', 'public.my_consent_history()',
        'public.log_access_event(text, text, text, text)', 'public.my_access_logs(integer)',
        'public.admin_list_logs(text, timestamptz, timestamptz, uuid, text, integer)',
        'public.admin_verify_log_chains()'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;

    -- Edge Functions only.
    execute 'revoke all on function public.service_record_admin_action(uuid, text[], text, text, text, text, jsonb) from public, anon, authenticated';
    execute 'grant execute on function public.service_record_admin_action(uuid, text[], text, text, text, text, jsonb) to service_role';
end;
$$;

-- request_* helpers are harmless but not part of the API.
revoke all on function public.request_header(text) from public, anon, authenticated, service_role;
revoke all on function public.request_ip() from public, anon, authenticated, service_role;
revoke all on function public.request_user_agent() from public, anon, authenticated, service_role;
revoke all on function public.request_audit_reason() from public, anon, authenticated, service_role;
