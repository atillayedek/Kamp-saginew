-- Communities: posts, comments and likes for verified students.
--
-- Visibility
--   GENERAL posts: every APPROVED student.
--   UNIVERSITY posts: APPROVED students of the same university.
-- Every read and write requires an APPROVED account. Writes go through
-- SECURITY DEFINER functions so authorship, university and counters can never
-- be set by the client; RLS still guards direct table access.

create type public.post_scope as enum ('GENERAL', 'UNIVERSITY');

-- The caller's profile when it is an approved student, else no row.
create function public.current_student()
returns table (id uuid, university_id uuid)
language sql
stable
security definer
set search_path = ''
as $$
    select p.id, p.university_id
    from public.profiles p
    where p.id = auth.uid() and p.account_status = 'APPROVED'
$$;

revoke all on function public.current_student() from public, anon;
grant execute on function public.current_student() to authenticated;

create table public.posts (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null references public.profiles (id) on delete cascade,
    scope public.post_scope not null,
    university_id uuid references public.universities (id) on delete restrict,
    body text not null check (char_length(btrim(body)) between 1 and 2000),
    like_count integer not null default 0 check (like_count >= 0),
    comment_count integer not null default 0 check (comment_count >= 0),
    created_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint posts_university_matches_scope
        check ((scope = 'UNIVERSITY') = (university_id is not null))
);

create index posts_general_feed_idx on public.posts (created_at desc, id desc)
    where scope = 'GENERAL' and deleted_at is null;
create index posts_university_feed_idx on public.posts (university_id, created_at desc, id desc)
    where scope = 'UNIVERSITY' and deleted_at is null;
create index posts_author_created_idx on public.posts (author_id, created_at desc);

create table public.comments (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.posts (id) on delete cascade,
    author_id uuid not null references public.profiles (id) on delete cascade,
    body text not null check (char_length(btrim(body)) between 1 and 1000),
    created_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index comments_post_created_idx on public.comments (post_id, created_at, id) where deleted_at is null;
create index comments_author_created_idx on public.comments (author_id, created_at desc);

create table public.post_likes (
    post_id uuid not null references public.posts (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

create index post_likes_user_idx on public.post_likes (user_id);

-- Whether the caller may see a post (approved, not deleted, scope allows it).
create function public.can_see_post(p_post_id uuid)
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
    )
$$;

revoke all on function public.can_see_post(uuid) from public, anon;
grant execute on function public.can_see_post(uuid) to authenticated;

alter table public.posts enable row level security;
alter table public.comments enable row level security;
alter table public.post_likes enable row level security;

create policy posts_select_visible on public.posts
    for select to authenticated
    using ((select public.can_see_post(id)));

create policy comments_select_visible on public.comments
    for select to authenticated
    using (deleted_at is null and (select public.can_see_post(post_id)));

create policy post_likes_select_own on public.post_likes
    for select to authenticated
    using (user_id = (select auth.uid()));

-- Counters -------------------------------------------------------------------

create function public.maintain_post_counters()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if tg_table_name = 'post_likes' then
        if tg_op = 'INSERT' then
            update public.posts set like_count = like_count + 1 where id = new.post_id;
        else
            update public.posts set like_count = greatest(like_count - 1, 0) where id = old.post_id;
        end if;
    elsif tg_op = 'INSERT' then
        update public.posts set comment_count = comment_count + 1 where id = new.post_id;
    elsif tg_op = 'UPDATE' and old.deleted_at is null and new.deleted_at is not null then
        update public.posts set comment_count = greatest(comment_count - 1, 0) where id = new.post_id;
    elsif tg_op = 'DELETE' and old.deleted_at is null then
        -- Rows removed by cascade (account deletion).
        update public.posts set comment_count = greatest(comment_count - 1, 0) where id = old.post_id;
    end if;
    return null;
end;
$$;

create trigger post_likes_counter
    after insert or delete on public.post_likes
    for each row execute function public.maintain_post_counters();

create trigger comments_counter
    after insert or update of deleted_at or delete on public.comments
    for each row execute function public.maintain_post_counters();

-- Rate limits ---------------------------------------------------------------------

create function public.enforce_rate_limit(p_table regclass, p_author uuid, p_limit integer, p_window interval)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_count integer;
begin
    execute format(
        'select count(*) from %s where author_id = $1 and created_at > now() - $2',
        p_table
    ) into v_count using p_author, p_window;
    if v_count >= p_limit then
        raise exception using errcode = 'P0001', message = 'rate_limited';
    end if;
end;
$$;

revoke all on function public.enforce_rate_limit(regclass, uuid, integer, interval) from public, anon, authenticated;

-- API --------------------------------------------------------------------------------

create function public.create_post(p_scope public.post_scope, p_body text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_student record;
    v_body text := btrim(coalesce(p_body, ''));
    v_id uuid;
begin
    select * into v_student from public.current_student();
    if not found then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if p_scope is null then
        raise exception using errcode = 'P0001', message = 'invalid_scope';
    end if;
    if char_length(v_body) not between 1 and 2000 then
        raise exception using errcode = 'P0001', message = 'invalid_post_body';
    end if;
    perform public.enforce_rate_limit('public.posts', v_student.id, 10, interval '1 hour');

    insert into public.posts (author_id, scope, university_id, body)
    values (
        v_student.id,
        p_scope,
        case when p_scope = 'UNIVERSITY' then v_student.university_id end,
        v_body
    )
    returning id into v_id;
    return v_id;
end;
$$;

create function public.delete_post(p_post_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    update public.posts
    set deleted_at = now()
    where id = p_post_id and author_id = auth.uid() and deleted_at is null;
    if not found then
        raise exception using errcode = 'P0001', message = 'post_not_found';
    end if;
end;
$$;

create function public.add_comment(p_post_id uuid, p_body text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_body text := btrim(coalesce(p_body, ''));
    v_id uuid;
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if not public.can_see_post(p_post_id) then
        raise exception using errcode = 'P0001', message = 'post_not_found';
    end if;
    if char_length(v_body) not between 1 and 1000 then
        raise exception using errcode = 'P0001', message = 'invalid_comment_body';
    end if;
    perform public.enforce_rate_limit('public.comments', auth.uid(), 60, interval '1 hour');

    insert into public.comments (post_id, author_id, body)
    values (p_post_id, auth.uid(), v_body)
    returning id into v_id;
    return v_id;
end;
$$;

create function public.delete_comment(p_comment_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    update public.comments
    set deleted_at = now()
    where id = p_comment_id and author_id = auth.uid() and deleted_at is null;
    if not found then
        raise exception using errcode = 'P0001', message = 'comment_not_found';
    end if;
end;
$$;

-- Idempotent: liking twice or unliking a post that was not liked is not an error.
create function public.set_post_like(p_post_id uuid, p_liked boolean)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_count integer;
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if not public.can_see_post(p_post_id) then
        raise exception using errcode = 'P0001', message = 'post_not_found';
    end if;
    if p_liked then
        insert into public.post_likes (post_id, user_id) values (p_post_id, auth.uid())
        on conflict do nothing;
    else
        delete from public.post_likes where post_id = p_post_id and user_id = auth.uid();
    end if;
    select like_count into v_count from public.posts where id = p_post_id;
    return v_count;
end;
$$;

-- Feed page, newest first, keyset-paginated by (created_at, id).
create function public.list_posts(
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
      and (p_before_created_at is null
           or (p.created_at, p.id) < (p_before_created_at, coalesce(p_before_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)))
    order by p.created_at desc, p.id desc
    limit least(greatest(coalesce(p_limit, 20), 1), 50);
end;
$$;

create function public.get_post(p_post_id uuid)
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
begin
    if not public.can_see_post(p_post_id) then
        raise exception using errcode = 'P0001', message = 'post_not_found';
    end if;
    return query
    select p.id, p.scope, p.body, p.created_at, p.like_count, p.comment_count,
           exists (select 1 from public.post_likes l where l.post_id = p.id and l.user_id = auth.uid()),
           p.author_id = auth.uid(),
           a.id, a.full_name, a.username, u.name
    from public.posts p
    join public.profiles a on a.id = p.author_id
    left join public.universities u on u.id = a.university_id
    where p.id = p_post_id;
end;
$$;

create function public.list_comments(p_post_id uuid)
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
    order by c.created_at, c.id
    limit 500;
end;
$$;

do $$
declare
    f text;
begin
    foreach f in array array[
        'public.create_post(public.post_scope, text)',
        'public.delete_post(uuid)',
        'public.add_comment(uuid, text)',
        'public.delete_comment(uuid)',
        'public.set_post_like(uuid, boolean)',
        'public.list_posts(public.post_scope, timestamptz, uuid, integer)',
        'public.get_post(uuid)',
        'public.list_comments(uuid)'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;
end;
$$;

revoke all on function public.maintain_post_counters() from public, anon, authenticated;
