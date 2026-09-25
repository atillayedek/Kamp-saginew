-- The universities data migration loads the product owner's list completely.

-- 1. Every institution from supabase/data/province-universities.json is present and active.
begin;
select tests.assert_equals((select count(*) from public.universities), 206::bigint, 'institution count');
select tests.assert_equals((select count(distinct city) from public.universities), 81::bigint, 'province count');
select tests.assert_equals((select count(*) from public.universities where not is_active), 0::bigint, 'all active');
rollback;

-- 2. Spot checks: Turkish city casing and domains cleaned from messy source values.
begin;
select tests.assert_equals(
    (select city || '|' || website_domain from public.universities where name = 'İSTANBUL TEKNİK ÜNİVERSİTESİ'),
    'İstanbul|itu.edu.tr',
    'http:www. prefix repaired'
);
select tests.assert_equals(
    (select city || '|' || website_domain from public.universities where name = 'HAKKARİ ÜNİVERSİTESİ'),
    'Hakkari|hakkari.edu.tr',
    'first of two listed addresses'
);
select tests.assert_equals(
    (select website_domain from public.universities where name = 'AFYONKARAHİSAR SAĞLIK BİLİMLERİ ÜNİVERSİTESİ'),
    null::text,
    'missing website stays null'
);
rollback;

-- 3. A signed-in person sees the whole list; anon sees nothing.
begin;
create temp table ids as select tests.create_user('liste@example.edu.tr') as a;
grant select on ids to authenticated;
select tests.act_as((select a from ids));
select tests.assert_equals((select count(*) from public.universities), 206::bigint, 'visible to signed-in users');
select tests.reset_role();
select tests.act_as_anon();
select tests.assert_equals((select count(*) from public.universities), 0::bigint, 'hidden from anon');
rollback;

-- 4. Malformed domains are rejected by the column check.
begin;
select tests.expect_error(
    $$update public.universities set website_domain = 'hakkari.edu.tr ' where name = 'HAKKARİ ÜNİVERSİTESİ'$$,
    'new row for relation "universities" violates check constraint "universities_website_domain_check"'
);
rollback;
