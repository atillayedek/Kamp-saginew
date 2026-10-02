-- Channels (Premium), study groups, course notes and badges.
--
-- A CHANNEL is a broadcast room like an Instagram channel: its owner and the
-- admins they choose post messages, photos and polls; members read, like and
-- vote. Only a Premium member can create a channel, and a channel can post
-- only while its owner's Premium is active. A STUDY_GROUP is a free group chat
-- for students of one university where every member writes.
--
-- Course notes are PDFs shared with the student's own university. The upload
-- is checked by the `submit-course-note` Edge Function (real PDF bytes, size)
-- before create_course_note() records it with the service role.

-- Premium --------------------------------------------------------------------------

-- Whether the person currently holds an active plan. Replaced when other
-- sources of Premium are added.
create function public.is_premium(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.entitlements e
        join public.subscription_plans p on p.id = e.plan_id and p.is_active
        where e.user_id = p_user_id and e.expires_at > now()
    )
$$;

create function public.am_i_premium()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select public.is_premium(auth.uid())
$$;

-- Groups and channels ----------------------------------------------------------------

create type public.group_kind as enum ('STUDY_GROUP', 'CHANNEL');
create type public.group_role as enum ('OWNER', 'ADMIN', 'MEMBER');

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('group-media', 'group-media', false, 3145728, array['image/jpeg'])
on conflict (id) do update
    set public = false,
        file_size_limit = excluded.file_size_limit,
        allowed_mime_types = excluded.allowed_mime_types;

create table public.groups (
    id uuid primary key default gen_random_uuid(),
    kind public.group_kind not null,
    name text not null check (char_length(btrim(name)) between 2 and 60),
    description text check (description is null or char_length(btrim(description)) between 1 and 500),
    course_code text check (course_code is null or course_code ~ '^[A-Z0-9]{2,12}$'),
    photo_path text check (photo_path is null or photo_path ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$'),
    owner_id uuid not null references public.profiles (id) on delete cascade,
    -- Study groups always belong to one university; a channel is either
    -- limited to its owner's university or open to every approved student.
    university_id uuid references public.universities (id) on delete restrict,
    member_count integer not null default 0 check (member_count >= 0),
    pinned_message_id uuid,
    last_message_at timestamptz,
    created_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint groups_study_group_has_university check (kind = 'CHANNEL' or university_id is not null)
);

create index groups_owner_idx on public.groups (owner_id);
create index groups_university_idx on public.groups (university_id);

create table public.group_members (
    group_id uuid not null references public.groups (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    role public.group_role not null default 'MEMBER',
    joined_at timestamptz not null default now(),
    last_read_at timestamptz,
    primary key (group_id, user_id)
);

create index group_members_user_idx on public.group_members (user_id);

create table public.group_messages (
    id uuid primary key,
    group_id uuid not null references public.groups (id) on delete cascade,
    sender_id uuid not null references public.profiles (id) on delete cascade,
    body text check (body is null or char_length(btrim(body)) between 1 and 2000),
    media_path text unique check (media_path is null or media_path ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$'),
    like_count integer not null default 0 check (like_count >= 0),
    created_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint group_messages_not_empty check (body is not null or media_path is not null)
);

create index group_messages_group_created_idx on public.group_messages (group_id, created_at desc, id desc);
create index group_messages_sender_idx on public.group_messages (sender_id);

alter table public.groups
    add constraint groups_pinned_message_fk foreign key (pinned_message_id)
    references public.group_messages (id) on delete set null;
create index groups_pinned_message_idx on public.groups (pinned_message_id);

create table public.group_message_likes (
    message_id uuid not null references public.group_messages (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (message_id, user_id)
);

create index group_message_likes_user_idx on public.group_message_likes (user_id);

-- Polls can now belong to a channel message instead of a post.
alter table public.polls add column group_message_id uuid unique references public.group_messages (id) on delete cascade;
alter table public.polls drop constraint polls_has_owner;
alter table public.polls add constraint polls_has_one_owner check (num_nonnulls(post_id, group_message_id) = 1);

create function public.maintain_group_counts()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if tg_table_name = 'group_members' then
        if tg_op = 'INSERT' then
            update public.groups set member_count = member_count + 1 where id = new.group_id;
        else
            update public.groups set member_count = greatest(member_count - 1, 0) where id = old.group_id;
        end if;
    elsif tg_op = 'INSERT' then
        update public.group_messages set like_count = like_count + 1 where id = new.message_id;
    else
        update public.group_messages set like_count = greatest(like_count - 1, 0) where id = old.message_id;
    end if;
    return null;
end;
$$;

create trigger group_members_count
    after insert or delete on public.group_members
    for each row execute function public.maintain_group_counts();

create trigger group_message_likes_count
    after insert or delete on public.group_message_likes
    for each row execute function public.maintain_group_counts();

-- Whether the caller may see a group: approved, not deleted, in reach of their
-- university and not separated from the owner by a block.
create function public.can_see_group(p_group_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.groups g
        join public.current_student() s on true
        where g.id = p_group_id
          and g.deleted_at is null
          and (g.university_id is null or g.university_id = s.university_id)
          and not public.is_blocked_with(g.owner_id)
    )
$$;

create function public.is_group_member(p_group_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select public.can_see_group(p_group_id) and exists (
        select 1 from public.group_members m where m.group_id = p_group_id and m.user_id = auth.uid()
    )
$$;

-- The caller's role in a group they can see, or null.
create function public.my_group_role(p_group_id uuid)
returns public.group_role
language sql
stable
security definer
set search_path = ''
as $$
    select m.role from public.group_members m
    where m.group_id = p_group_id and m.user_id = auth.uid() and public.can_see_group(p_group_id)
$$;

-- A channel posts only while its owner's Premium is active; study groups always can.
create function public.group_can_post(p_group_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select case g.kind
        when 'STUDY_GROUP' then public.my_group_role(g.id) is not null
        else public.my_group_role(g.id) in ('OWNER', 'ADMIN') and public.is_premium(g.owner_id)
    end
    from public.groups g
    where g.id = p_group_id
$$;

alter table public.groups enable row level security;
alter table public.group_members enable row level security;
alter table public.group_messages enable row level security;
alter table public.group_message_likes enable row level security;

create policy groups_select_visible on public.groups
    for select to authenticated
    using ((select public.can_see_group(id)));

create policy group_members_select_member on public.group_members
    for select to authenticated
    using ((select public.is_group_member(group_id)));

-- Also what Realtime uses to decide who receives new messages.
create policy group_messages_select_member on public.group_messages
    for select to authenticated
    using (deleted_at is null and (select public.is_group_member(group_id)) and not public.is_blocked_with(sender_id));

create policy group_message_likes_select_own on public.group_message_likes
    for select to authenticated
    using (user_id = (select auth.uid()));

create policy group_media_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'group-media'
        and (storage.foldername(name))[1] = (select auth.uid())::text
        and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$'
    );

create policy group_media_select_visible on storage.objects
    for select to authenticated
    using (
        bucket_id = 'group-media'
        and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or exists (select 1 from public.groups g where g.photo_path = objects.name and public.can_see_group(g.id))
            or exists (
                select 1 from public.group_messages m
                where m.media_path = objects.name and m.deleted_at is null and public.is_group_member(m.group_id)
            )
        )
    );

create policy group_media_delete_own on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'group-media'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

-- "BLM 101", "blm101" and "Blm-101" become "BLM101".
create function public.normalize_course_code(p_code text)
returns text
language sql
immutable
set search_path = ''
as $$
    select nullif(regexp_replace(upper(public.search_fold(coalesce(p_code, ''))), '[^A-Z0-9]', '', 'g'), '')
$$;

-- An uploaded group photo in the caller's folder that nothing else uses yet.
create function public.is_unused_group_media(p_path text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select p_path ~ ('^' || auth.uid()::text || '/[0-9a-f-]{36}\.jpg$')
       and exists (select 1 from storage.objects o where o.bucket_id = 'group-media' and o.name = p_path)
       and not exists (select 1 from public.groups g where g.photo_path = p_path)
       and not exists (select 1 from public.group_messages m where m.media_path = p_path)
$$;

create function public.create_group(
    p_kind public.group_kind,
    p_name text,
    p_description text default null,
    p_course_code text default null,
    p_university_only boolean default true
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_student record;
    v_name text := btrim(coalesce(p_name, ''));
    v_description text := nullif(btrim(coalesce(p_description, '')), '');
    v_code text := public.normalize_course_code(p_course_code);
    v_id uuid;
begin
    select * into v_student from public.current_student();
    if not found then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if p_kind is null or char_length(v_name) not between 2 and 60 or char_length(coalesce(v_description, '')) > 500
        or (v_code is not null and v_code !~ '^[A-Z0-9]{2,12}$') then
        raise exception using errcode = 'P0001', message = 'invalid_group';
    end if;
    if p_kind = 'CHANNEL' and not public.is_premium(v_student.id) then
        raise exception using errcode = 'P0001', message = 'premium_required';
    end if;
    if (select count(*) from public.groups
        where owner_id = v_student.id and kind = p_kind and deleted_at is null)
        >= (case p_kind when 'CHANNEL' then 5 else 10 end) then
        raise exception using errcode = 'P0001', message = 'too_many_groups';
    end if;
    if (select count(*) from public.groups
        where owner_id = v_student.id and created_at > now() - interval '24 hours') >= 5 then
        raise exception using errcode = 'P0001', message = 'rate_limited';
    end if;

    insert into public.groups (kind, name, description, course_code, owner_id, university_id)
    values (
        p_kind, v_name, v_description, v_code, v_student.id,
        case when p_kind = 'STUDY_GROUP' or coalesce(p_university_only, true) then v_student.university_id end
    )
    returning id into v_id;
    insert into public.group_members (group_id, user_id, role) values (v_id, v_student.id, 'OWNER');
    return v_id;
end;
$$;

create function public.update_group(p_group_id uuid, p_name text, p_description text, p_course_code text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_name text := btrim(coalesce(p_name, ''));
    v_description text := nullif(btrim(coalesce(p_description, '')), '');
    v_code text := public.normalize_course_code(p_course_code);
begin
    if public.my_group_role(p_group_id) is distinct from 'OWNER' and public.my_group_role(p_group_id) is distinct from 'ADMIN' then
        raise exception using errcode = 'P0001', message = 'group_not_allowed';
    end if;
    if char_length(v_name) not between 2 and 60 or char_length(coalesce(v_description, '')) > 500
        or (v_code is not null and v_code !~ '^[A-Z0-9]{2,12}$') then
        raise exception using errcode = 'P0001', message = 'invalid_group';
    end if;
    update public.groups set name = v_name, description = v_description, course_code = v_code where id = p_group_id;
end;
$$;

-- A channel's photo is a Premium feature; a study group's photo is free.
create function public.set_group_photo(p_group_id uuid, p_path text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_group public.groups%rowtype;
begin
    if public.my_group_role(p_group_id) is distinct from 'OWNER' and public.my_group_role(p_group_id) is distinct from 'ADMIN' then
        raise exception using errcode = 'P0001', message = 'group_not_allowed';
    end if;
    select * into v_group from public.groups where id = p_group_id;
    if v_group.kind = 'CHANNEL' and not public.is_premium(v_group.owner_id) then
        raise exception using errcode = 'P0001', message = 'premium_required';
    end if;
    if p_path is not null and not public.is_unused_group_media(p_path) then
        raise exception using errcode = 'P0001', message = 'invalid_media';
    end if;
    update public.groups set photo_path = p_path where id = p_group_id;
end;
$$;

create function public.delete_group(p_group_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if public.my_group_role(p_group_id) is distinct from 'OWNER' then
        raise exception using errcode = 'P0001', message = 'group_not_allowed';
    end if;
    update public.groups set deleted_at = now() where id = p_group_id and deleted_at is null;
end;
$$;

create function public.join_group(p_group_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_group public.groups%rowtype;
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if not public.can_see_group(p_group_id) then
        raise exception using errcode = 'P0001', message = 'group_not_found';
    end if;
    select * into v_group from public.groups where id = p_group_id for update;
    if exists (select 1 from public.group_members where group_id = p_group_id and user_id = auth.uid()) then
        return;
    end if;
    if v_group.member_count >= (case v_group.kind when 'CHANNEL' then 10000 else 200 end) then
        raise exception using errcode = 'P0001', message = 'group_full';
    end if;
    if (select count(*) from public.group_members where user_id = auth.uid()) >= 100 then
        raise exception using errcode = 'P0001', message = 'too_many_groups';
    end if;
    insert into public.group_members (group_id, user_id, role, last_read_at) values (p_group_id, auth.uid(), 'MEMBER', now());
end;
$$;

create function public.leave_group(p_group_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if public.my_group_role(p_group_id) = 'OWNER' then
        raise exception using errcode = 'P0001', message = 'owner_cannot_leave';
    end if;
    delete from public.group_members where group_id = p_group_id and user_id = auth.uid();
end;
$$;

-- The owner makes a member an admin or back; only the owner can.
create function public.set_group_member_role(p_group_id uuid, p_user_id uuid, p_role public.group_role)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if public.my_group_role(p_group_id) is distinct from 'OWNER' then
        raise exception using errcode = 'P0001', message = 'group_not_allowed';
    end if;
    if p_role not in ('ADMIN', 'MEMBER') or p_user_id = auth.uid() then
        raise exception using errcode = 'P0001', message = 'invalid_group';
    end if;
    update public.group_members set role = p_role where group_id = p_group_id and user_id = p_user_id;
    if not found then
        raise exception using errcode = 'P0001', message = 'user_not_found';
    end if;
end;
$$;

-- Owners remove anyone but themselves; admins remove members only.
create function public.remove_group_member(p_group_id uuid, p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_my_role public.group_role := public.my_group_role(p_group_id);
    v_their_role public.group_role;
begin
    select role into v_their_role from public.group_members where group_id = p_group_id and user_id = p_user_id;
    if v_their_role is null then
        raise exception using errcode = 'P0001', message = 'user_not_found';
    end if;
    if v_my_role is null or v_their_role = 'OWNER'
        or (v_my_role = 'ADMIN' and v_their_role <> 'MEMBER') or v_my_role = 'MEMBER' then
        raise exception using errcode = 'P0001', message = 'group_not_allowed';
    end if;
    delete from public.group_members where group_id = p_group_id and user_id = p_user_id;
end;
$$;

-- Groups the caller belongs to, most recent activity first.
create function public.list_my_groups(p_kind public.group_kind)
returns table (
    group_id uuid,
    kind public.group_kind,
    name text,
    description text,
    course_code text,
    photo_path text,
    member_count integer,
    my_role public.group_role,
    is_global boolean,
    last_message_body text,
    last_message_at timestamptz,
    unread_count integer
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
    select g.id, g.kind, g.name, g.description, g.course_code, g.photo_path, g.member_count, me.role,
           g.university_id is null,
           lm.body, lm.created_at,
           (select count(*)::integer from public.group_messages x
            where x.group_id = g.id and x.deleted_at is null and x.sender_id <> auth.uid()
              and x.created_at > coalesce(me.last_read_at, me.joined_at)
              and not public.is_blocked_with(x.sender_id))
    from public.group_members me
    join public.groups g on g.id = me.group_id
    left join lateral (
        select coalesce(m.body, '') as body, m.created_at
        from public.group_messages m
        where m.group_id = g.id and m.deleted_at is null and not public.is_blocked_with(m.sender_id)
        order by m.created_at desc, m.id desc
        limit 1
    ) lm on true
    where me.user_id = auth.uid()
      and g.kind = p_kind
      and public.can_see_group(g.id)
    order by coalesce(lm.created_at, g.created_at) desc
    limit 200;
end;
$$;

-- Groups the caller can join, largest first; optionally filtered by name or course code.
create function public.discover_groups(p_kind public.group_kind, p_query text default null)
returns table (
    group_id uuid,
    kind public.group_kind,
    name text,
    description text,
    course_code text,
    photo_path text,
    member_count integer,
    is_global boolean,
    owner_full_name text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_query text := nullif(btrim(coalesce(p_query, '')), '');
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if v_query is not null and char_length(v_query) > 64 then
        raise exception using errcode = 'P0001', message = 'invalid_query';
    end if;
    return query
    select g.id, g.kind, g.name, g.description, g.course_code, g.photo_path, g.member_count, g.university_id is null, o.full_name
    from public.groups g
    join public.profiles o on o.id = g.owner_id
    where g.kind = p_kind
      and public.can_see_group(g.id)
      and not exists (select 1 from public.group_members m where m.group_id = g.id and m.user_id = auth.uid())
      and (v_query is null
           or public.search_fold(g.name) like public.search_pattern(v_query)
           or coalesce(g.course_code, '') like '%' || coalesce(public.normalize_course_code(v_query), '#') || '%')
    order by g.member_count desc, g.created_at desc
    limit 50;
end;
$$;

create function public.get_group(p_group_id uuid)
returns table (
    group_id uuid,
    kind public.group_kind,
    name text,
    description text,
    course_code text,
    photo_path text,
    member_count integer,
    is_global boolean,
    owner_id uuid,
    owner_full_name text,
    my_role public.group_role,
    can_post boolean,
    owner_is_premium boolean,
    pinned_message_id uuid,
    pinned_message_body text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.can_see_group(p_group_id) then
        raise exception using errcode = 'P0001', message = 'group_not_found';
    end if;
    return query
    select g.id, g.kind, g.name, g.description, g.course_code, g.photo_path, g.member_count, g.university_id is null,
           g.owner_id, o.full_name, public.my_group_role(g.id), coalesce(public.group_can_post(g.id), false),
           public.is_premium(g.owner_id),
           case when p.id is not null and p.deleted_at is null then p.id end,
           case when p.id is not null and p.deleted_at is null then coalesce(p.body, '') end
    from public.groups g
    join public.profiles o on o.id = g.owner_id
    left join public.group_messages p on p.id = g.pinned_message_id
    where g.id = p_group_id;
end;
$$;

create function public.list_group_members(p_group_id uuid)
returns table (user_id uuid, full_name text, username text, role public.group_role, joined_at timestamptz)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.is_group_member(p_group_id) then
        raise exception using errcode = 'P0001', message = 'group_not_found';
    end if;
    return query
    select p.id, p.full_name, p.username, m.role, m.joined_at
    from public.group_members m
    join public.profiles p on p.id = m.user_id
    where m.group_id = p_group_id and not public.is_blocked_with(p.id)
    order by case m.role when 'OWNER' then 0 when 'ADMIN' then 1 else 2 end, p.full_name, p.id
    limit 500;
end;
$$;

-- Idempotent for the same message id. Polls are for channels (a Premium feature).
create function public.send_group_message(
    p_group_id uuid,
    p_message_id uuid,
    p_body text,
    p_media_path text default null,
    p_poll_options text[] default null,
    p_poll_closes_at timestamptz default null
)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_body text := nullif(btrim(coalesce(p_body, '')), '');
    v_group public.groups%rowtype;
    v_created timestamptz;
    v_poll uuid;
begin
    if not public.is_group_member(p_group_id) then
        raise exception using errcode = 'P0001', message = 'group_not_found';
    end if;
    select * into v_group from public.groups where id = p_group_id;
    if not public.group_can_post(p_group_id) then
        if v_group.kind = 'CHANNEL' and public.my_group_role(p_group_id) in ('OWNER', 'ADMIN') then
            raise exception using errcode = 'P0001', message = 'premium_required';
        end if;
        raise exception using errcode = 'P0001', message = 'group_not_allowed';
    end if;

    select created_at into v_created
    from public.group_messages
    where id = p_message_id and group_id = p_group_id and sender_id = auth.uid();
    if found then
        return v_created;
    end if;

    if p_message_id is null or (v_body is null and p_media_path is null)
        or char_length(coalesce(v_body, '')) > 2000 then
        raise exception using errcode = 'P0001', message = 'invalid_message';
    end if;
    if p_media_path is not null and not public.is_unused_group_media(p_media_path) then
        raise exception using errcode = 'P0001', message = 'invalid_media';
    end if;
    if p_poll_options is not null and (
        v_group.kind <> 'CHANNEL'
        or v_body is null
        or not public.valid_poll_options(p_poll_options)
        or (p_poll_closes_at is not null and (p_poll_closes_at <= now() or p_poll_closes_at > now() + interval '30 days'))
    ) then
        raise exception using errcode = 'P0001', message = 'invalid_poll';
    end if;
    if p_poll_options is null and p_poll_closes_at is not null then
        raise exception using errcode = 'P0001', message = 'invalid_poll';
    end if;
    if (select count(*) from public.group_messages
        where sender_id = auth.uid() and created_at > now() - interval '1 minute') >= 30 then
        raise exception using errcode = 'P0001', message = 'rate_limited';
    end if;

    insert into public.group_messages (id, group_id, sender_id, body, media_path)
    values (p_message_id, p_group_id, auth.uid(), v_body, p_media_path)
    returning created_at into v_created;

    if p_poll_options is not null then
        insert into public.polls (group_message_id, closes_at) values (p_message_id, p_poll_closes_at) returning id into v_poll;
        perform public.insert_poll_options(v_poll, p_poll_options);
    end if;

    update public.groups set last_message_at = v_created where id = p_group_id;
    update public.group_members set last_read_at = v_created where group_id = p_group_id and user_id = auth.uid();
    return v_created;
exception
    when unique_violation then
        raise exception using errcode = 'P0001', message = 'invalid_message';
end;
$$;

-- The sender deletes their own message; owners and admins delete any.
create function public.delete_group_message(p_message_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_message public.group_messages%rowtype;
begin
    select * into v_message from public.group_messages where id = p_message_id and deleted_at is null;
    if not found or not public.is_group_member(v_message.group_id) then
        raise exception using errcode = 'P0001', message = 'message_not_found';
    end if;
    if v_message.sender_id <> auth.uid() and public.my_group_role(v_message.group_id) not in ('OWNER', 'ADMIN') then
        raise exception using errcode = 'P0001', message = 'group_not_allowed';
    end if;
    update public.group_messages set deleted_at = now() where id = p_message_id;
end;
$$;

create function public.set_group_message_liked(p_message_id uuid, p_liked boolean)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_group uuid;
begin
    select group_id into v_group from public.group_messages where id = p_message_id and deleted_at is null;
    if v_group is null or not public.is_group_member(v_group) then
        raise exception using errcode = 'P0001', message = 'message_not_found';
    end if;
    if p_liked then
        insert into public.group_message_likes (message_id, user_id) values (p_message_id, auth.uid())
        on conflict do nothing;
    else
        delete from public.group_message_likes where message_id = p_message_id and user_id = auth.uid();
    end if;
    return (select like_count from public.group_messages where id = p_message_id);
end;
$$;

create function public.pin_group_message(p_group_id uuid, p_message_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if public.my_group_role(p_group_id) is distinct from 'OWNER' and public.my_group_role(p_group_id) is distinct from 'ADMIN' then
        raise exception using errcode = 'P0001', message = 'group_not_allowed';
    end if;
    if p_message_id is not null and not exists (
        select 1 from public.group_messages where id = p_message_id and group_id = p_group_id and deleted_at is null
    ) then
        raise exception using errcode = 'P0001', message = 'message_not_found';
    end if;
    update public.groups set pinned_message_id = p_message_id where id = p_group_id;
end;
$$;

-- Newest first, keyset-paginated. Messages from blocked people are left out.
create function public.list_group_messages(
    p_group_id uuid,
    p_before_created_at timestamptz default null,
    p_before_id uuid default null,
    p_limit integer default 50
)
returns table (
    id uuid,
    sender_id uuid,
    sender_full_name text,
    sender_username text,
    body text,
    media_path text,
    created_at timestamptz,
    like_count integer,
    liked_by_me boolean,
    is_mine boolean,
    poll jsonb
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.is_group_member(p_group_id) then
        raise exception using errcode = 'P0001', message = 'group_not_found';
    end if;
    return query
    select m.id, m.sender_id, s.full_name, s.username, m.body, m.media_path, m.created_at, m.like_count,
           exists (select 1 from public.group_message_likes l where l.message_id = m.id and l.user_id = auth.uid()),
           m.sender_id = auth.uid(),
           (select public.poll_json(pl.id) from public.polls pl where pl.group_message_id = m.id)
    from public.group_messages m
    join public.profiles s on s.id = m.sender_id
    where m.group_id = p_group_id
      and m.deleted_at is null
      and not public.is_blocked_with(m.sender_id)
      and (p_before_created_at is null
           or (m.created_at, m.id) < (p_before_created_at, coalesce(p_before_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)))
    order by m.created_at desc, m.id desc
    limit least(greatest(coalesce(p_limit, 50), 1), 100);
end;
$$;

create function public.mark_group_read(p_group_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_group_member(p_group_id) then
        raise exception using errcode = 'P0001', message = 'group_not_found';
    end if;
    update public.group_members
    set last_read_at = greatest(coalesce(last_read_at, '-infinity'), now())
    where group_id = p_group_id and user_id = auth.uid();
end;
$$;

-- Polls in channel messages are seen and voted on by members.
create or replace function public.can_see_poll(p_poll_id uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_poll public.polls%rowtype;
    v_group uuid;
begin
    select * into v_poll from public.polls where id = p_poll_id;
    if not found then
        return false;
    end if;
    if v_poll.post_id is not null then
        return public.can_see_post(v_poll.post_id);
    end if;
    select group_id into v_group from public.group_messages where id = v_poll.group_message_id and deleted_at is null;
    return v_group is not null and public.is_group_member(v_group);
end;
$$;

alter publication supabase_realtime add table public.group_messages;

-- Course notes -------------------------------------------------------------------------

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('course-notes', 'course-notes', false, 20971520, array['application/pdf'])
on conflict (id) do update
    set public = false,
        file_size_limit = excluded.file_size_limit,
        allowed_mime_types = excluded.allowed_mime_types;

create table public.course_notes (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null references public.profiles (id) on delete cascade,
    university_id uuid not null references public.universities (id) on delete restrict,
    course_code text not null check (course_code ~ '^[A-Z0-9]{2,12}$'),
    course_name text not null check (char_length(btrim(course_name)) between 2 and 120),
    title text not null check (char_length(btrim(title)) between 2 and 120),
    description text check (description is null or char_length(btrim(description)) between 1 and 500),
    file_path text not null unique check (file_path ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.pdf$'),
    file_size bigint not null check (file_size between 1 and 20971520),
    download_count integer not null default 0 check (download_count >= 0),
    created_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index course_notes_university_idx on public.course_notes (university_id, course_code, created_at desc);
create index course_notes_author_idx on public.course_notes (author_id);

create table public.note_downloads (
    note_id uuid not null references public.course_notes (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (note_id, user_id)
);

create index note_downloads_user_idx on public.note_downloads (user_id);

alter table public.course_notes enable row level security;
alter table public.note_downloads enable row level security;
-- No policies on the tables: read through the functions below.

create function public.can_see_note(p_note_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.course_notes n
        join public.current_student() s on s.university_id = n.university_id
        where n.id = p_note_id and n.deleted_at is null and not public.is_blocked_with(n.author_id)
    )
$$;

-- Whether a stored PDF belongs to a note the caller can see (the notes table has no read policy).
create function public.can_read_note_file(p_path text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (select 1 from public.course_notes n where n.file_path = p_path and public.can_see_note(n.id))
$$;

create policy course_notes_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'course-notes'
        and (storage.foldername(name))[1] = (select auth.uid())::text
        and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.pdf$'
    );

create policy course_notes_select_visible on storage.objects
    for select to authenticated
    using (
        bucket_id = 'course-notes'
        and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or public.can_read_note_file(name)
        )
    );

create policy course_notes_delete_own on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'course-notes'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

-- Called by the submit-course-note Edge Function after it checked the PDF bytes.
create function public.create_course_note(
    p_user_id uuid,
    p_course_code text,
    p_course_name text,
    p_title text,
    p_description text,
    p_path text,
    p_size bigint
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_university uuid;
    v_code text := public.normalize_course_code(p_course_code);
    v_id uuid;
begin
    select university_id into v_university
    from public.profiles where id = p_user_id and account_status = 'APPROVED';
    if not found or v_university is null then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if p_path is null or p_path !~ ('^' || p_user_id::text || '/[0-9a-f-]{36}\.pdf$')
        or not exists (select 1 from storage.objects o where o.bucket_id = 'course-notes' and o.name = p_path)
        or exists (select 1 from public.course_notes n where n.file_path = p_path) then
        raise exception using errcode = 'P0001', message = 'invalid_document_path';
    end if;
    if v_code is null or v_code !~ '^[A-Z0-9]{2,12}$'
        or char_length(btrim(coalesce(p_course_name, ''))) not between 2 and 120
        or char_length(btrim(coalesce(p_title, ''))) not between 2 and 120
        or char_length(btrim(coalesce(p_description, ''))) > 500
        or p_size is null or p_size not between 1 and 20971520 then
        raise exception using errcode = 'P0001', message = 'invalid_note';
    end if;
    if (select count(*) from public.course_notes
        where author_id = p_user_id and created_at > now() - interval '24 hours') >= 20 then
        raise exception using errcode = 'P0001', message = 'rate_limited';
    end if;

    insert into public.course_notes (author_id, university_id, course_code, course_name, title, description, file_path, file_size)
    values (p_user_id, v_university, v_code, btrim(p_course_name), btrim(p_title),
            nullif(btrim(coalesce(p_description, '')), ''), p_path, p_size)
    returning id into v_id;
    return v_id;
end;
$$;

-- Notes of the caller's university, newest first, filtered by course or text.
create function public.list_course_notes(p_course_code text default null, p_query text default null)
returns table (
    note_id uuid,
    course_code text,
    course_name text,
    title text,
    description text,
    file_size bigint,
    download_count integer,
    created_at timestamptz,
    is_mine boolean,
    author_id uuid,
    author_full_name text,
    author_username text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_student record;
    v_code text := public.normalize_course_code(p_course_code);
    v_query text := nullif(btrim(coalesce(p_query, '')), '');
begin
    select * into v_student from public.current_student();
    if not found then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if v_query is not null and char_length(v_query) > 64 then
        raise exception using errcode = 'P0001', message = 'invalid_query';
    end if;
    return query
    select n.id, n.course_code, n.course_name, n.title, n.description, n.file_size, n.download_count, n.created_at,
           n.author_id = v_student.id, a.id, a.full_name, a.username
    from public.course_notes n
    join public.profiles a on a.id = n.author_id
    where n.university_id = v_student.university_id
      and n.deleted_at is null
      and not public.is_blocked_with(n.author_id)
      and (v_code is null or n.course_code = v_code)
      and (v_query is null
           or public.search_fold(n.title || ' ' || n.course_name || ' ' || coalesce(n.description, '')) like public.search_pattern(v_query)
           or n.course_code like '%' || coalesce(public.normalize_course_code(v_query), '#') || '%')
    order by n.created_at desc, n.id desc
    limit 100;
end;
$$;

-- Course codes that have notes at the caller's university, most notes first.
create function public.list_note_courses()
returns table (course_code text, course_name text, note_count bigint)
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
    select n.course_code, min(n.course_name), count(*)
    from public.course_notes n
    where n.university_id = v_student.university_id and n.deleted_at is null and not public.is_blocked_with(n.author_id)
    group by n.course_code
    order by count(*) desc, n.course_code
    limit 100;
end;
$$;

-- Records that the caller opens the note (counted once per person) and returns its file.
create function public.open_course_note(p_note_id uuid)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_note public.course_notes%rowtype;
begin
    if not public.can_see_note(p_note_id) then
        raise exception using errcode = 'P0001', message = 'note_not_found';
    end if;
    select * into v_note from public.course_notes where id = p_note_id;
    if v_note.author_id <> auth.uid() then
        insert into public.note_downloads (note_id, user_id) values (p_note_id, auth.uid()) on conflict do nothing;
        if found then
            update public.course_notes set download_count = download_count + 1 where id = p_note_id;
        end if;
    end if;
    return v_note.file_path;
end;
$$;

create function public.delete_course_note(p_note_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.course_notes set deleted_at = now()
    where id = p_note_id and author_id = auth.uid() and deleted_at is null;
    if not found then
        raise exception using errcode = 'P0001', message = 'note_not_found';
    end if;
end;
$$;

-- Badges ----------------------------------------------------------------------------------

-- Points and badges earned from what the person actually did. Nothing is stored:
-- they are computed from posts, answers, notes, events and likes each time.
create function public.user_badges(p_user_id uuid)
returns table (points integer, badges text[])
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_likes integer;
    v_answers integer;
    v_notes integer;
    v_note_opens integer;
    v_events integer;
    v_posts integer;
    v_badges text[] := '{}';
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if not exists (select 1 from public.profiles where id = p_user_id and account_status = 'APPROVED')
        or (p_user_id <> auth.uid() and public.is_blocked_with(p_user_id)) then
        raise exception using errcode = 'P0001', message = 'user_not_found';
    end if;

    select coalesce(sum(p.like_count), 0), count(*) filter (where p.category = 'EVENT'), count(*)
    into v_likes, v_events, v_posts
    from public.posts p where p.author_id = p_user_id and p.deleted_at is null;

    select count(*) into v_answers
    from public.comments c
    join public.posts p on p.id = c.post_id
    where c.author_id = p_user_id and c.deleted_at is null
      and p.deleted_at is null and p.category = 'QUESTION' and p.author_id <> p_user_id;

    select count(*), coalesce(sum(n.download_count), 0) into v_notes, v_note_opens
    from public.course_notes n where n.author_id = p_user_id and n.deleted_at is null;

    if public.is_premium(p_user_id) then v_badges := v_badges || 'PREMIUM'::text; end if;
    if exists (select 1 from public.groups g where g.owner_id = p_user_id and g.kind = 'CHANNEL' and g.deleted_at is null) then
        v_badges := v_badges || 'CHANNEL_OWNER'::text;
    end if;
    if v_answers >= 10 then v_badges := v_badges || 'HELPFUL'::text; end if;
    if v_notes >= 3 then v_badges := v_badges || 'NOTE_SHARER'::text; end if;
    if v_likes >= 50 then v_badges := v_badges || 'POPULAR'::text; end if;
    if v_events >= 3 then v_badges := v_badges || 'EVENT_ORGANIZER'::text; end if;
    if v_posts >= 25 then v_badges := v_badges || 'ACTIVE_MEMBER'::text; end if;

    return query select (v_likes + 3 * v_answers + 5 * v_notes + 2 * v_note_opens + 5 * v_events)::integer, v_badges;
end;
$$;

-- Reports ----------------------------------------------------------------------------------

create or replace function public.report_content(
    p_target_kind public.report_target,
    p_target_id uuid,
    p_reason public.report_reason,
    p_details text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid;
    v_excerpt text;
    v_details text := nullif(btrim(coalesce(p_details, '')), '');
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if p_reason is null or p_target_kind is null or char_length(coalesce(v_details, '')) > 500 then
        raise exception using errcode = 'P0001', message = 'invalid_report';
    end if;

    -- Only what the reporter can actually see can be reported.
    if p_target_kind = 'POST' then
        select p.author_id, p.body into v_user, v_excerpt
        from public.posts p where p.id = p_target_id and public.can_see_post(p.id);
    elsif p_target_kind = 'COMMENT' then
        select c.author_id, c.body into v_user, v_excerpt
        from public.comments c
        where c.id = p_target_id and c.deleted_at is null and public.can_see_post(c.post_id);
    elsif p_target_kind = 'MESSAGE' then
        select m.sender_id, m.body into v_user, v_excerpt
        from public.messages m
        where m.id = p_target_id and public.is_conversation_member(m.conversation_id);
    elsif p_target_kind = 'GROUP_MESSAGE' then
        select m.sender_id, coalesce(m.body, '[fotoğraf]') into v_user, v_excerpt
        from public.group_messages m
        where m.id = p_target_id and m.deleted_at is null and public.is_group_member(m.group_id);
    elsif p_target_kind = 'GROUP' then
        select g.owner_id, g.name || coalesce(' — ' || g.description, '') into v_user, v_excerpt
        from public.groups g where g.id = p_target_id and public.can_see_group(g.id);
    elsif p_target_kind = 'NOTE' then
        select n.author_id, n.course_code || ' · ' || n.title || coalesce(' — ' || n.description, '') into v_user, v_excerpt
        from public.course_notes n where n.id = p_target_id and public.can_see_note(n.id);
    else
        select p.id, p.full_name || ' (@' || p.username || ')' into v_user, v_excerpt
        from public.profiles p where p.id = p_target_id and p.username is not null;
    end if;
    if v_user is null or v_user = auth.uid() then
        raise exception using errcode = 'P0001', message = 'report_target_not_found';
    end if;

    if (select count(*) from public.reports
        where reporter_id = auth.uid() and created_at > now() - interval '24 hours') >= 20 then
        raise exception using errcode = 'P0001', message = 'rate_limited';
    end if;

    insert into public.reports (reporter_id, target_kind, target_id, target_user_id, target_excerpt, reason, details)
    values (auth.uid(), p_target_kind, p_target_id, v_user, left(v_excerpt, 2000), p_reason, v_details)
    on conflict (reporter_id, target_kind, target_id) where status = 'OPEN' do nothing;
end;
$$;

create or replace function public.resolve_report(p_report_id uuid, p_action text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_report public.reports%rowtype;
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    select * into v_report from public.reports where id = p_report_id and status = 'OPEN' for update;
    if not found then
        raise exception using errcode = 'P0001', message = 'report_not_found';
    end if;
    if p_action is null or p_action not in ('DISMISS', 'REMOVE_CONTENT', 'SUSPEND_USER')
        or (p_action = 'REMOVE_CONTENT' and v_report.target_kind = 'USER') then
        raise exception using errcode = 'P0001', message = 'invalid_action';
    end if;

    if p_action = 'REMOVE_CONTENT' then
        if v_report.target_kind = 'POST' then
            update public.posts set deleted_at = now() where id = v_report.target_id and deleted_at is null;
        elsif v_report.target_kind = 'COMMENT' then
            update public.comments set deleted_at = now() where id = v_report.target_id and deleted_at is null;
        elsif v_report.target_kind = 'GROUP_MESSAGE' then
            update public.group_messages set deleted_at = now() where id = v_report.target_id and deleted_at is null;
        elsif v_report.target_kind = 'GROUP' then
            update public.groups set deleted_at = now() where id = v_report.target_id and deleted_at is null;
        elsif v_report.target_kind = 'NOTE' then
            update public.course_notes set deleted_at = now() where id = v_report.target_id and deleted_at is null;
        else
            delete from public.messages where id = v_report.target_id;
        end if;
    elsif p_action = 'SUSPEND_USER' then
        update public.profiles set account_status = 'SUSPENDED', updated_at = now()
        where id = v_report.target_user_id;
    end if;

    update public.reports
    set status = case when p_action = 'DISMISS' then 'DISMISSED'::public.report_status else 'RESOLVED' end,
        resolution = p_action,
        resolved_by = auth.uid(),
        resolved_at = now()
    where status = 'OPEN'
      and (id = p_report_id
           or (target_kind = v_report.target_kind and target_id = v_report.target_id)
           or (p_action = 'SUSPEND_USER' and target_user_id = v_report.target_user_id));
end;
$$;

-- Grants ------------------------------------------------------------------------------------

do $$
declare
    f text;
begin
    foreach f in array array[
        'public.am_i_premium()',
        'public.create_group(public.group_kind, text, text, text, boolean)',
        'public.update_group(uuid, text, text, text)',
        'public.set_group_photo(uuid, text)',
        'public.delete_group(uuid)',
        'public.join_group(uuid)',
        'public.leave_group(uuid)',
        'public.set_group_member_role(uuid, uuid, public.group_role)',
        'public.remove_group_member(uuid, uuid)',
        'public.list_my_groups(public.group_kind)',
        'public.discover_groups(public.group_kind, text)',
        'public.get_group(uuid)',
        'public.list_group_members(uuid)',
        'public.send_group_message(uuid, uuid, text, text, text[], timestamptz)',
        'public.delete_group_message(uuid)',
        'public.set_group_message_liked(uuid, boolean)',
        'public.pin_group_message(uuid, uuid)',
        'public.list_group_messages(uuid, timestamptz, uuid, integer)',
        'public.mark_group_read(uuid)',
        'public.list_course_notes(text, text)',
        'public.list_note_courses()',
        'public.open_course_note(uuid)',
        'public.delete_course_note(uuid)',
        'public.user_badges(uuid)',
        -- Used by RLS policies, which run as the signed-in person.
        'public.can_see_group(uuid)',
        'public.is_group_member(uuid)',
        'public.can_see_note(uuid)',
        'public.can_read_note_file(text)'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;
    foreach f in array array[
        'public.is_premium(uuid)',
        'public.my_group_role(uuid)',
        'public.group_can_post(uuid)',
        'public.is_unused_group_media(text)',
        'public.maintain_group_counts()'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated', f);
    end loop;
    execute 'revoke all on function public.normalize_course_code(text) from public, anon';
    execute 'revoke all on function public.create_course_note(uuid, text, text, text, text, text, bigint) from public, anon, authenticated';
    execute 'grant execute on function public.create_course_note(uuid, text, text, text, text, text, bigint) to service_role';
end;
$$;
