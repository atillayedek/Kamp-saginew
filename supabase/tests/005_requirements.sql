-- Requirements: service-role-only writes, embedding shape, AI quota, ownership.

create function tests.vector_literal(p_first real, p_dims integer default 1536) returns text
language sql immutable as $$
    select '[' || p_first::text || repeat(',0', p_dims - 1) || ']'
$$;
grant execute on function tests.vector_literal(real, integer) to authenticated, service_role;

create function tests.approved(p_email text, p_username text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    v_id := tests.create_user(p_email);
    update public.profiles
    set full_name = 'Öğrenci', username = p_username, department = 'Hukuk',
        university_id = (select id from public.universities where name = 'İSTANBUL TEKNİK ÜNİVERSİTESİ'),
        account_status = 'APPROVED'
    where id = v_id;
    return v_id;
end;
$$;
grant execute on function tests.approved(text, text) to service_role;

create temp table people as
select tests.approved('r1@example.edu.tr', 'rbir') as a,
       tests.approved('r2@example.edu.tr', 'riki') as b,
       tests.create_user('r3@example.edu.tr') as pending;
grant select on people to authenticated, service_role;

-- Inserts a valid requirement for p_user as the service role.
create function tests.insert_req(p_user uuid, p_title text default 'Basketbol', p_tags text[] default '{Spor, spor ,  basket}')
returns uuid language sql as $$
    select public.insert_requirement(
        p_user, 'Yarın akşam basketbol oynayacak iki kişi arıyorum', p_title, 'Kampüs sahasında maç',
        'SPORTS', p_tags, ' Kampüs sahası ', timestamptz '2026-09-27 18:00+03', 2,
        tests.vector_literal(1), 'text-embedding-3-small'
    )
$$;
grant execute on function tests.insert_req(uuid, text, text[]) to service_role, authenticated;

-- 1. Only the service role writes; values are normalised; university comes from the profile.
begin;
select tests.act_as((select a from people));
select tests.expect_error(
    format($$select tests.insert_req(%L)$$, (select a from people)),
    'permission denied for function insert_requirement'
);
select tests.expect_error(
    format(
        $$insert into public.requirements (owner_id, university_id, original_text, title, description, category, embedding, embedding_model)
          values (%L, (select id from public.universities limit 1), 'uzun bir metin burada', 'Başlık', 'x', 'OTHER', %L, 'm')$$,
        (select a from people), tests.vector_literal(1)
    ),
    'new row violates row-level security policy for table "requirements"'
);
select tests.reset_role();
select tests.act_as_service();
create temp table r as select tests.insert_req((select a from people)) as id;
select tests.reset_role();
select tests.assert_equals(
    (select array_to_string(tags, ',') || '|' || location_text || '|' || u.name
     from public.requirements q join public.universities u on u.id = q.university_id),
    'basket,spor|Kampüs sahası|İSTANBUL TEKNİK ÜNİVERSİTESİ',
    'normalised'
);
rollback;

-- 2. Invalid content, wrong embedding size and unapproved owners are refused.
begin;
select tests.act_as_service();
select tests.expect_error(format($$select tests.insert_req(%L, 'x')$$, (select a from people)), 'invalid_requirement');
select tests.expect_error(
    format($$select tests.insert_req(%L, 'Basketbol', array['a','b','c','d','e','f','g','h','i'])$$, (select a from people)),
    'invalid_requirement'
);
select tests.expect_error(
    format(
        $$select public.insert_requirement(%L, 'Yarın akşam basketbol oynayacak iki kişi', 'Basketbol', 'Maç', 'SPORTS', '{}', null, null, null, %L, 'm')$$,
        (select a from people), tests.vector_literal(1, 3)
    ),
    'invalid_requirement'
);
select tests.expect_error(
    format(
        $$select public.insert_requirement(%L, 'Yarın akşam basketbol oynayacak iki kişi', 'Basketbol', 'Maç', 'SPORTS', '{}', null, null, 99, %L, 'm')$$,
        (select a from people), tests.vector_literal(1)
    ),
    'invalid_requirement'
);
select tests.expect_error(format($$select tests.insert_req(%L)$$, (select pending from people)), 'approved_student_required');
rollback;

-- 3. At most 20 active requirements per person.
begin;
select tests.act_as_service();
select tests.insert_req((select a from people)) from generate_series(1, 20);
select tests.expect_error(format($$select tests.insert_req(%L)$$, (select a from people)), 'too_many_active_requirements');
rollback;

-- 4. Owners list and close their own; nobody else can see or close them.
begin;
select tests.act_as_service();
create temp table r as select tests.insert_req((select a from people)) as id;
select tests.reset_role();
grant select on r to authenticated;
select tests.act_as((select b from people));
select tests.assert_equals((select count(*) from public.requirements), 0::bigint, 'others cannot read the table');
select tests.assert_equals((select count(*) from public.list_my_requirements()), 0::bigint, 'others list nothing');
select tests.expect_error(format($$select public.close_requirement(%L)$$, (select id from r)), 'requirement_not_found');
select tests.reset_role();
select tests.act_as((select a from people));
select tests.assert_equals((select title from public.list_my_requirements()), 'Basketbol', 'owner lists');
select public.close_requirement((select id from r));
select tests.assert_equals((select status::text from public.list_my_requirements()), 'CLOSED', 'closed');
select tests.expect_error(format($$select public.close_requirement(%L)$$, (select id from r)), 'requirement_not_found');
select tests.reset_role();
select tests.act_as((select pending from people));
select tests.expect_error($$select * from public.list_my_requirements()$$, 'approved_student_required');
rollback;

-- 5. AI quota: service role only, approved students only, 30 analyses / 24 h.
begin;
select tests.act_as((select a from people));
select tests.expect_error(
    format($$select public.consume_ai_quota(%L, 'analyze')$$, (select a from people)),
    'permission denied for function consume_ai_quota'
);
select tests.reset_role();
select tests.act_as_service();
select public.consume_ai_quota((select a from people), 'analyze') from generate_series(1, 30);
select tests.expect_error(format($$select public.consume_ai_quota(%L, 'analyze')$$, (select a from people)), 'ai_quota_exceeded');
-- Other kinds and other people have their own budget.
select public.consume_ai_quota((select a from people), 'publish');
select public.consume_ai_quota((select b from people), 'analyze');
select tests.expect_error(format($$select public.consume_ai_quota(%L, 'analyze')$$, (select pending from people)), 'approved_student_required');
select tests.expect_error(format($$select public.consume_ai_quota(%L, 'other')$$, (select a from people)), 'invalid_quota_kind');
select tests.reset_role();
-- Usage older than 24 hours no longer counts.
update public.ai_usage set created_at = now() - interval '25 hours' where user_id = (select a from people);
select tests.act_as_service();
select public.consume_ai_quota((select a from people), 'analyze');
rollback;

-- 6. Account deletion removes requirements and usage.
begin;
select tests.act_as_service();
select tests.insert_req((select a from people));
select public.consume_ai_quota((select a from people), 'publish');
select tests.reset_role();
delete from auth.users where id = (select a from people);
select tests.assert_equals(
    (select count(*) from public.requirements) + (select count(*) from public.ai_usage),
    0::bigint,
    'cascade'
);
rollback;
