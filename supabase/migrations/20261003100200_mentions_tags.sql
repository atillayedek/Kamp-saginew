-- @mentions and #topic tags in posts and comments (product owner's request, 2026-10-03).
--
-- Nothing changes in how posts are written: the text stays as typed. After a post or comment
-- is inserted, a trigger reads it:
--   #tag      -> post_tags (folded: "#Kampüs" and "#kampus" are the same tag; posts only)
--   @username -> post_mentions / comment_mentions, and a MENTIONED notification
-- A person is only recorded (and notified) when they could see the post: approved, not blocked
-- either way with the writer (or, for comments, the post's author), and for university-only
-- posts at the same university. Writers never see these tables; the app renders the text.

-- Parsing -----------------------------------------------------------------------------------------
-- Same patterns as the app (domain/usecase/BodyLinks.kt); keep them in step.

create function public.extract_tags(p_text text)
returns text[]
language sql
immutable
parallel safe
set search_path = ''
as $$
    select coalesce(array_agg(t order by first_at), '{}')
    from (
        select t, min(ord) as first_at
        from (
            select public.search_fold(m[1]) as t, ord
            from regexp_matches(
                coalesce(p_text, ''),
                '(?<![A-Za-z0-9_çğıöşüÇĞİÖŞÜâîûÂÎÛ&#])#([A-Za-z0-9_çğıöşüÇĞİÖŞÜâîûÂÎÛ]{2,40})',
                'g'
            ) with ordinality as x (m, ord)
        ) found
        where t ~ '[a-z]'
        group by t
        order by min(ord)
        limit 10
    ) tags
$$;

-- Usernames as typed after "@", lower-cased; a trailing "." (end of sentence) is also tried without it.
create function public.extract_usernames(p_text text)
returns text[]
language sql
immutable
parallel safe
set search_path = ''
as $$
    select coalesce(array_agg(distinct u), '{}')
    from (
        select lower(m[1]) as raw
        from regexp_matches(coalesce(p_text, ''), '(?<![A-Za-z0-9_.@])@([A-Za-z0-9_.]{3,31})', 'g') as m
    ) found
    cross join lateral (values (raw), (rtrim(raw, '.'))) as v (u)
    where u ~ '^[a-z0-9_.]{3,30}$'
$$;

-- A block in either direction between two people (the caller-independent form of is_blocked_with).
create function public.blocked_between(p_a uuid, p_b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.user_blocks b
        where (b.blocker_id = p_a and b.blocked_id = p_b) or (b.blocker_id = p_b and b.blocked_id = p_a)
    )
$$;

-- Tables ------------------------------------------------------------------------------------------

create table public.post_tags (
    post_id uuid not null references public.posts (id) on delete cascade,
    tag text not null check (tag ~ '^[a-z0-9_]{2,40}$'),
    created_at timestamptz not null default now(),
    primary key (post_id, tag)
);
create index post_tags_tag_created_idx on public.post_tags (tag, created_at desc);

create table public.post_mentions (
    post_id uuid not null references public.posts (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    primary key (post_id, user_id)
);
create index post_mentions_user_idx on public.post_mentions (user_id);

create table public.comment_mentions (
    comment_id uuid not null references public.comments (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    primary key (comment_id, user_id)
);
create index comment_mentions_user_idx on public.comment_mentions (user_id);

alter table public.post_tags enable row level security;
alter table public.post_mentions enable row level security;
alter table public.comment_mentions enable row level security;
revoke all on public.post_tags, public.post_mentions, public.comment_mentions from public, anon, authenticated, service_role;

-- Recording -----------------------------------------------------------------------------------------

create function public.record_post_links()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.post_tags (post_id, tag, created_at)
    select new.id, t, new.created_at from unnest(public.extract_tags(new.body)) t;

    with targets as (
        select pr.id
        from public.profiles pr
        where pr.username = any (public.extract_usernames(new.body))
          and pr.id <> new.author_id
          and pr.account_status = 'APPROVED'
          and (new.scope = 'GENERAL' or pr.university_id = new.university_id)
          and not public.blocked_between(pr.id, new.author_id)
        limit 10
    ), mentioned as (
        insert into public.post_mentions (post_id, user_id)
        select new.id, id from targets
        returning user_id
    )
    insert into public.notifications (user_id, kind, actor_id, post_id)
    select user_id, 'MENTIONED', new.author_id, new.id from mentioned;
    return null;
end;
$$;

create trigger posts_record_links
    after insert on public.posts
    for each row execute function public.record_post_links();

create function public.record_comment_links()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_post public.posts;
begin
    select * into v_post from public.posts where id = new.post_id;
    with targets as (
        select pr.id
        from public.profiles pr
        where pr.username = any (public.extract_usernames(new.body))
          and pr.id <> new.author_id
          and pr.account_status = 'APPROVED'
          and (v_post.scope = 'GENERAL' or pr.university_id = v_post.university_id)
          and not public.blocked_between(pr.id, new.author_id)
          and not public.blocked_between(pr.id, v_post.author_id)
        limit 10
    ), mentioned as (
        insert into public.comment_mentions (comment_id, user_id)
        select new.id, id from targets
        returning user_id
    )
    -- The post's author already gets NEW_COMMENT for this comment.
    insert into public.notifications (user_id, kind, actor_id, post_id)
    select user_id, 'MENTIONED', new.author_id, new.post_id from mentioned
    where user_id <> v_post.author_id;
    return null;
end;
$$;

create trigger comments_record_links
    after insert on public.comments
    for each row execute function public.record_comment_links();

-- Reading -------------------------------------------------------------------------------------------

-- "#Kampüs", "kampus" and "KAMPUS" are the same tag.
create function public.normalize_tag(p_tag text)
returns text
language sql
immutable
parallel safe
set search_path = ''
as $$
    select public.search_fold(ltrim(btrim(coalesce(p_tag, '')), '#'))
$$;

-- Posts with a tag that the caller may see, newest first (same paging as list_posts).
create function public.list_tag_posts(
    p_tag text,
    p_before_created_at timestamptz default null,
    p_before_id uuid default null,
    p_limit integer default 20
)
returns table (
    id uuid, scope public.post_scope, category public.post_category, body text, created_at timestamptz,
    like_count integer, comment_count integer, liked_by_me boolean, is_mine boolean,
    author_id uuid, author_full_name text, author_username text, author_university text,
    saved_by_me boolean, media text[], poll jsonb, event jsonb, listing jsonb
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_student record;
    v_tag text := public.normalize_tag(p_tag);
    v_ids uuid[];
begin
    select * into v_student from public.current_student();
    if not found then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if v_tag !~ '^[a-z0-9_]{2,40}$' then
        raise exception using errcode = 'P0001', message = 'invalid_tag';
    end if;
    select array_agg(q.id order by q.created_at desc, q.id desc) into v_ids
    from (
        select p.id, p.created_at
        from public.post_tags t
        join public.posts p on p.id = t.post_id
        where t.tag = v_tag
          and p.deleted_at is null
          and (p.scope = 'GENERAL' or p.university_id = v_student.university_id)
          and not public.is_blocked_with(p.author_id)
          and (p_before_created_at is null
               or (p.created_at, p.id) < (p_before_created_at, coalesce(p_before_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)))
        order by p.created_at desc, p.id desc
        limit least(greatest(coalesce(p_limit, 20), 1), 50)
    ) q;
    return query select * from public.post_details(coalesce(v_ids, '{}'));
end;
$$;

-- Most used tags of the last seven days among posts the caller may see.
create function public.popular_tags(p_limit integer default 10)
returns table (tag text, post_count bigint)
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
    select t.tag, count(*)
    from public.post_tags t
    join public.posts p on p.id = t.post_id
    where t.created_at > now() - interval '7 days'
      and p.deleted_at is null
      and (p.scope = 'GENERAL' or p.university_id = v_student.university_id)
      and not public.is_blocked_with(p.author_id)
    group by t.tag
    order by count(*) desc, t.tag
    limit least(greatest(coalesce(p_limit, 10), 1), 30);
end;
$$;

-- People to offer while typing "@": approved, not blocked, not the caller; for university-only
-- posts, classmates only. Other students see the masked name.
create function public.suggest_mentions(p_query text, p_scope public.post_scope default 'GENERAL')
returns table (user_id uuid, username text, display_name text, university text)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_student record;
    v_query text := lower(ltrim(btrim(coalesce(p_query, '')), '@'));
begin
    select * into v_student from public.current_student();
    if not found then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if char_length(v_query) not between 1 and 30 then
        return;
    end if;
    return query
    select p.id, p.username, p.public_name, u.name
    from public.profiles p
    left join public.universities u on u.id = p.university_id
    where p.account_status = 'APPROVED'
      and p.username is not null
      and p.id <> v_student.id
      and (coalesce(p_scope, 'GENERAL') = 'GENERAL' or p.university_id = v_student.university_id)
      and not public.is_blocked_with(p.id)
      and (p.username like replace(replace(replace(v_query, '\', '\\'), '%', '\%'), '_', '\_') || '%'
           or public.search_fold(p.full_name) like public.search_pattern(v_query))
    order by (p.username like replace(replace(replace(v_query, '\', '\\'), '%', '\%'), '_', '\_') || '%') desc,
             (p.university_id = v_student.university_id) desc,
             p.username
    limit 8;
end;
$$;

-- Opening "@username" from a post: the profile id, under the same rules as get_user_profile.
create function public.resolve_username(p_username text)
returns uuid
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_name text := lower(ltrim(btrim(coalesce(p_username, '')), '@'));
    v_id uuid;
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    select p.id into v_id
    from public.profiles p
    where p.username in (v_name, rtrim(v_name, '.'))
      and p.account_status = 'APPROVED'
      and (p.id = auth.uid() or not public.is_blocked_with(p.id))
    order by p.username = v_name desc
    limit 1;
    if v_id is null then
        raise exception using errcode = 'P0001', message = 'user_not_found';
    end if;
    return v_id;
end;
$$;

-- Grants ----------------------------------------------------------------------------------------------

revoke all on function public.extract_tags(text) from public, anon, authenticated;
revoke all on function public.extract_usernames(text) from public, anon, authenticated;
revoke all on function public.blocked_between(uuid, uuid) from public, anon, authenticated;
revoke all on function public.record_post_links() from public, anon, authenticated;
revoke all on function public.record_comment_links() from public, anon, authenticated;
revoke all on function public.normalize_tag(text) from public, anon;
grant execute on function public.normalize_tag(text) to authenticated;
revoke all on function public.list_tag_posts(text, timestamptz, uuid, integer) from public, anon;
grant execute on function public.list_tag_posts(text, timestamptz, uuid, integer) to authenticated;
revoke all on function public.popular_tags(integer) from public, anon;
grant execute on function public.popular_tags(integer) to authenticated;
revoke all on function public.suggest_mentions(text, public.post_scope) from public, anon;
grant execute on function public.suggest_mentions(text, public.post_scope) to authenticated;
revoke all on function public.resolve_username(text) from public, anon;
grant execute on function public.resolve_username(text) to authenticated;

-- Existing posts get their tags and mentions recorded (no notifications for old posts).
insert into public.post_tags (post_id, tag, created_at)
select p.id, t, p.created_at
from public.posts p
cross join lateral unnest(public.extract_tags(p.body)) t
on conflict do nothing;
