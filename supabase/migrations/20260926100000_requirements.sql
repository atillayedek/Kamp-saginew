-- Requirements ("ihtiyaç"): a student's need, structured by OpenAI on the
-- backend and stored with an OpenAI embedding in pgvector for matching.
--
-- Rows are written only by the `publish-requirement` Edge Function with the
-- service role (it produces the embedding); the app reads and closes its own
-- requirements through the functions below. AI calls are metered per person in
-- ai_usage so a single account cannot run up the OpenAI bill.

create schema if not exists extensions;
create extension if not exists vector with schema extensions;

create type public.requirement_category as enum (
    'SPORTS', 'STUDY', 'PROJECT', 'TRANSPORT', 'ITEM', 'EVENT', 'HOUSING', 'OTHER'
);

create type public.requirement_status as enum ('ACTIVE', 'CLOSED');

-- Must match the embedding model's output size (text-embedding-3-small: 1536).
create table public.requirements (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references public.profiles (id) on delete cascade,
    university_id uuid not null references public.universities (id) on delete restrict,
    original_text text not null check (char_length(btrim(original_text)) between 10 and 1000),
    title text not null check (char_length(btrim(title)) between 3 and 120),
    description text not null check (char_length(btrim(description)) between 1 and 1000),
    category public.requirement_category not null,
    tags text[] not null default '{}' check (cardinality(tags) <= 8),
    location_text text check (location_text is null or char_length(btrim(location_text)) between 1 and 120),
    starts_at timestamptz,
    participants_needed integer check (participants_needed is null or participants_needed between 1 and 50),
    embedding extensions.vector(1536) not null,
    embedding_model text not null check (char_length(embedding_model) between 1 and 100),
    status public.requirement_status not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    closed_at timestamptz,
    constraint requirements_closed_at_matches_status check ((status = 'CLOSED') = (closed_at is not null))
);

create index requirements_owner_created_idx on public.requirements (owner_id, created_at desc);
create index requirements_active_university_idx on public.requirements (university_id) where status = 'ACTIVE';
create index requirements_embedding_idx on public.requirements
    using hnsw (embedding extensions.vector_cosine_ops) where status = 'ACTIVE';

alter table public.requirements enable row level security;

-- Owners read their own rows directly; everything else goes through functions.
create policy requirements_select_own on public.requirements
    for select to authenticated
    using (owner_id = (select auth.uid()));

-- AI metering -----------------------------------------------------------------

create table public.ai_usage (
    id bigint generated always as identity primary key,
    user_id uuid not null references public.profiles (id) on delete cascade,
    kind text not null check (kind in ('analyze', 'publish')),
    created_at timestamptz not null default now()
);

create index ai_usage_user_kind_created_idx on public.ai_usage (user_id, kind, created_at desc);

alter table public.ai_usage enable row level security;
-- No policies: only the service role touches this table.

-- Records one AI call for an approved student, refusing it over the daily limit.
create function public.consume_ai_quota(p_user_id uuid, p_kind text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_limit integer := case p_kind when 'analyze' then 30 when 'publish' then 10 end;
    v_used integer;
begin
    if v_limit is null then
        raise exception using errcode = 'P0001', message = 'invalid_quota_kind';
    end if;
    if not exists (select 1 from public.profiles where id = p_user_id and account_status = 'APPROVED') then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    -- Serialise concurrent calls of the same person so the limit cannot be raced.
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

revoke all on function public.consume_ai_quota(uuid, text) from public, anon, authenticated;
grant execute on function public.consume_ai_quota(uuid, text) to service_role;

-- insert_requirement (service role only) ------------------------------------------

create function public.insert_requirement(
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

    -- Normalise tags: trimmed, lower-case, unique, non-empty, at most 30 characters.
    select coalesce(array_agg(distinct t order by t), '{}') into v_tags
    from (select lower(btrim(x)) as t from unnest(coalesce(p_tags, '{}')) x) s
    where t <> '' and char_length(t) <= 30;
    if cardinality(v_tags) > 8 then
        raise exception using errcode = 'P0001', message = 'invalid_requirement';
    end if;

    if (select count(*) from public.requirements where owner_id = p_user_id and status = 'ACTIVE') >= 20 then
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

revoke all on function public.insert_requirement(uuid, text, text, text, public.requirement_category, text[], text, timestamptz, integer, text, text)
    from public, anon, authenticated;
grant execute on function public.insert_requirement(uuid, text, text, text, public.requirement_category, text[], text, timestamptz, integer, text, text)
    to service_role;

-- App API -----------------------------------------------------------------------------

create function public.list_my_requirements()
returns table (
    id uuid,
    title text,
    description text,
    category public.requirement_category,
    tags text[],
    location_text text,
    starts_at timestamptz,
    participants_needed integer,
    status public.requirement_status,
    created_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    return query
    select r.id, r.title, r.description, r.category, r.tags, r.location_text, r.starts_at,
           r.participants_needed, r.status, r.created_at
    from public.requirements r
    where r.owner_id = auth.uid()
    order by (r.status = 'ACTIVE') desc, r.created_at desc
    limit 100;
end;
$$;

create function public.close_requirement(p_requirement_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    update public.requirements
    set status = 'CLOSED', closed_at = now()
    where id = p_requirement_id and owner_id = auth.uid() and status = 'ACTIVE';
    if not found then
        raise exception using errcode = 'P0001', message = 'requirement_not_found';
    end if;
end;
$$;

revoke all on function public.list_my_requirements() from public, anon;
grant execute on function public.list_my_requirements() to authenticated;
revoke all on function public.close_requirement(uuid) from public, anon;
grant execute on function public.close_requirement(uuid) to authenticated;
