-- Notifications: created by the database from real events, shown in the app
-- and delivered as push through FCM by the `dispatch-push` Edge Function
-- (called by a Supabase Database Webhook on INSERT into public.notifications).
--
-- Events
--   NEW_MESSAGE            a chat message to you (one unread row per conversation,
--                          refreshed instead of piling up)
--   NEW_COMMENT            someone commented on your post
--   VERIFICATION_APPROVED  an admin approved your student document
--   VERIFICATION_REJECTED  an admin rejected it

create type public.notification_kind as enum (
    'NEW_MESSAGE', 'NEW_COMMENT', 'VERIFICATION_APPROVED', 'VERIFICATION_REJECTED'
);

create table public.notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    kind public.notification_kind not null,
    actor_id uuid references public.profiles (id) on delete cascade,
    conversation_id uuid references public.conversations (id) on delete cascade,
    post_id uuid references public.posts (id) on delete cascade,
    created_at timestamptz not null default now(),
    read_at timestamptz
);

create index notifications_user_created_idx on public.notifications (user_id, created_at desc);
create index notifications_user_unread_idx on public.notifications (user_id) where read_at is null;
create index notifications_actor_idx on public.notifications (actor_id);
create index notifications_conversation_idx on public.notifications (conversation_id);
create index notifications_post_idx on public.notifications (post_id);
-- At most one unread message notification per conversation and person.
create unique index notifications_unread_message_idx
    on public.notifications (user_id, conversation_id) where kind = 'NEW_MESSAGE' and read_at is null;

alter table public.notifications enable row level security;

create policy notifications_select_own on public.notifications
    for select to authenticated
    using (user_id = (select auth.uid()));

-- Device tokens for FCM ---------------------------------------------------------------

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

-- Triggers that create notifications ---------------------------------------------------

create function public.notify_new_message()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.notifications (user_id, kind, actor_id, conversation_id)
    select m.user_id, 'NEW_MESSAGE', new.sender_id, new.conversation_id
    from public.conversation_members m
    where m.conversation_id = new.conversation_id and m.user_id <> new.sender_id
    on conflict (user_id, conversation_id) where kind = 'NEW_MESSAGE' and read_at is null
    do update set created_at = now(), actor_id = excluded.actor_id;
    return null;
end;
$$;

create trigger messages_notify
    after insert on public.messages
    for each row execute function public.notify_new_message();

create function public.notify_new_comment()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.notifications (user_id, kind, actor_id, post_id)
    select p.author_id, 'NEW_COMMENT', new.author_id, new.post_id
    from public.posts p
    where p.id = new.post_id and p.author_id <> new.author_id;
    return null;
end;
$$;

create trigger comments_notify
    after insert on public.comments
    for each row execute function public.notify_new_comment();

create function public.notify_verification_reviewed()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if old.status = 'PENDING' and new.status in ('APPROVED', 'REJECTED') then
        insert into public.notifications (user_id, kind)
        values (
            new.user_id,
            case when new.status = 'APPROVED' then 'VERIFICATION_APPROVED'::public.notification_kind
                 else 'VERIFICATION_REJECTED' end
        );
    end if;
    return null;
end;
$$;

create trigger student_verifications_notify
    after update of status on public.student_verifications
    for each row execute function public.notify_verification_reviewed();

-- Reading a conversation clears its message notification.
create function public.clear_message_notification()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.last_read_at is distinct from old.last_read_at then
        update public.notifications
        set read_at = now()
        where user_id = new.user_id and conversation_id = new.conversation_id
          and kind = 'NEW_MESSAGE' and read_at is null;
    end if;
    return null;
end;
$$;

create trigger conversation_members_clear_notification
    after update of last_read_at on public.conversation_members
    for each row execute function public.clear_message_notification();

revoke all on function public.notify_new_message() from public, anon, authenticated;
revoke all on function public.notify_new_comment() from public, anon, authenticated;
revoke all on function public.notify_verification_reviewed() from public, anon, authenticated;
revoke all on function public.clear_message_notification() from public, anon, authenticated;

-- App API -----------------------------------------------------------------------------

create function public.list_notifications(p_limit integer default 50)
returns table (
    id uuid,
    kind public.notification_kind,
    created_at timestamptz,
    read_at timestamptz,
    actor_full_name text,
    actor_username text,
    conversation_id uuid,
    post_id uuid
)
language sql
stable
security definer
set search_path = ''
as $$
    select n.id, n.kind, n.created_at, n.read_at, a.full_name, a.username, n.conversation_id, n.post_id
    from public.notifications n
    left join public.profiles a on a.id = n.actor_id
    where n.user_id = auth.uid()
    order by n.created_at desc
    limit least(greatest(coalesce(p_limit, 50), 1), 100)
$$;

create function public.mark_notifications_read(p_ids uuid[] default null)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.notifications
    set read_at = now()
    where user_id = auth.uid() and read_at is null and (p_ids is null or id = any (p_ids))
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

do $$
declare
    f text;
begin
    foreach f in array array[
        'public.register_device_token(text)',
        'public.unregister_device_token(text)',
        'public.list_notifications(integer)',
        'public.mark_notifications_read(uuid[])'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;
    foreach f in array array['public.push_payload(uuid)', 'public.delete_device_tokens(text[])'] loop
        execute format('revoke all on function %s from public, anon, authenticated', f);
        execute format('grant execute on function %s to service_role', f);
    end loop;
end;
$$;

-- In-app list updates live, like chat.
alter publication supabase_realtime add table public.notifications;
