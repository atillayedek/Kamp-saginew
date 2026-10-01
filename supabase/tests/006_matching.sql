-- Rule-based matching: same category and campus, tags + words + time + place, exclusions, ownership.

create function tests.student_at(p_email text, p_username text, p_university text, p_status public.account_status default 'APPROVED')
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    v_id := tests.create_user(p_email);
    update public.profiles
    set full_name = 'Öğrenci ' || p_username, username = p_username, department = 'Bölüm ' || p_username,
        university_id = (select id from public.universities where name = p_university),
        account_status = p_status
    where id = v_id;
    return v_id;
end;
$$;

-- Inserts a requirement directly with the same keys create_requirement() computes.
create function tests.req(
    p_owner uuid, p_title text, p_tags text[], p_category public.requirement_category default 'SPORTS',
    p_location text default null, p_starts_at timestamptz default null, p_status public.requirement_status default 'ACTIVE'
)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
    v_tags text[] := coalesce((select array_agg(distinct public.requirement_tag(t)) from unnest(p_tags) t), '{}');
    v_id uuid;
begin
    insert into public.requirements (owner_id, university_id, title, description, category, tags, tag_keys, terms,
                                     location_text, starts_at, status, closed_at)
    values (p_owner, (select university_id from public.profiles where id = p_owner), p_title, '', p_category, v_tags,
            coalesce((select array_agg(distinct public.search_fold(t)) from unnest(v_tags) t), '{}'),
            public.requirement_terms(p_title, ''), p_location, p_starts_at, p_status,
            case when p_status = 'CLOSED' then now() end)
    returning id into v_id;
    return v_id;
end;
$$;

-- 0. Building blocks.
select tests.assert_equals(public.requirement_terms('Kitaplar ARIYORUM', 've İstanbul için'), '{ariyor,istanbul,kitap}'::text[], 'stems, stop words');
select tests.assert_equals(public.requirement_terms('Kitaplar', ''), public.requirement_terms('kitap', ''), 'plural meets singular');
select tests.assert_equals(public.requirement_tag('  Oda   Arkadaşı '), 'ev arkadaşı', 'synonym');
select tests.assert_equals(public.requirement_tag('HALI SAHA'), 'futbol', 'synonym folded');
select tests.assert_equals(public.requirement_tag('Çİğ Köfte'), 'çiğ köfte', 'turkish lower case');
select tests.assert_equals(public.set_overlap('{a,b}', '{b,c}'), 0.5::numeric, 'overlap');
select tests.assert_equals(public.set_overlap('{}', '{a}'), 0::numeric, 'empty overlap');

begin;
create temp table people as
select tests.student_at('m1@example.edu.tr', 'mbir', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as me,
       tests.student_at('m2@example.edu.tr', 'miki', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as same_campus,
       tests.student_at('m3@example.edu.tr', 'muc', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as other_same_campus,
       tests.student_at('m4@example.edu.tr', 'mdort', 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ') as other_campus,
       tests.student_at('m5@example.edu.tr', 'mbes', 'İSTANBUL TEKNİK ÜNİVERSİTESİ', 'SUSPENDED') as suspended,
       tests.student_at('m6@example.edu.tr', 'malti', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as blocker;
grant select on people to authenticated;

create temp table t0 as select now() + interval '2 days' as at;
create temp table reqs as
select tests.req((select me from people), 'Basketbol maçı', '{Basket}', 'SPORTS', 'Kampüs sahası', (select at from t0)) as mine,
       -- tags 1, words 1, time 1, place 1 -> 100
       tests.req((select same_campus from people), 'Basketbol maçı', '{basketbol}', 'SPORTS', 'Merkez kampüs sahası', (select at from t0)) as identical,
       -- tags 1/sqrt(1*2) = 0.707, words 0, 3.5 days apart -> time 0.5, no place -> 35.4 + 7.5 = 43
       tests.req((select other_same_campus from people), 'Pota arıyorum', '{basketbol,koşu}', 'SPORTS', null,
                 (select at from t0) + interval '84 hours') as tag_match,
       -- tags 0, words {basketbol,mac} vs {basketbol,mac,oyna...}: 2 / sqrt(2*3) = 0.816 -> 20
       tests.req((select same_campus from people), 'Basketbol maçı oynayalım', '{spor}') as word_match,
       -- one shared word of many: 1 / sqrt(2*4) -> 9, under the threshold
       tests.req((select same_campus from people), 'Basketbol izlemek için arkadaş sinema', '{}') as weak,
       tests.req((select same_campus from people), 'Satılık bisiklet', '{bisiklet}') as unrelated,
       tests.req((select same_campus from people), 'Basketbol maçı', '{basketbol}', 'STUDY') as other_category,
       tests.req((select same_campus from people), 'Basketbol maçı', '{basketbol}', 'SPORTS', null, null, 'CLOSED') as closed,
       tests.req((select other_campus from people), 'Basketbol maçı', '{basketbol}') as far,
       tests.req((select suspended from people), 'Basketbol maçı', '{basketbol}') as suspended_req,
       tests.req((select blocker from people), 'Basketbol maçı', '{basketbol}') as blocked_req;
grant select on reqs to authenticated, anon;
insert into public.user_blocks (blocker_id, blocked_id) values ((select blocker from people), (select me from people));

-- 1. Ranked by score; the reason (shared tags) comes along; exclusions applied.
select tests.act_as((select me from people));
select tests.assert_equals(
    (select string_agg(title || ':' || score, ',' order by score desc) from public.find_matches((select mine from reqs))),
    'Basketbol maçı:100,Pota arıyorum:43,Basketbol maçı oynayalım:20',
    'ranking and scores'
);
select tests.assert_equals(
    (select array_to_string(shared_tags, ',') from public.find_matches((select mine from reqs)) where title = 'Pota arıyorum'),
    'basketbol',
    'shared tags'
);
select tests.assert_equals(
    (select owner_full_name || '|' || owner_username || '|' || owner_department
     from public.find_matches((select mine from reqs)) where title = 'Pota arıyorum'),
    -- Surnames are hidden from other students unless shown (KVKK data minimisation).
    'Öğrenci m.|muc|Bölüm muc',
    'owner details'
);
select tests.assert_equals((select count(*) from public.find_matches((select mine from reqs), 1)), 1::bigint, 'limit');

-- 2. Only for one's own requirement.
select tests.expect_error(format($$select * from public.find_matches(%L)$$, (select tag_match from reqs)), 'requirement_not_found');
select tests.expect_error(format($$select * from public.find_matches(%L)$$, gen_random_uuid()), 'requirement_not_found');
select tests.reset_role();

-- 3. The other side sees the match with the same score.
select tests.act_as((select other_same_campus from people));
select tests.assert_equals(
    -- mine and identical score 43 both ways; the blocker's (not blocked for this person) has only the tag: 35.
    (select string_agg(title || ':' || score, ',' order by score desc) from public.find_matches((select tag_match from reqs))),
    'Basketbol maçı:43,Basketbol maçı:43,Basketbol maçı:35',
    'symmetric'
);
select tests.reset_role();

-- 4. Unapproved callers and anon are refused.
select tests.act_as((select suspended from people));
select tests.expect_error(format($$select * from public.find_matches(%L)$$, (select suspended_req from reqs)), 'approved_student_required');
select tests.reset_role();
select tests.act_as_anon();
select tests.expect_error(format($$select * from public.find_matches(%L)$$, (select mine from reqs)), 'permission denied for function find_matches');
rollback;
