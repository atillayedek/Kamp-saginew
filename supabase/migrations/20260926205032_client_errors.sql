-- Crash reports from the Android app (D31). Crashlytics is part of Firebase,
-- which is not used (D30), so fatal crashes are stored here instead. The app
-- writes a crash to local storage and sends it once someone is signed in.
-- Reports are read in the Supabase Dashboard (service role); clients can
-- neither read nor edit them. A report goes with its account on deletion.

create table public.client_errors (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    app_version text not null check (char_length(app_version) between 1 and 40),
    android_sdk integer check (android_sdk is null or android_sdk between 1 and 100),
    device_model text check (device_model is null or char_length(device_model) <= 100),
    exception_type text not null check (char_length(exception_type) between 1 and 300),
    message text check (message is null or char_length(message) <= 1000),
    stacktrace text not null check (char_length(stacktrace) between 1 and 16000),
    occurred_at timestamptz not null,
    created_at timestamptz not null default now()
);

create index client_errors_user_created_idx on public.client_errors (user_id, created_at desc);
create index client_errors_created_idx on public.client_errors (created_at desc);

alter table public.client_errors enable row level security;
-- No policies: written through report_client_error(), read with the service role.

create function public.report_client_error(
    p_app_version text,
    p_android_sdk integer,
    p_device_model text,
    p_exception_type text,
    p_message text,
    p_stacktrace text,
    p_occurred_at timestamptz
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null or not exists (select 1 from public.profiles where id = auth.uid()) then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;
    if nullif(btrim(coalesce(p_app_version, '')), '') is null
        or nullif(btrim(coalesce(p_exception_type, '')), '') is null
        or nullif(btrim(coalesce(p_stacktrace, '')), '') is null
        or p_occurred_at is null
        or p_occurred_at > now() + interval '5 minutes'
        or p_occurred_at < now() - interval '30 days' then
        raise exception using errcode = 'P0001', message = 'invalid_report';
    end if;
    if (select count(*) from public.client_errors
        where user_id = auth.uid() and created_at > now() - interval '1 hour') >= 30 then
        raise exception using errcode = 'P0001', message = 'rate_limited';
    end if;

    insert into public.client_errors (
        user_id, app_version, android_sdk, device_model, exception_type, message, stacktrace, occurred_at
    ) values (
        auth.uid(),
        left(btrim(p_app_version), 40),
        case when p_android_sdk between 1 and 100 then p_android_sdk end,
        left(nullif(btrim(coalesce(p_device_model, '')), ''), 100),
        left(btrim(p_exception_type), 300),
        left(nullif(p_message, ''), 1000),
        left(p_stacktrace, 16000),
        p_occurred_at
    );
end;
$$;

revoke all on function public.report_client_error(text, integer, text, text, text, text, timestamptz)
    from public, anon;
grant execute on function public.report_client_error(text, integer, text, text, text, text, timestamptz)
    to authenticated;
