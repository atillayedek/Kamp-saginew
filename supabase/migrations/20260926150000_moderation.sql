-- Moderation: blocking between students and reports reviewed by admins.
--
-- Blocking is mutual in effect: once either person blocks the other, neither
-- sees the other's posts, comments, matches, conversations or notifications,
-- and neither can start a chat or send a message to the other. Only the
-- blocker can see and undo a block; the blocked person is never told.
--
-- Reports keep a snapshot of the reported text so an admin can still judge it
-- if the author deletes it. Admins resolve a report by dismissing it, removing
-- the content or suspending the author; every other open report on the same
-- target is closed with it.

create table public.user_blocks (
    blocker_id uuid not null references public.profiles (id) on delete cascade,
    blocked_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    constraint user_blocks_not_self check (blocker_id <> blocked_id)
);

create index user_blocks_blocked_idx on public.user_blocks (blocked_id);

alter table public.user_blocks enable row level security;

create policy user_blocks_select_own on public.user_blocks
    for select to authenticated
    using (blocker_id = (select auth.uid()));

-- Whether the caller and another person are separated by a block, in either direction.
create function public.is_blocked_with(p_other uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.user_blocks b
        where (b.blocker_id = auth.uid() and b.blocked_id = p_other)
           or (b.blocker_id = p_other and b.blocked_id = auth.uid())
    )
$$;

revoke all on function public.is_blocked_with(uuid) from public, anon;
grant execute on function public.is_blocked_with(uuid) to authenticated;

create function public.block_user(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if p_user_id is null or p_user_id = auth.uid()
        or not exists (select 1 from public.profiles where id = p_user_id) then
        raise exception using errcode = 'P0001', message = 'user_not_found';
    end if;
    insert into public.user_blocks (blocker_id, blocked_id) values (auth.uid(), p_user_id)
    on conflict do nothing;
end;
$$;

create function public.unblock_user(p_user_id uuid)
returns void
language sql
security definer
set search_path = ''
as $$
    delete from public.user_blocks where blocker_id = auth.uid() and blocked_id = p_user_id
$$;

create function public.list_blocked_users()
returns table (user_id uuid, full_name text, username text, blocked_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
    select p.id, p.full_name, p.username, b.created_at
    from public.user_blocks b
    join public.profiles p on p.id = b.blocked_id
    where b.blocker_id = auth.uid()
    order by b.created_at desc
$$;

-- Posts and comments ----------------------------------------------------------------

create or replace function public.can_see_post(p_post_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.posts p
        join public.current_student() s on true
        where p.id = p_post_id
          and p.deleted_at is null
          and (p.scope = 'GENERAL' or p.university_id = s.university_id)
          and not public.is_blocked_with(p.author_id)
    )
$$;

drop policy comments_select_visible on public.comments;
create policy comments_select_visible on public.comments
    for select to authenticated
    using (
        deleted_at is null
        and (select public.can_see_post(post_id))
        and not public.is_blocked_with(author_id)
    );

create or replace function public.list_posts(
    p_scope public.post_scope,
    p_before_created_at timestamptz default null,
    p_before_id uuid default null,
    p_limit integer default 20
)
returns table (
    id uuid,
    scope public.post_scope,
    body text,
    created_at timestamptz,
    like_count integer,
    comment_count integer,
    liked_by_me boolean,
    is_mine boolean,
    author_id uuid,
    author_full_name text,
    author_username text,
    author_university text
)
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
    select p.id, p.scope, p.body, p.created_at, p.like_count, p.comment_count,
           exists (select 1 from public.post_likes l where l.post_id = p.id and l.user_id = v_student.id),
           p.author_id = v_student.id,
           a.id, a.full_name, a.username, u.name
    from public.posts p
    join public.profiles a on a.id = p.author_id
    left join public.universities u on u.id = a.university_id
    where p.deleted_at is null
      and p.scope = p_scope
      and (p_scope = 'GENERAL' or p.university_id = v_student.university_id)
      and not exists (
          select 1 from public.user_blocks b
          where (b.blocker_id = v_student.id and b.blocked_id = p.author_id)
             or (b.blocker_id = p.author_id and b.blocked_id = v_student.id)
      )
      and (p_before_created_at is null
           or (p.created_at, p.id) < (p_before_created_at, coalesce(p_before_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)))
    order by p.created_at desc, p.id desc
    limit least(greatest(coalesce(p_limit, 20), 1), 50);
end;
$$;

create or replace function public.list_comments(p_post_id uuid)
returns table (
    id uuid,
    body text,
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
begin
    if not public.can_see_post(p_post_id) then
        raise exception using errcode = 'P0001', message = 'post_not_found';
    end if;
    return query
    select c.id, c.body, c.created_at, c.author_id = auth.uid(), a.id, a.full_name, a.username
    from public.comments c
    join public.profiles a on a.id = c.author_id
    where c.post_id = p_post_id and c.deleted_at is null
      and not public.is_blocked_with(c.author_id)
    order by c.created_at, c.id
    limit 500;
end;
$$;

-- Matching ------------------------------------------------------------------------------

create or replace function public.find_matches(p_requirement_id uuid, p_limit integer default 20)
returns table (
    requirement_id uuid,
    title text,
    description text,
    category public.requirement_category,
    tags text[],
    location_text text,
    starts_at timestamptz,
    participants_needed integer,
    created_at timestamptz,
    score integer,
    owner_id uuid,
    owner_full_name text,
    owner_username text,
    owner_department text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_source public.requirements%rowtype;
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;

    select r.* into v_source
    from public.requirements r
    where r.id = p_requirement_id and r.owner_id = auth.uid();
    if not found then
        raise exception using errcode = 'P0001', message = 'requirement_not_found';
    end if;

    return query
    select c.id, c.title, c.description, c.category, c.tags, c.location_text, c.starts_at,
           c.participants_needed, c.created_at,
           round(100 * (1 - (c.embedding operator(extensions.<=>) v_source.embedding)))::integer,
           p.id, p.full_name, p.username, p.department
    from public.requirements c
    join public.profiles p on p.id = c.owner_id and p.account_status = 'APPROVED'
    where c.status = 'ACTIVE'
      and c.owner_id <> v_source.owner_id
      and c.university_id = v_source.university_id
      and not public.is_blocked_with(c.owner_id)
      and (c.embedding operator(extensions.<=>) v_source.embedding) <= 0.5
    order by c.embedding operator(extensions.<=>) v_source.embedding, c.created_at desc
    limit least(greatest(coalesce(p_limit, 20), 1), 50);
end;
$$;

-- Chat ------------------------------------------------------------------------------------

create or replace function public.start_conversation(p_other_user_id uuid)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_me uuid := auth.uid();
    v_a uuid;
    v_b uuid;
    v_id uuid;
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if p_other_user_id is null or p_other_user_id = v_me or not exists (
        select 1 from public.profiles where id = p_other_user_id and account_status = 'APPROVED'
    ) or public.is_blocked_with(p_other_user_id) then
        raise exception using errcode = 'P0001', message = 'recipient_not_available';
    end if;

    v_a := least(v_me, p_other_user_id);
    v_b := greatest(v_me, p_other_user_id);

    insert into public.conversations (user_a, user_b) values (v_a, v_b)
    on conflict (user_a, user_b) do nothing
    returning id into v_id;

    if v_id is null then
        select id into v_id from public.conversations where user_a = v_a and user_b = v_b;
    else
        insert into public.conversation_members (conversation_id, user_id)
        values (v_id, v_a), (v_id, v_b);
    end if;
    return v_id;
end;
$$;

create or replace function public.send_message(p_conversation_id uuid, p_message_id uuid, p_body text)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_body text := btrim(coalesce(p_body, ''));
    v_created timestamptz;
    v_other uuid;
begin
    if not public.is_conversation_member(p_conversation_id) then
        raise exception using errcode = 'P0001', message = 'conversation_not_found';
    end if;
    if p_message_id is null or char_length(v_body) not between 1 and 2000 then
        raise exception using errcode = 'P0001', message = 'invalid_message';
    end if;

    select created_at into v_created
    from public.messages
    where id = p_message_id and conversation_id = p_conversation_id and sender_id = auth.uid();
    if found then
        return v_created;
    end if;

    -- The other member must still be an approved student and not blocked either way.
    select m.user_id into v_other
    from public.conversation_members m
    join public.profiles p on p.id = m.user_id and p.account_status = 'APPROVED'
    where m.conversation_id = p_conversation_id and m.user_id <> auth.uid();
    if v_other is null or public.is_blocked_with(v_other) then
        raise exception using errcode = 'P0001', message = 'recipient_not_available';
    end if;

    if (select count(*) from public.messages
        where sender_id = auth.uid() and created_at > now() - interval '1 minute') >= 30 then
        raise exception using errcode = 'P0001', message = 'rate_limited';
    end if;

    insert into public.messages (id, conversation_id, sender_id, body)
    values (p_message_id, p_conversation_id, auth.uid(), v_body)
    returning created_at into v_created;

    update public.conversations set last_message_at = v_created where id = p_conversation_id;
    -- Sending implies having read everything before it.
    update public.conversation_members set last_read_at = v_created
    where conversation_id = p_conversation_id and user_id = auth.uid();
    return v_created;
exception
    when unique_violation then
        -- Same id used for a different conversation or by someone else.
        raise exception using errcode = 'P0001', message = 'invalid_message';
end;
$$;

create or replace function public.list_conversations()
returns table (
    conversation_id uuid,
    other_user_id uuid,
    other_full_name text,
    other_username text,
    other_university text,
    last_message_body text,
    last_message_at timestamptz,
    last_message_is_mine boolean,
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
    select c.id, o.id, o.full_name, o.username, u.name,
           lm.body, lm.created_at, lm.sender_id = auth.uid(),
           (select count(*)::integer from public.messages x
            where x.conversation_id = c.id and x.sender_id <> auth.uid()
              and x.created_at > coalesce(me.last_read_at, '-infinity'))
    from public.conversation_members me
    join public.conversations c on c.id = me.conversation_id
    join public.profiles o on o.id = case when c.user_a = auth.uid() then c.user_b else c.user_a end
    left join public.universities u on u.id = o.university_id
    left join lateral (
        select m.body, m.created_at, m.sender_id
        from public.messages m
        where m.conversation_id = c.id
        order by m.created_at desc, m.id desc
        limit 1
    ) lm on true
    where me.user_id = auth.uid()
      and not public.is_blocked_with(o.id)
    order by coalesce(c.last_message_at, c.created_at) desc
    limit 200;
end;
$$;

-- The other member of a conversation, so the chat screen can block or report them.
create function public.conversation_partner(p_conversation_id uuid)
returns uuid
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_other uuid;
begin
    if not public.is_conversation_member(p_conversation_id) then
        raise exception using errcode = 'P0001', message = 'conversation_not_found';
    end if;
    select user_id into v_other
    from public.conversation_members
    where conversation_id = p_conversation_id and user_id <> auth.uid();
    return v_other;
end;
$$;

-- Notifications from a blocked person are no longer listed.
create or replace function public.list_notifications(p_limit integer default 50)
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
      and (n.actor_id is null or not public.is_blocked_with(n.actor_id))
    order by n.created_at desc
    limit least(greatest(coalesce(p_limit, 50), 1), 100)
$$;

-- Reports -------------------------------------------------------------------------------

create type public.report_target as enum ('POST', 'COMMENT', 'USER', 'MESSAGE');
create type public.report_reason as enum ('SPAM', 'HARASSMENT', 'INAPPROPRIATE', 'FAKE_PROFILE', 'OTHER');
create type public.report_status as enum ('OPEN', 'RESOLVED', 'DISMISSED');

create table public.reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references public.profiles (id) on delete cascade,
    target_kind public.report_target not null,
    target_id uuid not null,
    -- The person responsible for the reported content (the user themself for USER).
    target_user_id uuid not null references public.profiles (id) on delete cascade,
    target_excerpt text not null,
    reason public.report_reason not null,
    details text check (details is null or char_length(details) <= 500),
    status public.report_status not null default 'OPEN',
    resolution text check (resolution is null or resolution in ('DISMISS', 'REMOVE_CONTENT', 'SUSPEND_USER')),
    resolved_by uuid references auth.users (id) on delete set null,
    resolved_at timestamptz,
    created_at timestamptz not null default now()
);

-- Reporting the same thing twice while it is open is a no-op.
create unique index reports_open_once_idx on public.reports (reporter_id, target_kind, target_id) where status = 'OPEN';
create index reports_open_created_idx on public.reports (created_at) where status = 'OPEN';
create index reports_target_idx on public.reports (target_kind, target_id);
create index reports_target_user_idx on public.reports (target_user_id);

alter table public.reports enable row level security;
-- No policies: reports are created and read through the functions below.

create function public.report_content(
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

create function public.list_open_reports()
returns table (
    report_id uuid,
    target_kind public.report_target,
    target_id uuid,
    target_excerpt text,
    reason public.report_reason,
    details text,
    created_at timestamptz,
    report_count integer,
    reporter_username text,
    target_user_id uuid,
    target_full_name text,
    target_username text,
    target_account_status public.account_status
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
    select r.id, r.target_kind, r.target_id, r.target_excerpt, r.reason, r.details, r.created_at,
           (select count(*)::integer from public.reports x
            where x.target_kind = r.target_kind and x.target_id = r.target_id and x.status = 'OPEN'),
           rp.username, t.id, t.full_name, t.username, t.account_status
    from public.reports r
    join public.profiles rp on rp.id = r.reporter_id
    join public.profiles t on t.id = r.target_user_id
    where r.status = 'OPEN'
    order by r.created_at
    limit 200;
end;
$$;

create function public.resolve_report(p_report_id uuid, p_action text)
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

do $$
declare
    f text;
begin
    foreach f in array array[
        'public.block_user(uuid)',
        'public.unblock_user(uuid)',
        'public.list_blocked_users()',
        'public.conversation_partner(uuid)',
        'public.report_content(public.report_target, uuid, public.report_reason, text)',
        'public.list_open_reports()',
        'public.resolve_report(uuid, text)'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;
end;
$$;
