-- KampüsAğı foundation: universities, profiles and the account lifecycle.
--
-- Every write to a profile goes through a SECURITY DEFINER function so the
-- client can never change privileged fields (account_status, email, id).
-- Errors raised for the client use SQLSTATE P0001 and a stable snake_case
-- message that the Android app maps to a domain error.

create type public.account_status as enum (
    'PROFILE_INCOMPLETE', -- signed up, profile form not filled yet
    'DOCUMENT_REQUIRED',  -- profile filled, student document not uploaded yet
    'PENDING_REVIEW',     -- document uploaded, waiting for an admin
    'APPROVED',           -- verified student
    'REJECTED',           -- document rejected by an admin
    'SUSPENDED'           -- blocked by an admin
);

create function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

-- Universities ---------------------------------------------------------------
-- Managed by admins with the service role. The app only reads active rows.

create table public.universities (
    id uuid primary key default gen_random_uuid(),
    name text not null unique check (char_length(btrim(name)) between 2 and 200),
    city text not null check (char_length(btrim(city)) between 2 and 100),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index universities_active_name_idx on public.universities (name) where is_active;

create trigger universities_set_updated_at
    before update on public.universities
    for each row execute function public.set_updated_at();

alter table public.universities enable row level security;

create policy universities_select_active on public.universities
    for select to authenticated
    using (is_active);

-- Profiles -------------------------------------------------------------------

create table public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    email text not null,
    full_name text check (full_name is null or char_length(btrim(full_name)) between 2 and 100),
    username text unique check (username is null or username ~ '^[a-z0-9_.]{3,30}$'),
    university_id uuid references public.universities (id) on delete restrict,
    department text check (department is null or char_length(btrim(department)) between 2 and 120),
    account_status public.account_status not null default 'PROFILE_INCOMPLETE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index profiles_university_id_idx on public.profiles (university_id);
create index profiles_account_status_idx on public.profiles (account_status);

create trigger profiles_set_updated_at
    before update on public.profiles
    for each row execute function public.set_updated_at();

alter table public.profiles enable row level security;

-- A person always reads their own profile, whatever its status. Reading other
-- people's profiles is added together with the features that need it.
create policy profiles_select_own on public.profiles
    for select to authenticated
    using (id = (select auth.uid()));

-- No INSERT/UPDATE/DELETE policies: rows are created by the auth trigger and
-- changed only through the functions below.

create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, email)
    values (new.id, coalesce(new.email, ''));
    return new;
end;
$$;

revoke all on function public.handle_new_user() from public;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- complete_profile -------------------------------------------------------------
-- Allowed while the account is PROFILE_INCOMPLETE or DOCUMENT_REQUIRED. Once a
-- document is under review (or approved) the university and identity fields
-- are locked, because the verification was made against them.

create function public.complete_profile(
    p_full_name text,
    p_username text,
    p_university_id uuid,
    p_department text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_status public.account_status;
    v_full_name text := btrim(coalesce(p_full_name, ''));
    v_username text := lower(btrim(coalesce(p_username, '')));
    v_department text := btrim(coalesce(p_department, ''));
begin
    if v_uid is null then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;

    select account_status into v_status
    from public.profiles
    where id = v_uid
    for update;

    if not found then
        raise exception using errcode = 'P0001', message = 'profile_not_found';
    end if;

    if v_status not in ('PROFILE_INCOMPLETE', 'DOCUMENT_REQUIRED') then
        raise exception using errcode = 'P0001', message = 'profile_locked';
    end if;

    if char_length(v_full_name) not between 2 and 100 then
        raise exception using errcode = 'P0001', message = 'invalid_full_name';
    end if;

    if v_username !~ '^[a-z0-9_.]{3,30}$' then
        raise exception using errcode = 'P0001', message = 'invalid_username';
    end if;

    if char_length(v_department) not between 2 and 120 then
        raise exception using errcode = 'P0001', message = 'invalid_department';
    end if;

    if p_university_id is null or not exists (
        select 1 from public.universities where id = p_university_id and is_active
    ) then
        raise exception using errcode = 'P0001', message = 'university_not_found';
    end if;

    if exists (select 1 from public.profiles where username = v_username and id <> v_uid) then
        raise exception using errcode = 'P0001', message = 'username_taken';
    end if;

    update public.profiles
    set full_name = v_full_name,
        username = v_username,
        university_id = p_university_id,
        department = v_department,
        account_status = 'DOCUMENT_REQUIRED'
    where id = v_uid;
exception
    -- Two people racing for the same username: the unique index decides.
    when unique_violation then
        raise exception using errcode = 'P0001', message = 'username_taken';
end;
$$;

revoke all on function public.complete_profile(text, text, uuid, text) from public, anon;
grant execute on function public.complete_profile(text, text, uuid, text) to authenticated;
