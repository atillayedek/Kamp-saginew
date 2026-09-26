-- Matching: real cosine similarity, campus scope, exclusions, ownership.

create function tests.vec(p_x real, p_y real) returns extensions.vector
language sql immutable as $$
    select ('[' || p_x::text || ',' || p_y::text || repeat(',0', 1534) || ']')::extensions.vector
$$;

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

-- Inserts a requirement directly (test setup bypasses the Edge Function).
create function tests.req(p_owner uuid, p_title text, p_vector extensions.vector, p_status public.requirement_status default 'ACTIVE')
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    insert into public.requirements (owner_id, university_id, original_text, title, description, category, embedding, embedding_model, status, closed_at)
    values (p_owner, (select university_id from public.profiles where id = p_owner), 'on karakterden uzun metin', p_title, 'açıklama',
            'SPORTS', p_vector, 'test-model', p_status, case when p_status = 'CLOSED' then now() end)
    returning id into v_id;
    return v_id;
end;
$$;

begin;
create temp table people as
select tests.student_at('m1@example.edu.tr', 'mbir', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as me,
       tests.student_at('m2@example.edu.tr', 'miki', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as same_campus,
       tests.student_at('m3@example.edu.tr', 'muc', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as other_same_campus,
       tests.student_at('m4@example.edu.tr', 'mdort', 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ') as other_campus,
       tests.student_at('m5@example.edu.tr', 'mbes', 'İSTANBUL TEKNİK ÜNİVERSİTESİ', 'SUSPENDED') as suspended;
grant select on people to authenticated;

create temp table reqs as
select tests.req((select me from people), 'Benim ilanım', tests.vec(1, 0)) as mine,
       tests.req((select me from people), 'Benim diğer ilanım', tests.vec(1, 0)) as my_other,
       tests.req((select same_campus from people), 'Birebir aynı', tests.vec(1, 0)) as identical,
       tests.req((select other_same_campus from people), 'Benzer', tests.vec(0.8, 0.6)) as close_match,
       tests.req((select same_campus from people), 'Alakasız', tests.vec(0, 1)) as unrelated,
       tests.req((select same_campus from people), 'Kapalı', tests.vec(1, 0), 'CLOSED') as closed,
       tests.req((select other_campus from people), 'Başka kampüs', tests.vec(1, 0)) as far,
       tests.req((select suspended from people), 'Askıdaki kullanıcı', tests.vec(1, 0)) as suspended_req;
grant select on reqs to authenticated, anon;

-- 1. Ranked by similarity with the real score; exclusions applied.
select tests.act_as((select me from people));
select tests.assert_equals(
    (select string_agg(title || ':' || score, ',' order by score desc) from public.find_matches((select mine from reqs))),
    'Birebir aynı:100,Benzer:80',
    'ranking and scores'
);
select tests.assert_equals(
    (select owner_full_name || '|' || owner_username || '|' || owner_department
     from public.find_matches((select mine from reqs)) where title = 'Benzer'),
    'Öğrenci muc|muc|Bölüm muc',
    'owner details'
);
select tests.assert_equals((select count(*) from public.find_matches((select mine from reqs), 1)), 1::bigint, 'limit');

-- 2. Only for one's own requirement.
select tests.expect_error(format($$select * from public.find_matches(%L)$$, (select close_match from reqs)), 'requirement_not_found');
select tests.expect_error(format($$select * from public.find_matches(%L)$$, gen_random_uuid()), 'requirement_not_found');
select tests.reset_role();

-- 3. The other side sees the match too, with the same score.
select tests.act_as((select other_same_campus from people));
select tests.assert_equals(
    (select string_agg(title || ':' || score, ',' order by title) from public.find_matches((select close_match from reqs))),
    -- cos([0.8,0.6],[0,1]) = 0.6 -> distance 0.4, inside the 0.5 threshold.
    'Alakasız:60,Benim diğer ilanım:80,Benim ilanım:80,Birebir aynı:80',
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
