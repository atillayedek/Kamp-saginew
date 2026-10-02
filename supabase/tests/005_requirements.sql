-- Requirements: written by the student through create_requirement(); validation, synonyms, limits, ownership.

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

create temp table people as
select tests.approved('r1@example.edu.tr', 'rbir') as a,
       tests.approved('r2@example.edu.tr', 'riki') as b,
       tests.create_user('r3@example.edu.tr') as pending;
grant select on people to authenticated, service_role;

-- A valid requirement for the signed-in student.
create function tests.new_req(p_title text default 'Basketbol', p_tags text[] default '{Spor, spor ,  Basket, HALI SAHA}')
returns uuid language sql as $$
    select public.create_requirement(
        p_title, '  Kampüs sahasında maç  ', 'SPORTS', p_tags, ' Kampüs sahası ', now() + interval '1 day', 2
    )
$$;
grant execute on function tests.new_req(text, text[]) to authenticated;

-- 1. Students write their own; values are normalised; tags go through synonyms; keys are stored.
begin;
select tests.act_as((select a from people));
create temp table r as select tests.new_req() as id;
select tests.expect_error(
    format(
        $$insert into public.requirements (owner_id, university_id, title, description, category)
          values (%L, (select id from public.universities limit 1), 'Başlık', 'x', 'OTHER')$$,
        (select a from people)
    ),
    'new row violates row-level security policy for table "requirements"'
);
select tests.reset_role();
select tests.assert_equals(
    (select array_to_string(tags, ',') || '|' || array_to_string(tag_keys, ',') || '|' || description || '|' || location_text || '|' || u.name
     from public.requirements q join public.universities u on u.id = q.university_id),
    'basketbol,futbol,spor|basketbol,futbol,spor|Kampüs sahasında maç|Kampüs sahası|İSTANBUL TEKNİK ÜNİVERSİTESİ',
    'normalised'
);
select tests.assert_equals((select terms from public.requirements), '{basketbol,kampus,mac,saha}'::text[], 'terms');
rollback;

-- 2. Invalid content and unapproved owners are refused.
begin;
select tests.act_as((select a from people));
select tests.expect_error($$select tests.new_req('x')$$, 'invalid_requirement');
select tests.expect_error($$select tests.new_req('Basketbol', array['a','b','c','d','e','f','g','h','i'])$$, 'invalid_requirement');
select tests.expect_error($$select tests.new_req('Basketbol', array[repeat('a', 31)])$$, 'invalid_requirement');
select tests.expect_error(
    $$select public.create_requirement('Basketbol', '', 'SPORTS', '{}', null, null, 99)$$, 'invalid_requirement'
);
select tests.expect_error(
    $$select public.create_requirement('Basketbol', '', 'SPORTS', '{}', null, now() - interval '2 days', null)$$, 'invalid_requirement'
);
select tests.expect_error(
    $$select public.create_requirement('Basketbol', repeat('x', 1001), 'SPORTS', '{}', null, null, null)$$, 'invalid_requirement'
);
select tests.expect_error($$select public.create_requirement('Basketbol', '', null, '{}', null, null, null)$$, 'invalid_requirement');
-- Description, tags, place, time and people are optional.
select tests.assert_equals(
    (select public.create_requirement('Sadece başlık', null, 'OTHER', null, '  ', null, null) is not null), true, 'minimal'
);
select tests.reset_role();
select tests.act_as((select pending from people));
select tests.expect_error($$select tests.new_req()$$, 'approved_student_required');
select tests.reset_role();
select tests.act_as_anon();
select tests.expect_error($$select public.create_requirement('Basketbol', '', 'SPORTS', '{}', null, null, null)$$,
    'permission denied for function create_requirement');
rollback;

-- 3. At most 20 active requirements (free), and at most 30 new ones in 24 hours.
begin;
select tests.act_as((select a from people));
select tests.new_req() from generate_series(1, 20);
select tests.expect_error($$select tests.new_req()$$, 'too_many_active_requirements');
select public.close_requirement(r.id) from public.list_my_requirements() r;
select tests.new_req() from generate_series(1, 10);
select tests.expect_error($$select tests.new_req()$$, 'requirement_daily_limit');
select tests.reset_role();
update public.requirements set created_at = now() - interval '25 hours' where owner_id = (select a from people);
select tests.act_as((select a from people));
select tests.assert_equals((select tests.new_req() is not null), true, 'older ones no longer count');
rollback;

-- 4. Owners list and close their own; nobody else can see or close them.
begin;
select tests.act_as((select a from people));
create temp table r as select tests.new_req() as id;
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

-- 5. Synonyms are not readable by students; account deletion removes requirements.
begin;
select tests.act_as((select a from people));
select tests.assert_equals((select count(*) from public.requirement_synonyms), 0::bigint, 'synonyms not readable');
select tests.expect_error($$select public.requirement_tag('x')$$, 'permission denied for function requirement_tag');
select tests.new_req();
select tests.reset_role();
delete from auth.users where id = (select a from people);
select tests.assert_equals((select count(*) from public.requirements), 0::bigint, 'cascade');
rollback;
