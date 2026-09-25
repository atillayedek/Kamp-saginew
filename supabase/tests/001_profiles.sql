-- Profiles, universities and complete_profile.
-- Each block runs in its own transaction and is rolled back.

-- 1. Sign-up creates a PROFILE_INCOMPLETE profile.
begin;
create temp table ids as select tests.create_user('ayse@example.edu.tr') as a;
select tests.assert_equals(
    (select account_status::text || '|' || email from public.profiles where id = (select a from ids)),
    'PROFILE_INCOMPLETE|ayse@example.edu.tr',
    'new user status'
);
rollback;

-- 2. A person reads only their own profile; anon reads nothing.
begin;
create temp table ids as
select tests.create_user('a@example.edu.tr') as a, tests.create_user('b@example.edu.tr') as b;
grant select on ids to authenticated, anon;
select tests.act_as((select a from ids));
select tests.assert_equals((select count(*) from public.profiles), 1::bigint, 'own profile only');
select tests.assert_equals((select id from public.profiles), (select a from ids), 'the own row');
select tests.reset_role();
select tests.act_as_anon();
select tests.assert_equals((select count(*) from public.profiles), 0::bigint, 'anon sees no profiles');
rollback;

-- 3. The client cannot write profiles directly (no policies).
begin;
create temp table ids as select tests.create_user('c@example.edu.tr') as a;
grant select on ids to authenticated;
select tests.act_as((select a from ids));
update public.profiles set account_status = 'APPROVED';
select tests.reset_role();
select tests.assert_equals(
    (select account_status::text from public.profiles where id = (select a from ids)),
    'PROFILE_INCOMPLETE',
    'direct update has no effect'
);
select tests.act_as((select a from ids));
select tests.expect_error(
    $$insert into public.profiles (id, email) values (gen_random_uuid(), 'x@example.edu.tr')$$,
    'new row violates row-level security policy for table "profiles"'
);
rollback;

-- 4. Only active universities are visible, and only to signed-in users.
begin;
insert into public.universities (name, city, is_active) values
    ('Aktif Üniversitesi', 'Ankara', true),
    ('Kapalı Üniversitesi', 'İzmir', false);
create temp table ids as select tests.create_user('d@example.edu.tr') as a;
grant select on ids to authenticated;
select tests.act_as((select a from ids));
select tests.assert_equals((select count(*) from public.universities), 1::bigint, 'active only');
select tests.expect_error(
    $$insert into public.universities (name, city) values ('Sahte', 'X')$$,
    'new row violates row-level security policy for table "universities"'
);
select tests.reset_role();
select tests.act_as_anon();
select tests.assert_equals((select count(*) from public.universities), 0::bigint, 'anon sees none');
rollback;

-- 5. complete_profile validates, normalises and moves to DOCUMENT_REQUIRED.
begin;
insert into public.universities (id, name, city) values ('00000000-0000-0000-0000-00000000000a', 'Aktif Üniversitesi', 'Ankara');
insert into public.universities (id, name, city, is_active) values ('00000000-0000-0000-0000-00000000000b', 'Kapalı Üniversitesi', 'İzmir', false);
create temp table ids as
select tests.create_user('e@example.edu.tr') as a, tests.create_user('f@example.edu.tr') as b;
grant select on ids to authenticated;

select tests.act_as((select a from ids));
select tests.expect_error(
    $$select public.complete_profile('A', 'ayse', '00000000-0000-0000-0000-00000000000a', 'Bilgisayar Müh.')$$,
    'invalid_full_name'
);
select tests.expect_error(
    $$select public.complete_profile('Ayşe Yılmaz', 'a!', '00000000-0000-0000-0000-00000000000a', 'Bilgisayar Müh.')$$,
    'invalid_username'
);
select tests.expect_error(
    $$select public.complete_profile('Ayşe Yılmaz', 'ayse', '00000000-0000-0000-0000-00000000000a', ' ')$$,
    'invalid_department'
);
select tests.expect_error(
    $$select public.complete_profile('Ayşe Yılmaz', 'ayse', '00000000-0000-0000-0000-00000000000b', 'Bilgisayar Müh.')$$,
    'university_not_found'
);
select tests.expect_error(
    $$select public.complete_profile('Ayşe Yılmaz', 'ayse', null, 'Bilgisayar Müh.')$$,
    'university_not_found'
);
select public.complete_profile('  Ayşe Yılmaz ', ' Ayse_Y ', '00000000-0000-0000-0000-00000000000a', 'Bilgisayar Müh.');
select tests.assert_equals(
    (select username || '|' || full_name || '|' || account_status::text from public.profiles),
    'ayse_y|Ayşe Yılmaz|DOCUMENT_REQUIRED',
    'normalised and advanced'
);
-- Editing again is allowed before a document is submitted.
select public.complete_profile('Ayşe Yılmaz', 'ayse_y', '00000000-0000-0000-0000-00000000000a', 'Yazılım Müh.');
select tests.assert_equals((select department from public.profiles), 'Yazılım Müh.', 'editable while DOCUMENT_REQUIRED');

-- Another person cannot take the same username (case-insensitive).
select tests.reset_role();
select tests.act_as((select b from ids));
select tests.expect_error(
    $$select public.complete_profile('Fatma Kaya', 'AYSE_Y', '00000000-0000-0000-0000-00000000000a', 'Hukuk')$$,
    'username_taken'
);
rollback;

-- 6. complete_profile is locked once verification started, and anon cannot call it.
begin;
insert into public.universities (id, name, city) values ('00000000-0000-0000-0000-00000000000a', 'Aktif Üniversitesi', 'Ankara');
create temp table ids as select tests.create_user('g@example.edu.tr') as a;
grant select on ids to authenticated, anon;
update public.profiles set account_status = 'PENDING_REVIEW' where id = (select a from ids);
select tests.act_as((select a from ids));
select tests.expect_error(
    $$select public.complete_profile('Ali Veli', 'aliveli', '00000000-0000-0000-0000-00000000000a', 'Hukuk')$$,
    'profile_locked'
);
select tests.reset_role();
select tests.act_as_anon();
select tests.expect_error(
    $$select public.complete_profile('Ali Veli', 'aliveli', '00000000-0000-0000-0000-00000000000a', 'Hukuk')$$,
    'permission denied for function complete_profile'
);
rollback;

-- 7. Deleting the auth user removes the profile.
begin;
create temp table ids as select tests.create_user('h@example.edu.tr') as a;
delete from auth.users where id = (select a from ids);
select tests.assert_equals((select count(*) from public.profiles where id = (select a from ids)), 0::bigint, 'cascade delete');
rollback;
