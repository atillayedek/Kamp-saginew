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
