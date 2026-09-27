-- Post categories, profile bio, profile photos and profile stats.
--
-- Avatars live in a private bucket: the owner writes to their own folder, and
-- only approved students (and the owner) can read. The app downloads them with
-- the signed-in session, so photos are never public on the internet.

-- Post categories ------------------------------------------------------------------
create type public.post_category as enum (
    'GENERAL', 'QUESTION', 'STUDY', 'EVENT', 'ANNOUNCEMENT',
    'MARKETPLACE', 'HOUSING', 'LOST_FOUND', 'CAREER', 'SPORTS'
);

alter table public.posts add column category public.post_category not null default 'GENERAL';

drop function public.create_post(public.post_scope, text);

create function public.create_post(
    p_scope public.post_scope,
    p_body text,
    p_category public.post_category default 'GENERAL'
)
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

    insert into public.posts (author_id, scope, university_id, body, category)
    values (
        v_student.id,
        p_scope,
        case when p_scope = 'UNIVERSITY' then v_student.university_id end,
        v_body,
        coalesce(p_category, 'GENERAL')
    )
    returning id into v_id;
    return v_id;
end;
$$;

drop function public.list_posts(public.post_scope, timestamptz, uuid, integer);

create function public.list_posts(
    p_scope public.post_scope,
    p_before_created_at timestamptz default null,
    p_before_id uuid default null,
    p_limit integer default 20,
    p_category public.post_category default null
)
returns table (
    id uuid,
    scope public.post_scope,
    category public.post_category,
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
    select p.id, p.scope, p.category, p.body, p.created_at, p.like_count, p.comment_count,
           exists (select 1 from public.post_likes l where l.post_id = p.id and l.user_id = v_student.id),
           p.author_id = v_student.id,
           a.id, a.full_name, a.username, u.name
    from public.posts p
    join public.profiles a on a.id = p.author_id
    left join public.universities u on u.id = a.university_id
    where p.deleted_at is null
      and p.scope = p_scope
      and (p_category is null or p.category = p_category)
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

drop function public.get_post(uuid);

create function public.get_post(p_post_id uuid)
returns table (
    id uuid,
    scope public.post_scope,
    category public.post_category,
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
    select p.id, p.scope, p.category, p.body, p.created_at, p.like_count, p.comment_count,
           exists (select 1 from public.post_likes l where l.post_id = p.id and l.user_id = auth.uid()),
           p.author_id = auth.uid(),
           a.id, a.full_name, a.username, u.name
    from public.posts p
    join public.profiles a on a.id = p.author_id
    left join public.universities u on u.id = a.university_id
    where p.id = p_post_id;
end;
$$;

-- Bio and avatar ------------------------------------------------------------------
alter table public.profiles
    add column bio text check (bio is null or char_length(bio) between 1 and 300),
    add column avatar_path text check (
        avatar_path is null or avatar_path ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$'
    );

create function public.update_bio(p_bio text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_bio text := nullif(btrim(coalesce(p_bio, '')), '');
begin
    if auth.uid() is null then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;
    if v_bio is not null and char_length(v_bio) > 300 then
        raise exception using errcode = 'P0001', message = 'invalid_bio';
    end if;
    update public.profiles set bio = v_bio, updated_at = now() where id = auth.uid();
end;
$$;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', false, 2097152, array['image/jpeg'])
on conflict (id) do update
    set public = false,
        file_size_limit = excluded.file_size_limit,
        allowed_mime_types = excluded.allowed_mime_types;

create policy avatars_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'avatars'
        and (storage.foldername(name))[1] = (select auth.uid())::text
        and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$'
    );

create policy avatars_select_own_or_student on storage.objects
    for select to authenticated
    using (
        bucket_id = 'avatars'
        and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or exists (select 1 from public.current_student())
        )
    );

create policy avatars_delete_own on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'avatars'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

create function public.set_avatar(p_path text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;
    if p_path is null or p_path !~ ('^' || auth.uid()::text || '/[0-9a-f-]{36}\.jpg$') then
        raise exception using errcode = 'P0001', message = 'invalid_avatar_path';
    end if;
    if not exists (select 1 from storage.objects where bucket_id = 'avatars' and name = p_path) then
        raise exception using errcode = 'P0001', message = 'avatar_not_found';
    end if;
    update public.profiles set avatar_path = p_path, updated_at = now() where id = auth.uid();
end;
$$;

create function public.remove_avatar()
returns void
language sql
security definer
set search_path = ''
as $$
    update public.profiles set avatar_path = null, updated_at = now()
    where id = auth.uid() and avatar_path is not null;
$$;

-- Avatars for a batch of people shown on screen (feed, comments, chats, matches).
create function public.avatar_paths(p_user_ids uuid[])
returns table (user_id uuid, avatar_path text)
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
    select p.id, p.avatar_path
    from public.profiles p
    where p.id = any ((coalesce(p_user_ids, '{}'::uuid[]))[1:200])
      and p.avatar_path is not null
      and (p.id = auth.uid() or not public.is_blocked_with(p.id));
end;
$$;

-- Profile stats -------------------------------------------------------------------
create function public.my_profile_stats()
returns table (post_count bigint, active_requirement_count bigint, conversation_count bigint)
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
    select
        (select count(*) from public.posts where author_id = v_student.id and deleted_at is null),
        (select count(*) from public.requirements where owner_id = v_student.id and status = 'ACTIVE'),
        (select count(*) from public.conversation_members where user_id = v_student.id);
end;
$$;

-- Grants --------------------------------------------------------------------------
do $$
declare
    f text;
begin
    foreach f in array array[
        'public.create_post(public.post_scope, text, public.post_category)',
        'public.list_posts(public.post_scope, timestamptz, uuid, integer, public.post_category)',
        'public.get_post(uuid)',
        'public.update_bio(text)',
        'public.set_avatar(text)',
        'public.remove_avatar()',
        'public.avatar_paths(uuid[])',
        'public.my_profile_stats()'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;
end;
$$;
