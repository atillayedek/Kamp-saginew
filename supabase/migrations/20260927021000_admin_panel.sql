-- Web admin panel (/admin): overview, users, purchases and revenue, universities,
-- e-mail broadcasts through Resend, marketing consent.
--
-- Every admin read goes through a SECURITY DEFINER function that checks
-- is_admin() (JWT app_metadata.role, settable only with the service role).
-- Revenue comes from purchases verified with Google Play by verify-purchase;
-- nothing is estimated.

-- Marketing consent ---------------------------------------------------------------
-- Marketing e-mails go only to people who opted in (Turkish law 6563, KVKK).
alter table public.profiles
    add column marketing_opt_in boolean not null default false,
    add column marketing_opt_in_at timestamptz,
    add constraint profiles_marketing_opt_in_at check (marketing_opt_in = (marketing_opt_in_at is not null));

create function public.set_marketing_consent(p_opt_in boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;
    if p_opt_in is null then
        raise exception using errcode = 'P0001', message = 'invalid_input';
    end if;
    update public.profiles
    set marketing_opt_in = p_opt_in,
        marketing_opt_in_at = case when p_opt_in then coalesce(marketing_opt_in_at, now()) end,
        updated_at = now()
    where id = auth.uid();
end;
$$;

-- Called by the email-unsubscribe Edge Function after it checks the signed link.
create function public.unsubscribe_marketing(p_user_id uuid)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.profiles
    set marketing_opt_in = false, marketing_opt_in_at = null, updated_at = now()
    where id = p_user_id and marketing_opt_in;
$$;

-- Purchases -------------------------------------------------------------------------
-- One row per Google Play order verified by verify-purchase. Renewals appear
-- when the app verifies the renewed purchase (restore / app start).
create table public.purchase_events (
    id uuid primary key default gen_random_uuid(),
    -- Kept after account deletion as a financial record, without the person.
    user_id uuid references public.profiles (id) on delete set null,
    plan_id uuid not null references public.subscription_plans (id) on delete restrict,
    order_id text check (order_id is null or char_length(order_id) between 1 and 200),
    dedupe_key text not null unique,
    amount_micros bigint check (amount_micros is null or amount_micros >= 0),
    currency text check (currency is null or currency ~ '^[A-Z]{3}$'),
    period_ends_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint purchase_events_price check ((amount_micros is null) = (currency is null))
);

create index purchase_events_user_idx on public.purchase_events (user_id);
create index purchase_events_plan_idx on public.purchase_events (plan_id);
create index purchase_events_created_idx on public.purchase_events (created_at);

alter table public.purchase_events enable row level security;
-- No policies: admins read through admin_* functions, writes use the service role.

create function public.record_purchase_event(
    p_user_id uuid,
    p_product_id text,
    p_purchase_token text,
    p_order_id text,
    p_expires_at timestamptz,
    p_amount_micros bigint,
    p_currency text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_plan uuid;
begin
    select id into v_plan from public.subscription_plans where play_product_id = p_product_id;
    if v_plan is null then
        raise exception using errcode = 'P0001', message = 'plan_not_available';
    end if;
    if p_user_id is null or p_purchase_token is null or p_expires_at is null
        or (p_amount_micros is null) <> (p_currency is null)
        or p_amount_micros < 0
        or (p_currency is not null and p_currency !~ '^[A-Z]{3}$') then
        raise exception using errcode = 'P0001', message = 'invalid_purchase_event';
    end if;

    insert into public.purchase_events (user_id, plan_id, order_id, dedupe_key, amount_micros, currency, period_ends_at)
    values (
        p_user_id, v_plan, nullif(btrim(p_order_id), ''),
        coalesce('order:' || nullif(btrim(p_order_id), ''), 'token:' || md5(p_purchase_token) || ':' || p_expires_at::text),
        p_amount_micros, p_currency, p_expires_at
    )
    on conflict (dedupe_key) do nothing;
end;
$$;

-- Broadcasts ----------------------------------------------------------------------
create table public.email_broadcasts (
    id uuid primary key default gen_random_uuid(),
    sent_by uuid references auth.users (id) on delete set null,
    audience text not null check (audience in ('ALL', 'APPROVED', 'PENDING_REVIEW', 'DOCUMENT_REQUIRED', 'PREMIUM')),
    marketing boolean not null,
    subject text not null check (char_length(btrim(subject)) between 1 and 150),
    body text not null check (char_length(btrim(body)) between 1 and 20000),
    recipient_count integer not null default 0 check (recipient_count >= 0),
    sent_count integer not null default 0 check (sent_count >= 0),
    failed_count integer not null default 0 check (failed_count >= 0),
    status text not null default 'SENDING' check (status in ('SENDING', 'SENT', 'PARTIAL', 'FAILED')),
    created_at timestamptz not null default now(),
    finished_at timestamptz
);

create index email_broadcasts_sent_by_idx on public.email_broadcasts (sent_by);
create index email_broadcasts_created_idx on public.email_broadcasts (created_at);

alter table public.email_broadcasts enable row level security;

-- Recipients for the admin-broadcast Edge Function (service role only).
-- Suspended accounts never receive mail; marketing mail needs consent.
create function public.broadcast_recipients(p_audience text, p_marketing boolean)
returns table (user_id uuid, email text)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if p_audience is null or p_audience not in ('ALL', 'APPROVED', 'PENDING_REVIEW', 'DOCUMENT_REQUIRED', 'PREMIUM')
        or p_marketing is null then
        raise exception using errcode = 'P0001', message = 'invalid_audience';
    end if;
    return query
    select p.id, p.email
    from public.profiles p
    where p.account_status <> 'SUSPENDED'
      and (not p_marketing or p.marketing_opt_in)
      and case p_audience
            when 'ALL' then true
            when 'PREMIUM' then exists (
                select 1 from public.entitlements e where e.user_id = p.id and e.expires_at > now())
            else p.account_status::text = p_audience
          end
    order by p.created_at;
end;
$$;

-- How many people a broadcast would reach, shown before the admin confirms.
create function public.admin_broadcast_audience_size(p_audience text, p_marketing boolean)
returns bigint
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    return (select count(*) from public.broadcast_recipients(p_audience, p_marketing));
end;
$$;

-- Admin reads -------------------------------------------------------------------
create function public.admin_overview()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_result jsonb;
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;

    select jsonb_build_object(
        'total_users', (select count(*) from public.profiles),
        'new_users_7_days', (select count(*) from public.profiles where created_at > now() - interval '7 days'),
        'new_users_30_days', (select count(*) from public.profiles where created_at > now() - interval '30 days'),
        'status_counts', (
            select coalesce(jsonb_object_agg(s.status, s.n), '{}'::jsonb)
            from (select account_status::text as status, count(*) as n from public.profiles group by account_status) s
        ),
        'pending_verifications', (select count(*) from public.student_verifications where status = 'PENDING'),
        'open_reports', (select count(*) from public.reports where status = 'OPEN'),
        'active_premium', (select count(*) from public.entitlements where expires_at > now()),
        'purchase_count', (select count(*) from public.purchase_events),
        'purchases_30_days', (select count(*) from public.purchase_events where created_at > now() - interval '30 days'),
        'purchases_without_price', (select count(*) from public.purchase_events where amount_micros is null),
        'marketing_opt_in', (select count(*) from public.profiles where marketing_opt_in),
        'revenue', (
            select coalesce(jsonb_object_agg(r.currency, jsonb_build_object(
                'total_micros', r.total, 'last_30_days_micros', r.recent)), '{}'::jsonb)
            from (
                select currency,
                       sum(amount_micros) as total,
                       coalesce(sum(amount_micros) filter (where created_at > now() - interval '30 days'), 0) as recent
                from public.purchase_events
                where currency is not null
                group by currency
            ) r
        ),
        'daily', (
            select jsonb_agg(jsonb_build_object(
                'day', d.day,
                'signups', (select count(*) from public.profiles p
                            where p.created_at >= d.day and p.created_at < d.day + 1),
                'purchases', (select count(*) from public.purchase_events e
                              where e.created_at >= d.day and e.created_at < d.day + 1)
            ) order by d.day)
            from (
                select generate_series(current_date - 29, current_date, interval '1 day')::date as day
            ) d
        )
    ) into v_result;
    return v_result;
end;
$$;

create function public.admin_list_users(
    p_search text,
    p_status public.account_status,
    p_limit integer,
    p_offset integer
)
returns table (
    id uuid,
    email text,
    full_name text,
    username text,
    university_name text,
    department text,
    account_status public.account_status,
    is_premium boolean,
    marketing_opt_in boolean,
    created_at timestamptz,
    total_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_pattern text := '%' || replace(replace(replace(coalesce(btrim(p_search), ''), '\', '\\'), '%', '\%'), '_', '\_') || '%';
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if p_limit is null or p_limit < 1 or p_limit > 500 or p_offset is null or p_offset < 0 then
        raise exception using errcode = 'P0001', message = 'invalid_page';
    end if;
    return query
    select p.id, p.email, p.full_name, p.username, u.name, p.department, p.account_status,
           exists (select 1 from public.entitlements e where e.user_id = p.id and e.expires_at > now()),
           p.marketing_opt_in, p.created_at,
           count(*) over ()
    from public.profiles p
    left join public.universities u on u.id = p.university_id
    where (p_status is null or p.account_status = p_status)
      and (p.email ilike v_pattern or coalesce(p.username, '') ilike v_pattern or coalesce(p.full_name, '') ilike v_pattern)
    order by p.created_at desc
    limit p_limit offset p_offset;
end;
$$;

create function public.admin_list_purchases(p_limit integer, p_offset integer)
returns table (
    id uuid,
    user_id uuid,
    email text,
    username text,
    plan_name text,
    order_id text,
    amount_micros bigint,
    currency text,
    period_ends_at timestamptz,
    created_at timestamptz,
    total_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if p_limit is null or p_limit < 1 or p_limit > 500 or p_offset is null or p_offset < 0 then
        raise exception using errcode = 'P0001', message = 'invalid_page';
    end if;
    return query
    select e.id, e.user_id, p.email, p.username, s.name, e.order_id, e.amount_micros, e.currency,
           e.period_ends_at, e.created_at, count(*) over ()
    from public.purchase_events e
    join public.subscription_plans s on s.id = e.plan_id
    left join public.profiles p on p.id = e.user_id
    order by e.created_at desc
    limit p_limit offset p_offset;
end;
$$;

create function public.admin_university_stats()
returns table (
    university_id uuid,
    name text,
    city text,
    students bigint,
    approved bigint,
    premium bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    return query
    select u.id, u.name, u.city,
           count(p.id),
           count(p.id) filter (where p.account_status = 'APPROVED'),
           count(p.id) filter (where exists (
               select 1 from public.entitlements e where e.user_id = p.id and e.expires_at > now()))
    from public.universities u
    join public.profiles p on p.university_id = u.id
    group by u.id, u.name, u.city
    order by count(p.id) desc, u.name;
end;
$$;

create function public.admin_list_broadcasts(p_limit integer)
returns table (
    id uuid,
    sent_by_email text,
    audience text,
    marketing boolean,
    subject text,
    recipient_count integer,
    sent_count integer,
    failed_count integer,
    status text,
    created_at timestamptz,
    finished_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    return query
    select b.id, a.email::text, b.audience, b.marketing, b.subject, b.recipient_count, b.sent_count,
           b.failed_count, b.status, b.created_at, b.finished_at
    from public.email_broadcasts b
    left join auth.users a on a.id = b.sent_by
    order by b.created_at desc
    limit least(greatest(coalesce(p_limit, 20), 1), 200);
end;
$$;

-- Lifts a suspension; the status follows the person's latest verification.
create function public.admin_reinstate_user(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles%rowtype;
    v_verification public.verification_status;
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    select * into v_profile from public.profiles where id = p_user_id for update;
    if not found or v_profile.account_status <> 'SUSPENDED' then
        raise exception using errcode = 'P0001', message = 'user_not_suspended';
    end if;
    select status into v_verification from public.student_verifications
    where user_id = p_user_id order by created_at desc limit 1;

    update public.profiles
    set account_status = case
            when v_profile.username is null or v_profile.university_id is null then 'PROFILE_INCOMPLETE'
            when v_verification = 'APPROVED' then 'APPROVED'
            when v_verification = 'PENDING' then 'PENDING_REVIEW'
            when v_verification = 'REJECTED' then 'REJECTED'
            else 'DOCUMENT_REQUIRED'
        end::public.account_status,
        updated_at = now()
    where id = p_user_id;
end;
$$;

-- Grants --------------------------------------------------------------------------
do $$
declare
    f text;
begin
    foreach f in array array[
        'public.set_marketing_consent(boolean)',
        'public.admin_overview()',
        'public.admin_list_users(text, public.account_status, integer, integer)',
        'public.admin_list_purchases(integer, integer)',
        'public.admin_university_stats()',
        'public.admin_list_broadcasts(integer)',
        'public.admin_reinstate_user(uuid)',
        'public.admin_broadcast_audience_size(text, boolean)'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;

    foreach f in array array[
        'public.unsubscribe_marketing(uuid)',
        'public.record_purchase_event(uuid, text, text, text, timestamptz, bigint, text)',
        'public.broadcast_recipients(text, boolean)'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated', f);
        execute format('grant execute on function %s to service_role', f);
    end loop;
end;
$$;
