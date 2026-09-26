-- Test-only reproduction of the parts of a Supabase database that the
-- migrations depend on: the API roles, their default privileges, the auth
-- schema with auth.users and auth.uid(). Used by scripts/test-db.sh on a
-- throwaway local PostgreSQL database; never applied to a real project.

create role anon nologin noinherit;
create role authenticated nologin noinherit;
create role service_role nologin noinherit bypassrls;

create extension if not exists pgcrypto;

create schema auth;
grant usage on schema auth to anon, authenticated, service_role;

create table auth.users (
    id uuid primary key default gen_random_uuid(),
    email text,
    raw_app_meta_data jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create function auth.uid() returns uuid
language sql stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')
    )::uuid
$$;

create function auth.jwt() returns jsonb
language sql stable
as $$
    select coalesce(nullif(current_setting('request.jwt.claims', true), ''), '{}')::jsonb
$$;

-- Supabase grants broad table and function privileges to the API roles and
-- relies on RLS and explicit REVOKEs. Reproduce that so the tests exercise
-- the same protection the real project has.
grant usage on schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;
alter default privileges in schema public grant execute on functions to anon, authenticated, service_role;

-- Storage: the columns and helper the migrations use. Real Supabase projects
-- have more columns; RLS on storage.objects is enabled there as well.
create schema storage;
grant usage on schema storage to anon, authenticated, service_role;

create table storage.buckets (
    id text primary key,
    name text not null unique,
    public boolean not null default false,
    file_size_limit bigint,
    allowed_mime_types text[]
);

create table storage.objects (
    id uuid primary key default gen_random_uuid(),
    bucket_id text references storage.buckets (id),
    name text not null,
    owner uuid,
    metadata jsonb,
    created_at timestamptz not null default now(),
    unique (bucket_id, name)
);

alter table storage.objects enable row level security;
grant all on storage.objects to anon, authenticated, service_role;
grant select on storage.buckets to anon, authenticated, service_role;

create function storage.foldername(name text) returns text[]
language sql immutable
as $$
    select (string_to_array(name, '/'))[1:array_length(string_to_array(name, '/'), 1) - 1]
$$;
