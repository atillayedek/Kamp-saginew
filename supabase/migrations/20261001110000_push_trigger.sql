-- Push without manual webhook setup (D56).
--
-- The database sends each new notification to the dispatch-push Edge Function
-- itself through pg_net. The shared secret that proves the call comes from the
-- database is generated here and never leaves the server: the trigger sends it,
-- dispatch-push reads it back with the service role. The functions URL is
-- recorded once by an admin from the panel (admin-push {"action": "setup"}),
-- because the database cannot know its own public address.
-- Without pg_net (local test database) or before setup, nothing is sent and
-- notifications still appear in the app.

do $$
begin
    if exists (select 1 from pg_available_extensions where name = 'pg_net') then
        create extension if not exists pg_net;
    end if;
end;
$$;

create table public.push_settings (
    id boolean primary key default true check (id),
    webhook_secret text not null
        default encode(sha256(convert_to(gen_random_uuid()::text || gen_random_uuid()::text, 'UTF8')), 'hex'),
    functions_url text check (functions_url ~ '^https://[a-z0-9-]+\.supabase\.co/functions/v1$'),
    updated_at timestamptz not null default now()
);

insert into public.push_settings default values;

alter table public.push_settings enable row level security;
-- No policies and no grants: only security-definer functions read it.
revoke all on table public.push_settings from public, anon, authenticated;

-- Service role only: dispatch-push compares the request header with this.
create function public.push_webhook_secret()
returns text
language sql
stable
security definer
set search_path = ''
as $$
    select webhook_secret from public.push_settings
$$;

-- Service role only: called by admin-push with its own SUPABASE_URL.
create function public.set_push_functions_url(p_url text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_url is null or p_url !~ '^https://[a-z0-9-]+\.supabase\.co/functions/v1$' then
        raise exception using errcode = 'P0001', message = 'invalid_functions_url';
    end if;
    update public.push_settings set functions_url = p_url, updated_at = now();
end;
$$;

-- Service role only: what the admin panel shows.
create function public.push_status()
returns table (trigger_ready boolean, pg_net_installed boolean)
language sql
stable
security definer
set search_path = ''
as $$
    select s.functions_url is not null and exists (select 1 from pg_extension where extname = 'pg_net'),
           exists (select 1 from pg_extension where extname = 'pg_net')
    from public.push_settings s
$$;

create function public.dispatch_notification_push()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_settings public.push_settings%rowtype;
begin
    select * into v_settings from public.push_settings;
    if v_settings.functions_url is null or not exists (select 1 from pg_extension where extname = 'pg_net') then
        return new;
    end if;
    -- Dynamic so this function also compiles where pg_net is not installed.
    execute 'select net.http_post(url := $1, body := $2, headers := $3)'
    using v_settings.functions_url || '/dispatch-push',
          jsonb_build_object('type', 'INSERT', 'table', 'notifications', 'record', jsonb_build_object('id', new.id)),
          jsonb_build_object('Content-Type', 'application/json', 'x-webhook-secret', v_settings.webhook_secret);
    return new;
exception
    when others then
        -- A push problem must never block the notification itself; it stays visible in the app.
        raise warning 'push dispatch for notification % not queued: %', new.id, sqlerrm;
        return new;
end;
$$;

create trigger notifications_dispatch_push
    after insert on public.notifications
    for each row execute function public.dispatch_notification_push();

do $$
declare
    f text;
begin
    foreach f in array array[
        'public.push_webhook_secret()',
        'public.set_push_functions_url(text)',
        'public.push_status()'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated', f);
        execute format('grant execute on function %s to service_role', f);
    end loop;
    execute 'revoke all on function public.dispatch_notification_push() from public, anon, authenticated';
end;
$$;
