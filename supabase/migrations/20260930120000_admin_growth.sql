-- Admin: in-app announcements, activity statistics, Premium gifts and promo codes.
--
-- Premium now has two sources: a Google Play subscription verified by the
-- server (entitlements) and a grant — given by an admin or earned with a promo
-- code. Both point at a plan in subscription_plans, so limits stay data.
-- Everything that asked "is this person Premium?" goes through is_premium().

-- Premium from gifts and promo codes --------------------------------------------------

create table public.promo_codes (
    id uuid primary key default gen_random_uuid(),
    code text not null unique check (code ~ '^[A-Z0-9-]{4,32}$'),
    plan_id uuid not null references public.subscription_plans (id) on delete restrict,
    days integer not null check (days between 1 and 365),
    max_redemptions integer not null check (max_redemptions between 1 and 100000),
    redemption_count integer not null default 0 check (redemption_count >= 0),
    expires_at timestamptz,
    created_by uuid references auth.users (id) on delete set null,
    created_at timestamptz not null default now(),
    disabled_at timestamptz
);

create index promo_codes_plan_idx on public.promo_codes (plan_id);
create index promo_codes_created_by_idx on public.promo_codes (created_by);

create table public.premium_grants (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    plan_id uuid not null references public.subscription_plans (id) on delete restrict,
    expires_at timestamptz not null,
    source text not null check (source in ('ADMIN', 'PROMO')),
    promo_code_id uuid references public.promo_codes (id) on delete set null,
    granted_by uuid references auth.users (id) on delete set null,
    note text check (note is null or char_length(note) <= 200),
    created_at timestamptz not null default now(),
    revoked_at timestamptz,
    constraint premium_grants_promo_has_code check (source <> 'PROMO' or promo_code_id is not null)
);

create index premium_grants_user_idx on public.premium_grants (user_id, expires_at desc);
create index premium_grants_plan_idx on public.premium_grants (plan_id);
create index premium_grants_code_idx on public.premium_grants (promo_code_id);
create index premium_grants_granted_by_idx on public.premium_grants (granted_by);

create table public.promo_redemptions (
    code_id uuid not null references public.promo_codes (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (code_id, user_id)
);

create index promo_redemptions_user_idx on public.promo_redemptions (user_id);

alter table public.promo_codes enable row level security;
alter table public.premium_grants enable row level security;
alter table public.promo_redemptions enable row level security;
-- No policies: read and written through the functions below.

-- Every current source of Premium for a person, with its plan.
create function public.active_premium(p_user_id uuid)
returns table (plan_id uuid, expires_at timestamptz, source text)
language sql
stable
security definer
set search_path = ''
as $$
    select e.plan_id, e.expires_at, 'PLAY'
    from public.entitlements e
    join public.subscription_plans p on p.id = e.plan_id and p.is_active
    where e.user_id = p_user_id and e.expires_at > now()
    union all
    select g.plan_id, g.expires_at, g.source
    from public.premium_grants g
    join public.subscription_plans p on p.id = g.plan_id and p.is_active
    where g.user_id = p_user_id and g.expires_at > now() and g.revoked_at is null
$$;

create or replace function public.is_premium(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (select 1 from public.active_premium(p_user_id))
$$;

-- The best limits among the person's active plans; free limits otherwise.
create or replace function public.current_limits(p_user_id uuid)
returns table (ai_analyze_daily integer, ai_publish_daily integer, max_active_requirements integer)
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(max(p.ai_analyze_daily), 30), coalesce(max(p.ai_publish_daily), 10), coalesce(max(p.max_active_requirements), 20)
    from public.active_premium(p_user_id) a
    join public.subscription_plans p on p.id = a.plan_id
$$;

drop function public.my_subscription();

create function public.my_subscription()
returns table (
    plan_name text,
    play_product_id text,
    expires_at timestamptz,
    source text,
    ai_analyze_daily integer,
    ai_publish_daily integer,
    max_active_requirements integer
)
language sql
stable
security definer
set search_path = ''
as $$
    select b.name, b.play_product_id, b.expires_at, b.source, l.ai_analyze_daily, l.ai_publish_daily, l.max_active_requirements
    from public.current_limits(auth.uid()) l
    left join lateral (
        select p.name, p.play_product_id, a.expires_at, a.source
        from public.active_premium(auth.uid()) a
        join public.subscription_plans p on p.id = a.plan_id
        order by a.expires_at desc
        limit 1
    ) b on true
    where exists (select 1 from public.current_student())
$$;

-- Adds days on top of the person's latest unexpired grant, so gifts stack.
create function public.insert_premium_grant(
    p_user_id uuid,
    p_plan_id uuid,
    p_days integer,
    p_source text,
    p_code_id uuid,
    p_note text
)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_start timestamptz;
    v_expires timestamptz;
begin
    select greatest(now(), coalesce(max(g.expires_at), now())) into v_start
    from public.premium_grants g
    where g.user_id = p_user_id and g.revoked_at is null and g.expires_at > now();
    v_expires := v_start + make_interval(days => p_days);
    insert into public.premium_grants (user_id, plan_id, expires_at, source, promo_code_id, granted_by, note)
    values (p_user_id, p_plan_id, v_expires, p_source, p_code_id,
            case when p_source = 'ADMIN' then auth.uid() end, nullif(btrim(coalesce(p_note, '')), ''));
    return v_expires;
end;
$$;

create function public.redeem_promo_code(p_code text)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_code public.promo_codes%rowtype;
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    select * into v_code from public.promo_codes
    where code = upper(btrim(coalesce(p_code, ''))) for update;
    if not found or v_code.disabled_at is not null
        or (v_code.expires_at is not null and v_code.expires_at <= now())
        or not exists (select 1 from public.subscription_plans p where p.id = v_code.plan_id and p.is_active) then
        raise exception using errcode = 'P0001', message = 'promo_code_invalid';
    end if;
    if exists (select 1 from public.promo_redemptions where code_id = v_code.id and user_id = auth.uid()) then
        raise exception using errcode = 'P0001', message = 'promo_code_used';
    end if;
    if v_code.redemption_count >= v_code.max_redemptions then
        raise exception using errcode = 'P0001', message = 'promo_code_exhausted';
    end if;
    insert into public.promo_redemptions (code_id, user_id) values (v_code.id, auth.uid());
    update public.promo_codes set redemption_count = redemption_count + 1 where id = v_code.id;
    return public.insert_premium_grant(auth.uid(), v_code.plan_id, v_code.days, 'PROMO', v_code.id, null);
end;
$$;

-- Admin: Premium --------------------------------------------------------------------------

create function public.admin_list_plans()
returns table (id uuid, name text, play_product_id text, is_active boolean)
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
    select p.id, p.name, p.play_product_id, p.is_active from public.subscription_plans p order by p.sort_order, p.name;
end;
$$;

create function public.admin_grant_premium(p_user_id uuid, p_plan_id uuid, p_days integer, p_note text default null)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if not exists (select 1 from public.profiles where id = p_user_id) then
        raise exception using errcode = 'P0001', message = 'user_not_found';
    end if;
    if not exists (select 1 from public.subscription_plans where id = p_plan_id and is_active) then
        raise exception using errcode = 'P0001', message = 'plan_not_available';
    end if;
    if p_days is null or p_days not between 1 and 365 or char_length(coalesce(p_note, '')) > 200 then
        raise exception using errcode = 'P0001', message = 'invalid_grant';
    end if;
    return public.insert_premium_grant(p_user_id, p_plan_id, p_days, 'ADMIN', null, p_note);
end;
$$;

create function public.admin_revoke_grant(p_grant_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    update public.premium_grants set revoked_at = now() where id = p_grant_id and revoked_at is null;
    if not found then
        raise exception using errcode = 'P0001', message = 'grant_not_found';
    end if;
end;
$$;

create function public.admin_list_grants(p_limit integer default 100)
returns table (
    id uuid,
    user_id uuid,
    email text,
    full_name text,
    username text,
    plan_name text,
    source text,
    promo_code text,
    note text,
    expires_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz
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
    select g.id, g.user_id, p.email, p.full_name, p.username, s.name, g.source, c.code, g.note, g.expires_at, g.revoked_at, g.created_at
    from public.premium_grants g
    join public.profiles p on p.id = g.user_id
    join public.subscription_plans s on s.id = g.plan_id
    left join public.promo_codes c on c.id = g.promo_code_id
    order by g.created_at desc
    limit least(greatest(coalesce(p_limit, 100), 1), 500);
end;
$$;

-- A missing code is generated: 10 characters without look-alikes (0/O, 1/I).
create function public.admin_create_promo_code(
    p_code text,
    p_plan_id uuid,
    p_days integer,
    p_max_redemptions integer,
    p_expires_at timestamptz default null
)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_code text := nullif(upper(btrim(coalesce(p_code, ''))), '');
    v_alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if not exists (select 1 from public.subscription_plans where id = p_plan_id and is_active) then
        raise exception using errcode = 'P0001', message = 'plan_not_available';
    end if;
    if v_code is null then
        select string_agg(substr(v_alphabet, 1 + (get_byte(b, i) % length(v_alphabet)), 1), '')
        into v_code
        from (select sha256(convert_to(gen_random_uuid()::text || gen_random_uuid()::text, 'UTF8')) as b) r, generate_series(0, 9) i;
    end if;
    if v_code !~ '^[A-Z0-9-]{4,32}$' or p_days is null or p_days not between 1 and 365
        or p_max_redemptions is null or p_max_redemptions not between 1 and 100000
        or (p_expires_at is not null and p_expires_at <= now()) then
        raise exception using errcode = 'P0001', message = 'invalid_promo_code';
    end if;
    insert into public.promo_codes (code, plan_id, days, max_redemptions, expires_at, created_by)
    values (v_code, p_plan_id, p_days, p_max_redemptions, p_expires_at, auth.uid());
    return v_code;
exception
    when unique_violation then
        raise exception using errcode = 'P0001', message = 'promo_code_taken';
end;
$$;

create function public.admin_list_promo_codes()
returns table (
    id uuid,
    code text,
    plan_name text,
    days integer,
    max_redemptions integer,
    redemption_count integer,
    expires_at timestamptz,
    disabled_at timestamptz,
    created_at timestamptz
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
    select c.id, c.code, p.name, c.days, c.max_redemptions, c.redemption_count, c.expires_at, c.disabled_at, c.created_at
    from public.promo_codes c
    join public.subscription_plans p on p.id = c.plan_id
    order by c.created_at desc
    limit 200;
end;
$$;

create function public.admin_disable_promo_code(p_code_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    update public.promo_codes set disabled_at = now() where id = p_code_id and disabled_at is null;
    if not found then
        raise exception using errcode = 'P0001', message = 'promo_code_not_found';
    end if;
end;
$$;

-- Existing admin reads count every source of Premium ---------------------------------------

create or replace function public.admin_overview()
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
        'active_premium', (select count(*) from public.profiles p where public.is_premium(p.id)),
        'active_gifts', (select count(distinct g.user_id) from public.premium_grants g where g.revoked_at is null and g.expires_at > now()),
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

create or replace function public.broadcast_recipients(p_audience text, p_marketing boolean)
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
            when 'PREMIUM' then public.is_premium(p.id)
            else p.account_status::text = p_audience
          end
    order by p.created_at;
end;
$$;

create or replace function public.admin_list_users(
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
           public.is_premium(p.id),
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

create or replace function public.admin_university_stats()
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
           count(p.id) filter (where public.is_premium(p.id))
    from public.universities u
    join public.profiles p on p.university_id = u.id
    group by u.id, u.name, u.city
    order by count(p.id) desc, u.name;
end;
$$;

-- Activity ----------------------------------------------------------------------------------

-- One row per person per day the app was opened; nothing else is recorded.
create table public.user_activity_days (
    user_id uuid not null references public.profiles (id) on delete cascade,
    day date not null,
    primary key (user_id, day)
);

create index user_activity_days_day_idx on public.user_activity_days (day);

alter table public.user_activity_days enable row level security;
-- No policies: written by touch_activity(), read by admin_activity_stats().

create function public.touch_activity()
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.user_activity_days (user_id, day)
    select auth.uid(), (now() at time zone 'Europe/Istanbul')::date
    where auth.uid() is not null and exists (select 1 from public.profiles where id = auth.uid())
    on conflict do nothing
$$;

-- Daily, weekly and monthly active students (Istanbul days), returning users
-- among recent sign-ups and the most active universities.
create function public.admin_activity_stats()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_today date := (now() at time zone 'Europe/Istanbul')::date;
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    return jsonb_build_object(
        'first_day', (select min(day) from public.user_activity_days),
        'today', (select count(*) from public.user_activity_days where day = v_today),
        'last_7_days', (select count(distinct user_id) from public.user_activity_days where day > v_today - 7),
        'last_30_days', (select count(distinct user_id) from public.user_activity_days where day > v_today - 30),
        'daily', (
            select jsonb_agg(jsonb_build_object(
                'day', d.day,
                'active', (select count(*) from public.user_activity_days a where a.day = d.day)
            ) order by d.day)
            from (select generate_series(v_today - 29, v_today, interval '1 day')::date as day) d
        ),
        -- Of the people who signed up 8–60 days ago: how many came back on a later day, and 7+ days later.
        'retention', (
            select jsonb_build_object(
                'cohort', count(*),
                'returned', count(*) filter (where exists (
                    select 1 from public.user_activity_days a
                    where a.user_id = p.id and a.day > (p.created_at at time zone 'Europe/Istanbul')::date)),
                'returned_after_7_days', count(*) filter (where exists (
                    select 1 from public.user_activity_days a
                    where a.user_id = p.id and a.day >= (p.created_at at time zone 'Europe/Istanbul')::date + 7))
            )
            from public.profiles p
            where p.created_at < now() - interval '8 days' and p.created_at >= now() - interval '60 days'
        ),
        'top_universities', (
            select coalesce(jsonb_agg(jsonb_build_object('name', t.name, 'active', t.active) order by t.active desc, t.name), '[]'::jsonb)
            from (
                select u.name, count(distinct a.user_id) as active
                from public.user_activity_days a
                join public.profiles p on p.id = a.user_id
                join public.universities u on u.id = p.university_id
                where a.day > v_today - 7
                group by u.name
                order by count(distinct a.user_id) desc, u.name
                limit 10
            ) t
        )
    );
end;
$$;

-- Announcements ------------------------------------------------------------------------------

create table public.announcements (
    id uuid primary key default gen_random_uuid(),
    title text not null check (char_length(btrim(title)) between 2 and 80),
    body text not null check (char_length(btrim(body)) between 2 and 1000),
    -- Null reaches every approved student.
    university_id uuid references public.universities (id) on delete cascade,
    starts_at timestamptz not null default now(),
    ends_at timestamptz not null,
    created_by uuid references auth.users (id) on delete set null,
    created_at timestamptz not null default now(),
    constraint announcements_ends_after_start check (ends_at > starts_at)
);

create index announcements_active_idx on public.announcements (ends_at);
create index announcements_university_idx on public.announcements (university_id);
create index announcements_created_by_idx on public.announcements (created_by);

create table public.announcement_dismissals (
    announcement_id uuid not null references public.announcements (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (announcement_id, user_id)
);

create index announcement_dismissals_user_idx on public.announcement_dismissals (user_id);

alter table public.announcements enable row level security;
alter table public.announcement_dismissals enable row level security;

-- Students read the announcements meant for them; Realtime uses the same rule
-- to tell the app a new one arrived.
create policy announcements_select_audience on public.announcements
    for select to authenticated
    using (exists (
        select 1 from public.current_student() s
        where announcements.university_id is null or announcements.university_id = s.university_id
    ));

create function public.active_announcements()
returns table (id uuid, title text, body text, created_at timestamptz, ends_at timestamptz)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_student record;
begin
    select * into v_student from public.current_student();
    if not found then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    return query
    select a.id, a.title, a.body, a.created_at, a.ends_at
    from public.announcements a
    where a.starts_at <= now() and a.ends_at > now()
      and (a.university_id is null or a.university_id = v_student.university_id)
      and not exists (select 1 from public.announcement_dismissals d where d.announcement_id = a.id and d.user_id = v_student.id)
    order by a.created_at desc
    limit 5;
end;
$$;

create function public.dismiss_announcement(p_announcement_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    insert into public.announcement_dismissals (announcement_id, user_id)
    select a.id, auth.uid() from public.announcements a where a.id = p_announcement_id
    on conflict do nothing;
end;
$$;

create function public.admin_create_announcement(p_title text, p_body text, p_university_id uuid, p_ends_at timestamptz)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_id uuid;
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if char_length(btrim(coalesce(p_title, ''))) not between 2 and 80
        or char_length(btrim(coalesce(p_body, ''))) not between 2 and 1000
        or p_ends_at is null or p_ends_at <= now() or p_ends_at > now() + interval '90 days'
        or (p_university_id is not null and not exists (select 1 from public.universities where id = p_university_id)) then
        raise exception using errcode = 'P0001', message = 'invalid_announcement';
    end if;
    insert into public.announcements (title, body, university_id, ends_at, created_by)
    values (btrim(p_title), btrim(p_body), p_university_id, p_ends_at, auth.uid())
    returning id into v_id;
    return v_id;
end;
$$;

-- Ends an announcement now; it disappears from the app at once.
create function public.admin_end_announcement(p_announcement_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    update public.announcements
    set ends_at = now(), starts_at = least(starts_at, now() - interval '1 second')
    where id = p_announcement_id and ends_at > now();
    if not found then
        raise exception using errcode = 'P0001', message = 'announcement_not_found';
    end if;
end;
$$;

create function public.admin_list_announcements()
returns table (
    id uuid,
    title text,
    body text,
    university_name text,
    starts_at timestamptz,
    ends_at timestamptz,
    dismissed_count bigint,
    created_at timestamptz
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
    select a.id, a.title, a.body, u.name, a.starts_at, a.ends_at,
           (select count(*) from public.announcement_dismissals d where d.announcement_id = a.id), a.created_at
    from public.announcements a
    left join public.universities u on u.id = a.university_id
    order by a.created_at desc
    limit 100;
end;
$$;

-- Universities for the announcement form.
create function public.admin_list_universities()
returns table (id uuid, name text, city text)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    return query select u.id, u.name, u.city from public.universities u order by u.name;
end;
$$;

alter publication supabase_realtime add table public.announcements;

-- Grants --------------------------------------------------------------------------------------

do $$
declare
    f text;
begin
    foreach f in array array[
        'public.my_subscription()',
        'public.redeem_promo_code(text)',
        'public.touch_activity()',
        'public.active_announcements()',
        'public.dismiss_announcement(uuid)',
        'public.admin_list_plans()',
        'public.admin_grant_premium(uuid, uuid, integer, text)',
        'public.admin_revoke_grant(uuid)',
        'public.admin_list_grants(integer)',
        'public.admin_create_promo_code(text, uuid, integer, integer, timestamptz)',
        'public.admin_list_promo_codes()',
        'public.admin_disable_promo_code(uuid)',
        'public.admin_activity_stats()',
        'public.admin_create_announcement(text, text, uuid, timestamptz)',
        'public.admin_end_announcement(uuid)',
        'public.admin_list_announcements()',
        'public.admin_list_universities()'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;
    foreach f in array array[
        'public.active_premium(uuid)',
        'public.insert_premium_grant(uuid, uuid, integer, text, uuid, text)'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated', f);
    end loop;
end;
$$;
