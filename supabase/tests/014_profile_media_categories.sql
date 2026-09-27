-- Post categories, bio, avatars (private storage) and profile stats.

create temp table pm as
select tests.approved_student('pm1@example.edu.tr', 'pm_bir', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as a,
       tests.approved_student('pm2@example.edu.tr', 'pm_iki', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as b,
       tests.create_user('pm-pending@example.edu.tr') as pending;
grant select on pm to authenticated, anon;

-- 1. Posts carry a category; the feed can be filtered by it.
begin;
select tests.act_as((select a from pm));
select public.create_post('GENERAL', 'Kitap satıyorum', 'MARKETPLACE');
select public.create_post('GENERAL', 'Yarın sınav var mı?', 'QUESTION');
select public.create_post('GENERAL', 'Kategorisiz');
select tests.expect_error($$select public.create_post('GENERAL', 'x', 'NOPE')$$, 'invalid input value for enum post_category: "NOPE"');
select tests.assert_equals(
    (select string_agg(category::text, ',' order by body) from public.list_posts('GENERAL') where is_mine),
    'GENERAL,MARKETPLACE,QUESTION',
    'category returned, default GENERAL'
);
select tests.assert_equals(
    (select string_agg(body, ',') from public.list_posts('GENERAL', null, null, 20, 'MARKETPLACE') where is_mine),
    'Kitap satıyorum',
    'filter by category'
);
select tests.assert_equals(
    (select category::text from public.get_post((select id from public.posts where body = 'Yarın sınav var mı?'))),
    'QUESTION',
    'get_post returns the category'
);
rollback;

-- 2. Bio: trimmed, limited, cleared when empty; only through the function.
begin;
select tests.act_as((select a from pm));
select public.update_bio('  Bilgisayar mühendisliği, satranç  ');
select tests.assert_equals(
    (select bio from public.profiles where id = (select a from pm)), 'Bilgisayar mühendisliği, satranç', 'bio saved'
);
select tests.expect_error(format($$select public.update_bio(%L)$$, repeat('a', 301)), 'invalid_bio');
select public.update_bio('   ');
select tests.assert_equals(
    (select bio is null from public.profiles where id = (select a from pm)), true, 'empty bio clears it'
);
select tests.reset_role();
select tests.act_as_anon();
select tests.expect_error($$select public.update_bio('x')$$, 'permission denied for function update_bio');
rollback;

-- 3. Avatar upload: own folder, jpeg path shape; set_avatar requires the file to exist.
begin;
select tests.act_as((select a from pm));
insert into storage.objects (bucket_id, name, owner)
values ('avatars', (select a from pm)::text || '/0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10.jpg', (select a from pm));
select tests.expect_error(
    format($$insert into storage.objects (bucket_id, name, owner) values ('avatars', %L, %L)$$,
           (select b from pm)::text || '/0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c11.jpg', (select a from pm)),
    'new row violates row-level security policy for table "objects"'
);
select tests.expect_error(
    format($$insert into storage.objects (bucket_id, name, owner) values ('avatars', %L, %L)$$,
           (select a from pm)::text || '/photo.png', (select a from pm)),
    'new row violates row-level security policy for table "objects"'
);
select tests.expect_error(
    format($$select public.set_avatar(%L)$$, (select a from pm)::text || '/1b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10.jpg'),
    'avatar_not_found'
);
select tests.expect_error(
    format($$select public.set_avatar(%L)$$, (select b from pm)::text || '/0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10.jpg'),
    'invalid_avatar_path'
);
select public.set_avatar((select a from pm)::text || '/0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10.jpg');
select tests.assert_equals(
    (select avatar_path from public.profiles where id = (select a from pm)),
    (select a from pm)::text || '/0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10.jpg',
    'avatar set'
);
select tests.reset_role();

-- Approved students see each other's avatars; pending accounts and anon do not.
select tests.act_as((select b from pm));
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'avatars'), 1::bigint, 'approved sees avatar');
select tests.assert_equals(
    (select avatar_path from public.avatar_paths(array[(select a from pm), (select b from pm)]) where user_id = (select a from pm)),
    (select a from pm)::text || '/0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10.jpg',
    'avatar_paths for approved callers'
);
select tests.reset_role();
select tests.act_as((select pending from pm));
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'avatars'), 0::bigint, 'pending sees none');
select tests.expect_error(format($$select * from public.avatar_paths(array[%L]::uuid[])$$, (select a from pm)), 'approved_student_required');
select tests.reset_role();
select tests.act_as_anon();
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'avatars'), 0::bigint, 'anon sees none');
select tests.reset_role();

-- The owner can delete old files and clear the avatar.
select tests.act_as((select a from pm));
select public.remove_avatar();
delete from storage.objects where bucket_id = 'avatars';
select tests.reset_role();
select tests.assert_equals(
    (select (avatar_path is null)::text || '|' || (select count(*) from storage.objects where bucket_id = 'avatars')::text
     from public.profiles where id = (select a from pm)),
    'true|0',
    'avatar removed and file deleted'
);
rollback;

-- 4. Blocked people's avatars are not returned.
begin;
select tests.act_as((select a from pm));
insert into storage.objects (bucket_id, name, owner)
values ('avatars', (select a from pm)::text || '/0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10.jpg', (select a from pm));
select public.set_avatar((select a from pm)::text || '/0b8e2f7c-3c1a-4a55-9d7e-2f1e4b6a9c10.jpg');
select public.block_user((select b from pm));
select tests.reset_role();
select tests.act_as((select b from pm));
select tests.assert_equals(
    (select count(*) from public.avatar_paths(array[(select a from pm)])), 0::bigint, 'blocked avatar hidden'
);
rollback;

-- 5. Profile stats for the signed-in person.
begin;
select tests.act_as((select a from pm));
select public.create_post('GENERAL', 'Bir', 'GENERAL');
select public.create_post('UNIVERSITY', 'İki', 'EVENT');
select tests.assert_equals(
    (select post_count::text || '|' || active_requirement_count::text || '|' || conversation_count::text from public.my_profile_stats()),
    '2|0|0',
    'stats'
);
select tests.reset_role();
select tests.act_as((select pending from pm));
select tests.expect_error($$select * from public.my_profile_stats()$$, 'approved_student_required');
rollback;
