-- Push notifications through Firebase Cloud Messaging again (user decision,
-- 2026-10-01, D55; reverses D30).
--
-- Every signed-in device registers its FCM token. A Supabase Database Webhook
-- on INSERT into public.notifications calls the dispatch-push Edge Function,
-- which reads push_payload() with the service role and sends through FCM.
-- An admin can also send an in-app announcement to phones once (admin-push).

-- Device tokens ---------------------------------------------------------------------------

create table public.device_tokens (
    token text primary key check (char_length(token) between 20 and 4096),
    user_id uuid not null references public.profiles (id) on delete cascade,
    platform text not null default 'android' check (platform in ('android')),
    updated_at timestamptz not null default now()
);

create index device_tokens_user_idx on public.device_tokens (user_id);

alter table public.device_tokens enable row level security;
-- No policies: managed through the functions below and the service role.

-- A token belongs to whoever registered it last on that device.
create function public.register_device_token(p_token text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;
    if p_token is null or char_length(p_token) not between 20 and 4096 then
        raise exception using errcode = 'P0001', message = 'invalid_token';
    end if;
    insert into public.device_tokens (token, user_id) values (p_token, auth.uid())
    on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
end;
$$;

create function public.unregister_device_token(p_token text)
returns void
language sql
security definer
set search_path = ''
as $$
    delete from public.device_tokens where token = p_token and user_id = auth.uid()
$$;

-- Everything the push sender needs for one notification (service role only).
create function public.push_payload(p_notification_id uuid)
returns table (
    kind public.notification_kind,
    actor_name text,
    conversation_id uuid,
    post_id uuid,
    tokens text[]
)
language sql
stable
security definer
set search_path = ''
as $$
    select n.kind, coalesce(a.full_name, a.username), n.conversation_id, n.post_id,
           coalesce((select array_agg(t.token) from public.device_tokens t where t.user_id = n.user_id), '{}')
    from public.notifications n
    left join public.profiles a on a.id = n.actor_id
    where n.id = p_notification_id and n.read_at is null
$$;

create function public.delete_device_tokens(p_tokens text[])
returns void
language sql
security definer
set search_path = ''
as $$
    delete from public.device_tokens where token = any (p_tokens)
$$;

-- Announcements to phones -------------------------------------------------------------------

alter table public.announcements
    add column pushed_at timestamptz,
    add column push_sent integer check (push_sent is null or push_sent >= 0);

-- Claims an active announcement for one push (a second claim fails) and returns
-- its text with the devices of the approved students it is meant for.
create function public.claim_announcement_push(p_announcement_id uuid)
returns table (title text, body text, tokens text[])
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_announcement public.announcements%rowtype;
begin
    update public.announcements a
    set pushed_at = now()
    where a.id = p_announcement_id and a.pushed_at is null and a.ends_at > now()
    returning a.* into v_announcement;
    if not found then
        if exists (select 1 from public.announcements a where a.id = p_announcement_id and a.pushed_at is not null) then
            raise exception using errcode = 'P0001', message = 'announcement_already_pushed';
        end if;
        raise exception using errcode = 'P0001', message = 'announcement_not_found';
    end if;

    return query
    select v_announcement.title, v_announcement.body,
           coalesce(array_agg(t.token), '{}')
    from public.device_tokens t
    join public.profiles p on p.id = t.user_id
    where p.account_status = 'APPROVED'
      and (v_announcement.university_id is null or p.university_id = v_announcement.university_id);
end;
$$;

create function public.record_announcement_push(p_announcement_id uuid, p_sent integer)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.announcements set push_sent = greatest(p_sent, 0) where id = p_announcement_id
$$;

drop function public.admin_list_announcements();

create function public.admin_list_announcements()
returns table (
    id uuid,
    title text,
    body text,
    university_name text,
    starts_at timestamptz,
    ends_at timestamptz,
    dismissed_count bigint,
    created_at timestamptz,
    pushed_at timestamptz,
    push_sent integer
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
           (select count(*) from public.announcement_dismissals d where d.announcement_id = a.id), a.created_at,
           a.pushed_at, a.push_sent
    from public.announcements a
    left join public.universities u on u.id = a.university_id
    order by a.created_at desc
    limit 100;
end;
$$;

-- Grants --------------------------------------------------------------------------------------

do $$
declare
    f text;
begin
    foreach f in array array[
        'public.register_device_token(text)',
        'public.unregister_device_token(text)',
        'public.admin_list_announcements()'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;
    foreach f in array array[
        'public.push_payload(uuid)',
        'public.delete_device_tokens(text[])',
        'public.claim_announcement_push(uuid)',
        'public.record_announcement_push(uuid, integer)'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated', f);
        execute format('grant execute on function %s to service_role', f);
    end loop;
end;
$$;
