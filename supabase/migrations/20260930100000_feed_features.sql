-- Feed features: photos on posts, polls, events, marketplace listings, saved
-- posts, search and other people's profiles.
--
-- Photos live in the private `post-media` bucket. The author uploads into
-- their own folder first and then names the files in create_post, which checks
-- that each file exists, is theirs and is not attached to another post. A photo
-- is readable by whoever can see its post, and by its owner.
--
-- Every post-shaped result (feed, detail, saved, search, events, a person's
-- posts) comes from post_details(), so all of them carry the same fields and
-- the same visibility rules.

-- Turkish-insensitive text for search: "İstanbul", "istanbul" and "ISTANBUL" match.
create function public.search_fold(p_text text)
returns text
language sql
immutable
parallel safe
set search_path = ''
as $$
    select lower(translate(coalesce(p_text, ''), 'İIıÇçĞğÖöŞşÜüÂâÎîÛû', 'iiiccggoossuuaaiiuu'))
$$;

-- LIKE pattern that matches the folded query literally anywhere in the text.
create function public.search_pattern(p_query text)
returns text
language sql
immutable
parallel safe
set search_path = ''
as $$
    select '%' || replace(replace(replace(public.search_fold(btrim(p_query)), '\', '\\'), '%', '\%'), '_', '\_') || '%'
$$;

-- Photos ---------------------------------------------------------------------------

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('post-media', 'post-media', false, 3145728, array['image/jpeg'])
on conflict (id) do update
    set public = false,
        file_size_limit = excluded.file_size_limit,
        allowed_mime_types = excluded.allowed_mime_types;

create table public.post_media (
    path text primary key check (path ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$'),
    post_id uuid not null references public.posts (id) on delete cascade,
    position smallint not null check (position between 0 and 3),
    unique (post_id, position)
);

alter table public.post_media enable row level security;

create policy post_media_select_visible on public.post_media
    for select to authenticated
    using ((select public.can_see_post(post_id)));

create policy post_media_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'post-media'
        and (storage.foldername(name))[1] = (select auth.uid())::text
        and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$'
    );

create policy post_media_select_visible on storage.objects
    for select to authenticated
    using (
        bucket_id = 'post-media'
        and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or exists (select 1 from public.post_media m where m.path = name and public.can_see_post(m.post_id))
        )
    );

create policy post_media_delete_own on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'post-media'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

-- Polls ------------------------------------------------------------------------------

create table public.polls (
    id uuid primary key default gen_random_uuid(),
    post_id uuid unique references public.posts (id) on delete cascade,
    closes_at timestamptz,
    created_at timestamptz not null default now(),
    constraint polls_has_owner check (post_id is not null)
);

create table public.poll_options (
    id uuid primary key default gen_random_uuid(),
    poll_id uuid not null references public.polls (id) on delete cascade,
    position smallint not null check (position between 0 and 3),
    label text not null check (char_length(btrim(label)) between 1 and 80),
    vote_count integer not null default 0 check (vote_count >= 0),
    unique (poll_id, position)
);

create table public.poll_votes (
    poll_id uuid not null references public.polls (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    option_id uuid not null references public.poll_options (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (poll_id, user_id)
);

create index poll_options_poll_idx on public.poll_options (poll_id);
create index poll_votes_option_idx on public.poll_votes (option_id);
create index poll_votes_user_idx on public.poll_votes (user_id);

alter table public.polls enable row level security;
alter table public.poll_options enable row level security;
alter table public.poll_votes enable row level security;
-- No policies: polls are read through post_details() and voted on with vote_poll().

create function public.maintain_poll_counts()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if tg_op in ('DELETE', 'UPDATE') then
        update public.poll_options set vote_count = greatest(vote_count - 1, 0) where id = old.option_id;
    end if;
    if tg_op in ('INSERT', 'UPDATE') then
        update public.poll_options set vote_count = vote_count + 1 where id = new.option_id;
    end if;
    return null;
end;
$$;

create trigger poll_votes_count
    after insert or delete or update of option_id on public.poll_votes
    for each row execute function public.maintain_poll_counts();

-- Whether the caller may see (and vote in) a poll. Extended when polls can
-- belong to something other than a post.
create function public.can_see_poll(p_poll_id uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_post uuid;
begin
    select post_id into v_post from public.polls where id = p_poll_id;
    return v_post is not null and public.can_see_post(v_post);
end;
$$;

-- The poll as the app shows it: options in order, counts and the caller's vote.
create function public.poll_json(p_poll_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select jsonb_build_object(
        'id', p.id,
        'closes_at', p.closes_at,
        'total_votes', (select coalesce(sum(o.vote_count), 0) from public.poll_options o where o.poll_id = p.id),
        'my_option_id', (select v.option_id from public.poll_votes v where v.poll_id = p.id and v.user_id = auth.uid()),
        'options', (
            select jsonb_agg(jsonb_build_object('id', o.id, 'label', o.label, 'votes', o.vote_count) order by o.position)
            from public.poll_options o where o.poll_id = p.id
        )
    )
    from public.polls p
    where p.id = p_poll_id
$$;

-- Creates a poll's options; the caller has validated them already.
create function public.insert_poll_options(p_poll_id uuid, p_options text[])
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.poll_options (poll_id, position, label)
    select p_poll_id, (o.ord - 1)::smallint, btrim(o.label)
    from unnest(p_options) with ordinality as o (label, ord)
$$;

-- Options must be 2 to 4 distinct, non-empty labels of at most 80 characters.
create function public.valid_poll_options(p_options text[])
returns boolean
language sql
immutable
set search_path = ''
as $$
    select cardinality(p_options) between 2 and 4
       and (select bool_and(char_length(btrim(coalesce(x, ''))) between 1 and 80) from unnest(p_options) x)
       and (select count(distinct public.search_fold(btrim(x))) from unnest(p_options) x) = cardinality(p_options)
$$;

-- Votes, changes the vote, or withdraws it when p_option_id is null.
create function public.vote_poll(p_poll_id uuid, p_option_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_closes timestamptz;
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if not public.can_see_poll(p_poll_id) then
        raise exception using errcode = 'P0001', message = 'poll_not_found';
    end if;
    select closes_at into v_closes from public.polls where id = p_poll_id;
    if v_closes is not null and v_closes <= now() then
        raise exception using errcode = 'P0001', message = 'poll_closed';
    end if;

    if p_option_id is null then
        delete from public.poll_votes where poll_id = p_poll_id and user_id = auth.uid();
    else
        if not exists (select 1 from public.poll_options where id = p_option_id and poll_id = p_poll_id) then
            raise exception using errcode = 'P0001', message = 'invalid_poll_option';
        end if;
        insert into public.poll_votes (poll_id, user_id, option_id)
        values (p_poll_id, auth.uid(), p_option_id)
        on conflict (poll_id, user_id) do update set option_id = excluded.option_id, created_at = now()
        where public.poll_votes.option_id <> excluded.option_id;
    end if;
    return public.poll_json(p_poll_id);
end;
$$;

-- Events ------------------------------------------------------------------------------

create table public.post_events (
    post_id uuid primary key references public.posts (id) on delete cascade,
    starts_at timestamptz not null,
    ends_at timestamptz,
    location text check (location is null or char_length(btrim(location)) between 1 and 120),
    attendee_count integer not null default 0 check (attendee_count >= 0),
    constraint post_events_ends_after_start check (ends_at is null or ends_at > starts_at)
);

create index post_events_starts_idx on public.post_events (starts_at);

create table public.event_attendees (
    post_id uuid not null references public.post_events (post_id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

create index event_attendees_user_idx on public.event_attendees (user_id);

alter table public.post_events enable row level security;
alter table public.event_attendees enable row level security;
-- No policies: read through post_details(), changed with set_event_attendance().

create function public.maintain_event_attendees()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if tg_op = 'INSERT' then
        update public.post_events set attendee_count = attendee_count + 1 where post_id = new.post_id;
    else
        update public.post_events set attendee_count = greatest(attendee_count - 1, 0) where post_id = old.post_id;
    end if;
    return null;
end;
$$;

create trigger event_attendees_count
    after insert or delete on public.event_attendees
    for each row execute function public.maintain_event_attendees();

create function public.set_event_attendance(p_post_id uuid, p_attending boolean)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_event public.post_events%rowtype;
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    select * into v_event from public.post_events where post_id = p_post_id;
    if not found or not public.can_see_post(p_post_id) then
        raise exception using errcode = 'P0001', message = 'event_not_found';
    end if;
    if p_attending then
        if coalesce(v_event.ends_at, v_event.starts_at + interval '6 hours') < now() then
            raise exception using errcode = 'P0001', message = 'event_ended';
        end if;
        insert into public.event_attendees (post_id, user_id) values (p_post_id, auth.uid())
        on conflict do nothing;
    else
        delete from public.event_attendees where post_id = p_post_id and user_id = auth.uid();
    end if;
    return (select attendee_count from public.post_events where post_id = p_post_id);
end;
$$;

-- Marketplace listings ----------------------------------------------------------------

create table public.post_listings (
    post_id uuid primary key references public.posts (id) on delete cascade,
    -- Price in kuruş; 0 means free.
    price_kurus bigint not null check (price_kurus between 0 and 1000000000),
    sold_at timestamptz
);

alter table public.post_listings enable row level security;
-- No policies: read through post_details(), changed by the seller with set_listing_sold().

create function public.set_listing_sold(p_post_id uuid, p_sold boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    update public.post_listings l
    set sold_at = case when p_sold then coalesce(l.sold_at, now()) end
    from public.posts p
    where l.post_id = p_post_id and p.id = l.post_id and p.author_id = auth.uid() and p.deleted_at is null;
    if not found then
        raise exception using errcode = 'P0001', message = 'listing_not_found';
    end if;
end;
$$;

-- Saved posts ---------------------------------------------------------------------------

create table public.saved_posts (
    user_id uuid not null references public.profiles (id) on delete cascade,
    post_id uuid not null references public.posts (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, post_id)
);

create index saved_posts_post_idx on public.saved_posts (post_id);
create index saved_posts_user_created_idx on public.saved_posts (user_id, created_at desc);

alter table public.saved_posts enable row level security;

create policy saved_posts_select_own on public.saved_posts
    for select to authenticated
    using (user_id = (select auth.uid()));

create function public.set_post_saved(p_post_id uuid, p_saved boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if p_saved then
        if not public.can_see_post(p_post_id) then
            raise exception using errcode = 'P0001', message = 'post_not_found';
        end if;
        if (select count(*) from public.saved_posts where user_id = auth.uid()) >= 1000 then
            raise exception using errcode = 'P0001', message = 'too_many_saved_posts';
        end if;
        insert into public.saved_posts (user_id, post_id) values (auth.uid(), p_post_id)
        on conflict do nothing;
    else
        delete from public.saved_posts where user_id = auth.uid() and post_id = p_post_id;
    end if;
end;
$$;

-- Post rows ------------------------------------------------------------------------------

-- Every field the app shows for the given posts, in the order of p_ids.
-- Posts the caller may not see are left out.
create function public.post_details(p_ids uuid[])
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
    author_university text,
    saved_by_me boolean,
    media text[],
    poll jsonb,
    event jsonb,
    listing jsonb
)
language sql
stable
security definer
set search_path = ''
as $$
    select p.id, p.scope, p.category, p.body, p.created_at, p.like_count, p.comment_count,
           exists (select 1 from public.post_likes l where l.post_id = p.id and l.user_id = auth.uid()),
           p.author_id = auth.uid(),
           a.id, a.full_name, a.username, u.name,
           exists (select 1 from public.saved_posts s where s.post_id = p.id and s.user_id = auth.uid()),
           coalesce((select array_agg(m.path order by m.position) from public.post_media m where m.post_id = p.id), '{}'),
           (select public.poll_json(pl.id) from public.polls pl where pl.post_id = p.id),
           (select jsonb_build_object(
                'starts_at', e.starts_at,
                'ends_at', e.ends_at,
                'location', e.location,
                'attendee_count', e.attendee_count,
                'attending', exists (select 1 from public.event_attendees x where x.post_id = e.post_id and x.user_id = auth.uid())
            ) from public.post_events e where e.post_id = p.id),
           (select jsonb_build_object('price_kurus', li.price_kurus, 'sold', li.sold_at is not null)
            from public.post_listings li where li.post_id = p.id)
    from unnest(p_ids) with ordinality as ids (post_id, ord)
    join public.posts p on p.id = ids.post_id
    join public.profiles a on a.id = p.author_id
    left join public.universities u on u.id = a.university_id
    where public.can_see_post(p.id)
    order by ids.ord
$$;

-- Creating posts -------------------------------------------------------------------------

drop function public.create_post(public.post_scope, text, public.post_category);

create function public.create_post(
    p_scope public.post_scope,
    p_body text,
    p_category public.post_category default 'GENERAL',
    p_media_paths text[] default null,
    p_poll_options text[] default null,
    p_poll_closes_at timestamptz default null,
    p_event_starts_at timestamptz default null,
    p_event_ends_at timestamptz default null,
    p_event_location text default null,
    p_price_kurus bigint default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_student record;
    v_body text := btrim(coalesce(p_body, ''));
    v_category public.post_category := coalesce(p_category, 'GENERAL');
    v_media text[] := coalesce(p_media_paths, '{}');
    v_location text := nullif(btrim(coalesce(p_event_location, '')), '');
    v_id uuid;
    v_poll uuid;
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

    -- Photos: up to four of the author's own, uploaded and not used elsewhere.
    if cardinality(v_media) > 4
        or cardinality(v_media) <> (select count(distinct x) from unnest(v_media) x)
        or exists (
            select 1 from unnest(v_media) x
            where x is null
               or x !~ ('^' || v_student.id::text || '/[0-9a-f-]{36}\.jpg$')
               or not exists (select 1 from storage.objects o where o.bucket_id = 'post-media' and o.name = x)
               or exists (select 1 from public.post_media m where m.path = x)
        ) then
        raise exception using errcode = 'P0001', message = 'invalid_media';
    end if;

    if p_poll_options is not null and (
        not public.valid_poll_options(p_poll_options)
        or (p_poll_closes_at is not null and (p_poll_closes_at <= now() or p_poll_closes_at > now() + interval '30 days'))
    ) then
        raise exception using errcode = 'P0001', message = 'invalid_poll';
    end if;
    if p_poll_options is null and p_poll_closes_at is not null then
        raise exception using errcode = 'P0001', message = 'invalid_poll';
    end if;

    if (p_event_starts_at is not null or p_event_ends_at is not null or v_location is not null) and (
        v_category <> 'EVENT'
        or p_event_starts_at is null
        or p_event_starts_at < now() - interval '1 hour'
        or p_event_starts_at > now() + interval '1 year'
        or (p_event_ends_at is not null and (p_event_ends_at <= p_event_starts_at or p_event_ends_at > p_event_starts_at + interval '30 days'))
        or char_length(coalesce(v_location, '')) > 120
    ) then
        raise exception using errcode = 'P0001', message = 'invalid_event';
    end if;

    if p_price_kurus is not null and (v_category <> 'MARKETPLACE' or p_price_kurus not between 0 and 1000000000) then
        raise exception using errcode = 'P0001', message = 'invalid_price';
    end if;

    perform public.enforce_rate_limit('public.posts', v_student.id, 10, interval '1 hour');

    insert into public.posts (author_id, scope, university_id, body, category)
    values (
        v_student.id,
        p_scope,
        case when p_scope = 'UNIVERSITY' then v_student.university_id end,
        v_body,
        v_category
    )
    returning id into v_id;

    insert into public.post_media (path, post_id, position)
    select x, v_id, (ord - 1)::smallint from unnest(v_media) with ordinality as m (x, ord);

    if p_poll_options is not null then
        insert into public.polls (post_id, closes_at) values (v_id, p_poll_closes_at) returning id into v_poll;
        perform public.insert_poll_options(v_poll, p_poll_options);
    end if;

    if p_event_starts_at is not null then
        insert into public.post_events (post_id, starts_at, ends_at, location)
        values (v_id, p_event_starts_at, p_event_ends_at, v_location);
    end if;

    if p_price_kurus is not null then
        insert into public.post_listings (post_id, price_kurus) values (v_id, p_price_kurus);
    end if;
    return v_id;
end;
$$;

-- Reading posts ---------------------------------------------------------------------------

drop function public.list_posts(public.post_scope, timestamptz, uuid, integer, public.post_category);
drop function public.get_post(uuid);

create function public.list_posts(
    p_scope public.post_scope,
    p_before_created_at timestamptz default null,
    p_before_id uuid default null,
    p_limit integer default 20,
    p_category public.post_category default null
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
    v_ids uuid[];
begin
    select * into v_student from public.current_student();
    if not found then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    select array_agg(q.id order by q.created_at desc, q.id desc) into v_ids
    from (
        select p.id, p.created_at
        from public.posts p
        where p.deleted_at is null
          and p.scope = p_scope
          and (p_category is null or p.category = p_category)
          and (p_scope = 'GENERAL' or p.university_id = v_student.university_id)
          and not public.is_blocked_with(p.author_id)
          and (p_before_created_at is null
               or (p.created_at, p.id) < (p_before_created_at, coalesce(p_before_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)))
        order by p.created_at desc, p.id desc
        limit least(greatest(coalesce(p_limit, 20), 1), 50)
    ) q;
    return query select * from public.post_details(coalesce(v_ids, '{}'));
end;
$$;

create function public.get_post(p_post_id uuid)
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
begin
    if not public.can_see_post(p_post_id) then
        raise exception using errcode = 'P0001', message = 'post_not_found';
    end if;
    return query select * from public.post_details(array[p_post_id]);
end;
$$;

create function public.list_saved_posts()
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
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    return query select * from public.post_details(coalesce((
        select array_agg(s.post_id order by s.created_at desc, s.post_id desc)
        from (select sp.post_id, sp.created_at from public.saved_posts sp
              where sp.user_id = auth.uid() order by sp.created_at desc, sp.post_id desc limit 200) s
    ), '{}'));
end;
$$;

-- Events that have not ended, soonest first.
create function public.list_upcoming_events()
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
begin
    select * into v_student from public.current_student();
    if not found then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    return query select * from public.post_details(coalesce((
        select array_agg(q.post_id order by q.starts_at, q.post_id)
        from (
            select e.post_id, e.starts_at
            from public.post_events e
            join public.posts p on p.id = e.post_id
            where p.deleted_at is null
              and (p.scope = 'GENERAL' or p.university_id = v_student.university_id)
              and not public.is_blocked_with(p.author_id)
              and coalesce(e.ends_at, e.starts_at + interval '6 hours') >= now()
            order by e.starts_at, e.post_id
            limit 100
        ) q
    ), '{}'));
end;
$$;

-- Search ------------------------------------------------------------------------------------

create function public.search_posts(p_query text)
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
begin
    select * into v_student from public.current_student();
    if not found then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    if char_length(btrim(coalesce(p_query, ''))) not between 2 and 64 then
        raise exception using errcode = 'P0001', message = 'invalid_query';
    end if;
    return query select * from public.post_details(coalesce((
        select array_agg(q.id order by q.created_at desc, q.id desc)
        from (
            select p.id, p.created_at
            from public.posts p
            where p.deleted_at is null
              and (p.scope = 'GENERAL' or p.university_id = v_student.university_id)
              and not public.is_blocked_with(p.author_id)
              and public.search_fold(p.body) like public.search_pattern(p_query)
            order by p.created_at desc, p.id desc
            limit 30
        ) q
    ), '{}'));
end;
$$;

create function public.search_people(p_query text)
returns table (
    user_id uuid,
    full_name text,
    username text,
    university text,
    department text
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
    if char_length(btrim(coalesce(p_query, ''))) not between 2 and 64 then
        raise exception using errcode = 'P0001', message = 'invalid_query';
    end if;
    return query
    select p.id, p.full_name, p.username, u.name, p.department
    from public.profiles p
    left join public.universities u on u.id = p.university_id
    where p.account_status = 'APPROVED'
      and not public.is_blocked_with(p.id)
      and (public.search_fold(p.full_name) like public.search_pattern(p_query)
           or public.search_fold(p.username) like public.search_pattern(p_query))
    order by (public.search_fold(p.username) = public.search_fold(btrim(p_query))) desc, p.full_name, p.id
    limit 30;
end;
$$;

-- Other people's profiles -----------------------------------------------------------------------

create function public.get_user_profile(p_user_id uuid)
returns table (
    user_id uuid,
    full_name text,
    username text,
    university text,
    department text,
    bio text,
    joined_at timestamptz,
    post_count bigint,
    is_me boolean
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
    if not exists (select 1 from public.profiles where id = p_user_id and account_status = 'APPROVED')
        or (p_user_id <> v_student.id and public.is_blocked_with(p_user_id)) then
        raise exception using errcode = 'P0001', message = 'user_not_found';
    end if;
    return query
    select p.id, p.full_name, p.username, u.name, p.department, p.bio, p.created_at,
           (select count(*) from public.posts x
            where x.author_id = p.id and x.deleted_at is null
              and (x.scope = 'GENERAL' or x.university_id = v_student.university_id)),
           p.id = v_student.id
    from public.profiles p
    left join public.universities u on u.id = p.university_id
    where p.id = p_user_id;
end;
$$;

create function public.list_user_posts(
    p_user_id uuid,
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
begin
    select * into v_student from public.current_student();
    if not found then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;
    return query select * from public.post_details(coalesce((
        select array_agg(q.id order by q.created_at desc, q.id desc)
        from (
            select p.id, p.created_at
            from public.posts p
            where p.author_id = p_user_id
              and p.deleted_at is null
              and (p.scope = 'GENERAL' or p.university_id = v_student.university_id)
              and not public.is_blocked_with(p.author_id)
              and (p_before_created_at is null
                   or (p.created_at, p.id) < (p_before_created_at, coalesce(p_before_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)))
            order by p.created_at desc, p.id desc
            limit least(greatest(coalesce(p_limit, 20), 1), 50)
        ) q
    ), '{}'));
end;
$$;

-- Grants -----------------------------------------------------------------------------------------

do $$
declare
    f text;
begin
    foreach f in array array[
        'public.create_post(public.post_scope, text, public.post_category, text[], text[], timestamptz, timestamptz, timestamptz, text, bigint)',
        'public.list_posts(public.post_scope, timestamptz, uuid, integer, public.post_category)',
        'public.get_post(uuid)',
        'public.vote_poll(uuid, uuid)',
        'public.set_event_attendance(uuid, boolean)',
        'public.set_listing_sold(uuid, boolean)',
        'public.set_post_saved(uuid, boolean)',
        'public.list_saved_posts()',
        'public.list_upcoming_events()',
        'public.search_posts(text)',
        'public.search_people(text)',
        'public.get_user_profile(uuid)',
        'public.list_user_posts(uuid, timestamptz, uuid, integer)'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;
    -- Internal helpers: only the functions above call them.
    foreach f in array array[
        'public.post_details(uuid[])',
        'public.poll_json(uuid)',
        'public.insert_poll_options(uuid, text[])',
        'public.can_see_poll(uuid)',
        'public.maintain_poll_counts()',
        'public.maintain_event_attendees()'
    ] loop
        execute format('revoke all on function %s from public, anon, authenticated', f);
    end loop;
    foreach f in array array[
        'public.search_fold(text)',
        'public.search_pattern(text)',
        'public.valid_poll_options(text[])'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
    end loop;
end;
$$;
