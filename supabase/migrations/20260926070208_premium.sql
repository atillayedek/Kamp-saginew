-- Premium: Google Play subscriptions verified on the server.
--
-- Plans are data. Each plan's benefits are limits the database enforces
-- (AI quota, active requirements); the client never decides what a person is
-- entitled to. An entitlement is written only by the `verify-purchase` Edge
-- Function after Google Play confirms the purchase token for this account.
-- No plan is inserted here: plans, product ids and benefits are the product
-- owner's decision (docs/DEPLOYMENT.md). With no active plan the app says so.

create table public.subscription_plans (
    id uuid primary key default gen_random_uuid(),
    play_product_id text not null unique check (play_product_id ~ '^[a-z0-9._]{1,100}$'),
    name text not null check (char_length(btrim(name)) between 2 and 60),
    description text not null check (char_length(btrim(description)) between 2 and 500),
    is_active boolean not null default false,
    ai_analyze_daily integer not null check (ai_analyze_daily between 1 and 1000),
    ai_publish_daily integer not null check (ai_publish_daily between 1 and 1000),
    max_active_requirements integer not null check (max_active_requirements between 1 and 500),
    sort_order integer not null default 0,
    created_at timestamptz not null default now()
);

alter table public.subscription_plans enable row level security;
-- Read through list_plans(); managed with the service role.

create table public.entitlements (
    user_id uuid primary key references public.profiles (id) on delete cascade,
    plan_id uuid not null references public.subscription_plans (id) on delete restrict,
    -- One Google Play purchase can back one account only (no token replay).
    purchase_token text not null unique check (char_length(purchase_token) between 10 and 4096),
    expires_at timestamptz not null,
    updated_at timestamptz not null default now()
);

create index entitlements_plan_idx on public.entitlements (plan_id);

alter table public.entitlements enable row level security;

create policy entitlements_select_own on public.entitlements
    for select to authenticated
    using (user_id = (select auth.uid()));

-- Limits ----------------------------------------------------------------------------

-- Free limits unless the person has a current entitlement to an active plan.
create function public.current_limits(p_user_id uuid)
returns table (ai_analyze_daily integer, ai_publish_daily integer, max_active_requirements integer)
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(p.ai_analyze_daily, 30), coalesce(p.ai_publish_daily, 10), coalesce(p.max_active_requirements, 20)
    from (select 1) one
    left join public.entitlements e on e.user_id = p_user_id and e.expires_at > now()
    left join public.subscription_plans p on p.id = e.plan_id and p.is_active
$$;

revoke all on function public.current_limits(uuid) from public, anon, authenticated;

create or replace function public.consume_ai_quota(p_user_id uuid, p_kind text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_limit integer;
    v_used integer;
begin
    if p_kind not in ('analyze', 'publish') then
        raise exception using errcode = 'P0001', message = 'invalid_quota_kind';
    end if;
    if not exists (select 1 from public.profiles where id = p_user_id and account_status = 'APPROVED') then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    select case p_kind when 'analyze' then l.ai_analyze_daily else l.ai_publish_daily end
    into v_limit
    from public.current_limits(p_user_id) l;

    perform pg_advisory_xact_lock(hashtextextended(p_user_id::text || p_kind, 0));
    select count(*) into v_used
    from public.ai_usage
    where user_id = p_user_id and kind = p_kind and created_at > now() - interval '24 hours';
    if v_used >= v_limit then
        raise exception using errcode = 'P0001', message = 'ai_quota_exceeded';
    end if;
    insert into public.ai_usage (user_id, kind) values (p_user_id, p_kind);
end;
$$;

-- The active-requirement limit now follows the plan as well.
create or replace function public.insert_requirement(
    p_user_id uuid,
    p_original_text text,
    p_title text,
    p_description text,
    p_category public.requirement_category,
    p_tags text[],
    p_location_text text,
    p_starts_at timestamptz,
    p_participants_needed integer,
    p_embedding text,
    p_embedding_model text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_university uuid;
    v_tags text[];
    v_id uuid;
begin
    select university_id into v_university
    from public.profiles
    where id = p_user_id and account_status = 'APPROVED';
    if not found or v_university is null then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;

    select coalesce(array_agg(distinct t order by t), '{}') into v_tags
    from (select lower(btrim(x)) as t from unnest(coalesce(p_tags, '{}')) x) s
    where t <> '' and char_length(t) <= 30;
    if cardinality(v_tags) > 8 then
        raise exception using errcode = 'P0001', message = 'invalid_requirement';
    end if;

    if (select count(*) from public.requirements where owner_id = p_user_id and status = 'ACTIVE')
        >= (select l.max_active_requirements from public.current_limits(p_user_id) l) then
        raise exception using errcode = 'P0001', message = 'too_many_active_requirements';
    end if;

    insert into public.requirements (
        owner_id, university_id, original_text, title, description, category, tags,
        location_text, starts_at, participants_needed, embedding, embedding_model
    ) values (
        p_user_id, v_university, btrim(p_original_text), btrim(p_title), btrim(p_description), p_category, v_tags,
        nullif(btrim(p_location_text), ''), p_starts_at, p_participants_needed,
        p_embedding::extensions.vector, p_embedding_model
    )
    returning id into v_id;
    return v_id;
exception
    when check_violation or not_null_violation or invalid_text_representation or data_exception then
        raise exception using errcode = 'P0001', message = 'invalid_requirement';
end;
$$;

-- Entitlements (service role only) ---------------------------------------------------

create function public.active_plan_for_product(p_product_id text)
returns uuid
language sql
stable
security definer
set search_path = ''
as $$
    select id from public.subscription_plans where play_product_id = p_product_id and is_active
$$;

create function public.record_entitlement(
    p_user_id uuid,
    p_product_id text,
    p_purchase_token text,
    p_expires_at timestamptz
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_plan uuid := public.active_plan_for_product(p_product_id);
    v_owner uuid;
begin
    if v_plan is null then
        raise exception using errcode = 'P0001', message = 'plan_not_available';
    end if;
    if p_expires_at is null or p_expires_at <= now() then
        raise exception using errcode = 'P0001', message = 'purchase_not_active';
    end if;
    select user_id into v_owner from public.entitlements where purchase_token = p_purchase_token;
    if v_owner is not null and v_owner <> p_user_id then
        raise exception using errcode = 'P0001', message = 'purchase_belongs_to_another_account';
    end if;

    insert into public.entitlements (user_id, plan_id, purchase_token, expires_at)
    values (p_user_id, v_plan, p_purchase_token, p_expires_at)
    on conflict (user_id) do update
        set plan_id = excluded.plan_id,
            purchase_token = excluded.purchase_token,
            expires_at = excluded.expires_at,
            updated_at = now();
end;
$$;

-- App API ----------------------------------------------------------------------------------

create function public.list_plans()
returns table (
    id uuid,
    play_product_id text,
    name text,
    description text,
    ai_analyze_daily integer,
    ai_publish_daily integer,
    max_active_requirements integer
)
language sql
stable
security definer
set search_path = ''
as $$
    select p.id, p.play_product_id, p.name, p.description, p.ai_analyze_daily, p.ai_publish_daily, p.max_active_requirements
    from public.subscription_plans p
    where p.is_active and exists (select 1 from public.current_student())
    order by p.sort_order, p.name
$$;

create function public.my_subscription()
returns table (
    plan_name text,
    play_product_id text,
    expires_at timestamptz,
    ai_analyze_daily integer,
    ai_publish_daily integer,
    max_active_requirements integer
)
language sql
stable
security definer
set search_path = ''
as $$
    select p.name, p.play_product_id, e.expires_at, l.ai_analyze_daily, l.ai_publish_daily, l.max_active_requirements
    from public.current_limits(auth.uid()) l
    left join public.entitlements e on e.user_id = auth.uid() and e.expires_at > now()
    left join public.subscription_plans p on p.id = e.plan_id and p.is_active
    where exists (select 1 from public.current_student())
$$;

do $$
declare
    f text;
begin
    foreach f in array array['public.list_plans()', 'public.my_subscription()'] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;
    foreach f in array array[
        'public.active_plan_for_product(text)',
        'public.record_entitlement(uuid, text, text, timestamptz)'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated', f);
        execute format('grant execute on function %s to service_role', f);
    end loop;
end;
$$;
