-- Rule-based matching (user decision, D54): no OpenAI, no embeddings.
--
-- The student fills a form (title, optional description, category, tags,
-- place, time, people) and the app writes it through create_requirement().
-- find_matches() scores other students' ACTIVE requirements in the same
-- category and university:
--
--   score = 100 * (0.50 * shared tags + 0.25 * shared words + 0.15 * time + 0.10 * place)
--
-- Shared tags and words are set overlaps |A ∩ B| / sqrt(|A| * |B|). Words are
-- PostgreSQL's Turkish stems of title + description (so "kitaplar" meets
-- "kitap"), folded to ASCII. Tags go through requirement_synonyms, so
-- "oda arkadaşı" and "ev arkadaşı" are the same tag. Time is 1 on the same
-- moment and falls to 0 at seven days apart; place is 1 when one place name
-- contains the other. Only candidates with at least one shared tag or word and
-- a score of 15 or more are shown, with the shared tags as the reason.

-- AI pieces go ---------------------------------------------------------------------------------

drop function public.insert_requirement(
    uuid, text, text, text, public.requirement_category, text[], text, timestamptz, integer, text, text
);
drop function public.consume_ai_quota(uuid, text);
drop table public.ai_usage;
drop index public.requirements_embedding_idx;
alter table public.requirements
    drop column embedding,
    drop column embedding_model,
    drop column original_text;

-- The description is optional now; empty means "no description".
alter table public.requirements drop constraint requirements_description_check;
alter table public.requirements add constraint requirements_description_check check (char_length(description) <= 1000);

-- Plans no longer carry AI limits.
drop function public.my_subscription();
drop function public.list_plans();
drop function public.current_limits(uuid);
alter table public.subscription_plans
    drop column ai_analyze_daily,
    drop column ai_publish_daily;

create function public.current_limits(p_user_id uuid)
returns table (max_active_requirements integer)
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(max(p.max_active_requirements), 20)
    from public.active_premium(p_user_id) a
    join public.subscription_plans p on p.id = a.plan_id
$$;

create function public.list_plans()
returns table (id uuid, play_product_id text, name text, description text, max_active_requirements integer)
language sql
stable
security definer
set search_path = ''
as $$
    select p.id, p.play_product_id, p.name, p.description, p.max_active_requirements
    from public.subscription_plans p
    where p.is_active and exists (select 1 from public.current_student())
    order by p.sort_order, p.name
$$;

create function public.my_subscription()
returns table (plan_name text, play_product_id text, expires_at timestamptz, source text, max_active_requirements integer)
language sql
stable
security definer
set search_path = ''
as $$
    select b.name, b.play_product_id, b.expires_at, b.source, l.max_active_requirements
    from public.current_limits(auth.uid()) l
    left join lateral (
        select p.name, p.play_product_id, a.expires_at, a.source
        from public.active_premium(auth.uid()) a
        join public.subscription_plans p on p.id = a.plan_id
        order by a.expires_at desc
        limit 1
    ) b on true
    where exists (select 1 from public.current_student())
$$;

-- Text helpers ---------------------------------------------------------------------------------

-- Turkish lower case without depending on the database locale.
create function public.turkish_lower(p_text text)
returns text
language sql
immutable
parallel safe
set search_path = ''
as $$
    select lower(translate(coalesce(p_text, ''), 'İIÇĞÖŞÜÂÎÛ', 'iıçğöşüâîû'))
$$;

-- Word stems used for matching: Turkish stemming, stop words removed, folded to ASCII.
create function public.requirement_terms(p_title text, p_description text)
returns text[]
language sql
immutable
parallel safe
set search_path = ''
as $$
    select coalesce(array_agg(distinct public.search_fold(t.lexeme) order by public.search_fold(t.lexeme)), '{}')
    from unnest(to_tsvector('pg_catalog.turkish'::regconfig, public.turkish_lower(p_title || ' ' || p_description))) t
    where char_length(t.lexeme) >= 2
$$;

-- |A ∩ B| / sqrt(|A| * |B|); 0 when either side is empty.
create function public.set_overlap(p_a text[], p_b text[])
returns numeric
language sql
immutable
parallel safe
set search_path = ''
as $$
    select case
        when coalesce(cardinality(p_a), 0) = 0 or coalesce(cardinality(p_b), 0) = 0 then 0
        else (select count(*) from (select unnest(p_a) intersect select unnest(p_b)) s)::numeric
             / sqrt(cardinality(p_a)::numeric * cardinality(p_b))
    end
$$;

-- Synonyms ------------------------------------------------------------------------------------

-- term: folded alias; tag: the tag it becomes (display form).
create table public.requirement_synonyms (
    term text primary key check (term = public.search_fold(term) and char_length(term) between 1 and 30),
    tag text not null check (char_length(tag) between 1 and 30)
);

alter table public.requirement_synonyms enable row level security;
-- No policies: read only through requirement_tag(); managed with the service role.

insert into public.requirement_synonyms (term, tag) values
    ('oda arkadasi', 'ev arkadaşı'),
    ('ev arkadasi', 'ev arkadaşı'),
    ('evarkadasi', 'ev arkadaşı'),
    ('kiralik oda', 'ev arkadaşı'),
    ('yurt', 'yurt'),
    ('halisaha', 'futbol'),
    ('hali saha', 'futbol'),
    ('mac', 'futbol'),
    ('futbol', 'futbol'),
    ('basket', 'basketbol'),
    ('basketbol', 'basketbol'),
    ('voley', 'voleybol'),
    ('voleybol', 'voleybol'),
    ('tenis', 'tenis'),
    ('masa tenisi', 'masa tenisi'),
    ('pingpong', 'masa tenisi'),
    ('ping pong', 'masa tenisi'),
    ('kosu', 'koşu'),
    ('jogging', 'koşu'),
    ('fitness', 'spor salonu'),
    ('gym', 'spor salonu'),
    ('spor salonu', 'spor salonu'),
    ('ders calisma', 'ders çalışma'),
    ('calisma arkadasi', 'ders çalışma'),
    ('etut', 'ders çalışma'),
    ('kutuphane', 'kütüphane'),
    ('vize', 'sınav'),
    ('final', 'sınav'),
    ('sinav', 'sınav'),
    ('butunleme', 'sınav'),
    ('ozel ders', 'özel ders'),
    ('proje', 'proje'),
    ('odev', 'ödev'),
    ('bitirme projesi', 'bitirme projesi'),
    ('hackathon', 'hackathon'),
    ('yol arkadasi', 'yolculuk'),
    ('yolculuk', 'yolculuk'),
    ('arac paylasimi', 'araç paylaşımı'),
    ('ortak arac', 'araç paylaşımı'),
    ('otostop', 'araç paylaşımı'),
    ('taksi', 'taksi paylaşımı'),
    ('ikinci el', 'ikinci el'),
    ('2. el', 'ikinci el'),
    ('kitap', 'kitap'),
    ('ders kitabi', 'kitap'),
    ('not', 'ders notu'),
    ('ders notu', 'ders notu'),
    ('konser', 'konser'),
    ('festival', 'festival'),
    ('parti', 'parti'),
    ('kahve', 'kahve'),
    ('yemek', 'yemek'),
    ('sinema', 'sinema'),
    ('film', 'sinema'),
    ('oyun', 'oyun'),
    ('e-spor', 'oyun'),
    ('espor', 'oyun');

-- One tag as stored: trimmed, single-spaced, Turkish lower case, synonyms applied.
create function public.requirement_tag(p_tag text)
returns text
language sql
stable
set search_path = ''
security definer
as $$
    select coalesce(
        (select s.tag from public.requirement_synonyms s
         where s.term = public.search_fold(regexp_replace(btrim(p_tag), '\s+', ' ', 'g'))),
        public.turkish_lower(regexp_replace(btrim(p_tag), '\s+', ' ', 'g'))
    )
$$;

-- Matching keys on the row ---------------------------------------------------------------------

alter table public.requirements
    add column tag_keys text[] not null default '{}',
    add column terms text[] not null default '{}';

update public.requirements
set tags = coalesce((select array_agg(distinct public.requirement_tag(t) order by public.requirement_tag(t)) from unnest(tags) t), '{}');

update public.requirements
set tag_keys = coalesce((select array_agg(distinct public.search_fold(t)) from unnest(tags) t), '{}'),
    terms = public.requirement_terms(title, description);

create index requirements_active_category_idx on public.requirements (university_id, category) where status = 'ACTIVE';

-- create_requirement --------------------------------------------------------------------------

create function public.create_requirement(
    p_title text,
    p_description text,
    p_category public.requirement_category,
    p_tags text[],
    p_location_text text,
    p_starts_at timestamptz,
    p_participants_needed integer
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_me uuid := auth.uid();
    v_university uuid;
    v_title text := btrim(coalesce(p_title, ''));
    v_description text := btrim(coalesce(p_description, ''));
    v_tags text[];
    v_id uuid;
begin
    select s.university_id into v_university from public.current_student() s;
    if v_university is null then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;

    select coalesce(array_agg(distinct x.tag order by x.tag), '{}') into v_tags
    from (select public.requirement_tag(t) as tag from unnest(coalesce(p_tags, '{}')) t) x
    where x.tag <> '';
    if p_category is null
        or char_length(v_title) not between 3 and 120
        or char_length(v_description) > 1000
        or cardinality(v_tags) > 8
        or exists (select 1 from unnest(v_tags) t where char_length(t) > 30)
        or (p_location_text is not null and char_length(btrim(p_location_text)) > 120)
        or (p_participants_needed is not null and p_participants_needed not between 1 and 50)
        or (p_starts_at is not null and (p_starts_at < now() - interval '1 day' or p_starts_at > now() + interval '1 year'))
    then
        raise exception using errcode = 'P0001', message = 'invalid_requirement';
    end if;

    -- Serialise one person's writes so the limits cannot be raced.
    perform pg_advisory_xact_lock(hashtextextended(v_me::text || 'requirement', 0));
    if (select count(*) from public.requirements where owner_id = v_me and status = 'ACTIVE')
        >= (select l.max_active_requirements from public.current_limits(v_me) l) then
        raise exception using errcode = 'P0001', message = 'too_many_active_requirements';
    end if;
    if (select count(*) from public.requirements where owner_id = v_me and created_at > now() - interval '24 hours') >= 30 then
        raise exception using errcode = 'P0001', message = 'requirement_daily_limit';
    end if;

    insert into public.requirements (
        owner_id, university_id, title, description, category, tags, tag_keys, terms,
        location_text, starts_at, participants_needed
    ) values (
        v_me, v_university, v_title, v_description, p_category, v_tags,
        coalesce((select array_agg(distinct public.search_fold(t)) from unnest(v_tags) t), '{}'),
        public.requirement_terms(v_title, v_description),
        nullif(btrim(p_location_text), ''), p_starts_at, p_participants_needed
    )
    returning id into v_id;
    return v_id;
end;
$$;

-- find_matches --------------------------------------------------------------------------------

drop function public.find_matches(uuid, integer);

create function public.find_matches(p_requirement_id uuid, p_limit integer default 20)
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
    shared_tags text[],
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
    with scored as (
        select c.*,
               public.set_overlap(c.tag_keys, v_source.tag_keys) as tag_part,
               public.set_overlap(c.terms, v_source.terms) as word_part,
               case
                   when c.starts_at is null or v_source.starts_at is null then 0
                   else greatest(0, 1 - abs(extract(epoch from c.starts_at - v_source.starts_at)) / (7 * 86400))
               end as time_part,
               case
                   when c.location_text is null or v_source.location_text is null then 0
                   when strpos(public.search_fold(c.location_text), public.search_fold(v_source.location_text)) > 0
                     or strpos(public.search_fold(v_source.location_text), public.search_fold(c.location_text)) > 0 then 1
                   else 0
               end as place_part
        from public.requirements c
        where c.status = 'ACTIVE'
          and c.university_id = v_source.university_id
          and c.category = v_source.category
          and c.owner_id <> v_source.owner_id
          and (c.tag_keys && v_source.tag_keys or c.terms && v_source.terms)
    ), ranked as (
        select s.*, round(100 * (0.50 * s.tag_part + 0.25 * s.word_part + 0.15 * s.time_part + 0.10 * s.place_part))::integer as total
        from scored s
    )
    select r.id, r.title, r.description, r.category, r.tags, r.location_text, r.starts_at,
           r.participants_needed, r.created_at, r.total,
           coalesce(array(select t from unnest(r.tags) t where public.search_fold(t) = any (v_source.tag_keys) order by t), '{}'),
           p.id, p.full_name, p.username, p.department
    from ranked r
    join public.profiles p on p.id = r.owner_id and p.account_status = 'APPROVED'
    where r.total >= 15
      and not public.is_blocked_with(r.owner_id)
    order by r.total desc, r.created_at desc
    limit least(greatest(coalesce(p_limit, 20), 1), 50);
end;
$$;

-- Grants --------------------------------------------------------------------------------------

do $$
declare
    f text;
begin
    foreach f in array array[
        'public.list_plans()',
        'public.my_subscription()',
        'public.create_requirement(text, text, public.requirement_category, text[], text, timestamptz, integer)',
        'public.find_matches(uuid, integer)'
    ] loop
        execute format('revoke all on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;
    foreach f in array array['public.current_limits(uuid)', 'public.requirement_tag(text)'] loop
        execute format('revoke all on function %s from public, anon, authenticated', f);
    end loop;
end;
$$;
