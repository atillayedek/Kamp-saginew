-- Photos, polls, events, listings, saved posts, search and other people's profiles.

create temp table ff as
select tests.approved_student('ff1@example.edu.tr', 'ff_bir', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as a,
       tests.approved_student('ff2@example.edu.tr', 'ff_iki', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as b,
       tests.approved_student('ff3@example.edu.tr', 'ff_uc', 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ') as c,
       tests.create_user('ff-pending@example.edu.tr') as pending;
grant select on ff to authenticated, anon;

-- 1. Photos: own uploaded files only, at most four, never reused; readable with the post.
begin;
select tests.act_as((select a from ff));
insert into storage.objects (bucket_id, name, owner) values
    ('post-media', (select a from ff)::text || '/00000000-0000-4000-8000-000000000001.jpg', (select a from ff)),
    ('post-media', (select a from ff)::text || '/00000000-0000-4000-8000-000000000002.jpg', (select a from ff));
select tests.expect_error(
    format($$insert into storage.objects (bucket_id, name, owner) values ('post-media', %L, %L)$$,
           (select b from ff)::text || '/00000000-0000-4000-8000-000000000003.jpg', (select a from ff)),
    'new row violates row-level security policy for table "objects"'
);
select tests.expect_error(
    format($$select public.create_post('UNIVERSITY', 'x', 'GENERAL', array[%L])$$,
           (select a from ff)::text || '/00000000-0000-4000-8000-000000000009.jpg'),
    'invalid_media'
);
select tests.expect_error(
    format($$select public.create_post('UNIVERSITY', 'x', 'GENERAL', array[%L, %L])$$,
           (select a from ff)::text || '/00000000-0000-4000-8000-000000000001.jpg',
           (select a from ff)::text || '/00000000-0000-4000-8000-000000000001.jpg'),
    'invalid_media'
);
select tests.expect_error(
    format($$select public.create_post('UNIVERSITY', 'x', 'GENERAL', array[%L, %L, %L, %L, %L])$$,
           'a', 'b', 'c', 'd', 'e'),
    'invalid_media'
);
select public.create_post('UNIVERSITY', 'Fotoğraflı gönderi', 'GENERAL', array[
    (select a from ff)::text || '/00000000-0000-4000-8000-000000000002.jpg',
    (select a from ff)::text || '/00000000-0000-4000-8000-000000000001.jpg'
]);
select tests.expect_error(
    format($$select public.create_post('UNIVERSITY', 'tekrar', 'GENERAL', array[%L])$$,
           (select a from ff)::text || '/00000000-0000-4000-8000-000000000001.jpg'),
    'invalid_media'
);
select tests.assert_equals(
    (select array_to_string(media, ',') from public.list_posts('UNIVERSITY') where body = 'Fotoğraflı gönderi'),
    (select a from ff)::text || '/00000000-0000-4000-8000-000000000002.jpg,' ||
        (select a from ff)::text || '/00000000-0000-4000-8000-000000000001.jpg',
    'media in chosen order'
);
select tests.reset_role();

-- Same university sees the files; another university and pending accounts do not.
select tests.act_as((select b from ff));
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'post-media'), 2::bigint, 'b sees photos');
select tests.reset_role();
select tests.act_as((select c from ff));
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'post-media'), 0::bigint, 'c sees none');
select tests.reset_role();
select tests.act_as((select pending from ff));
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'post-media'), 0::bigint, 'pending sees none');
select tests.reset_role();

-- A deleted post hides its photos from everyone but the owner.
select tests.act_as((select a from ff));
select public.delete_post((select id from public.posts where body = 'Fotoğraflı gönderi'));
select tests.reset_role();
select tests.act_as((select b from ff));
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'post-media'), 0::bigint, 'deleted post hides photos');
rollback;

-- 2. Polls: 2-4 distinct options, one vote per person, changeable, closable.
begin;
select tests.act_as((select a from ff));
select tests.expect_error($$select public.create_post('GENERAL', 'Tek', 'GENERAL', null, array['Evet'])$$, 'invalid_poll');
select tests.expect_error($$select public.create_post('GENERAL', 'Aynı', 'GENERAL', null, array['Evet', ' evet '])$$, 'invalid_poll');
select tests.expect_error($$select public.create_post('GENERAL', 'Boş', 'GENERAL', null, array['Evet', ''])$$, 'invalid_poll');
select tests.expect_error(
    $$select public.create_post('GENERAL', 'Geç', 'GENERAL', null, array['A', 'B'], now() - interval '1 minute')$$, 'invalid_poll'
);
select tests.expect_error($$select public.create_post('GENERAL', 'Kapanış', 'GENERAL', null, null, now() + interval '1 day')$$, 'invalid_poll');
select public.create_post('GENERAL', 'Kantin mi yemekhane mi?', 'QUESTION', null, array['Kantin', 'Yemekhane', 'İkisi de']);
select tests.reset_role();

create temp table poll_ids as
select pl.id as poll, (select id from public.poll_options where poll_id = pl.id and position = 0) as o0,
       (select id from public.poll_options where poll_id = pl.id and position = 1) as o1
from public.polls pl;
grant select on poll_ids to authenticated;

select tests.act_as((select b from ff));
select public.vote_poll((select poll from poll_ids), (select o0 from poll_ids));
select public.vote_poll((select poll from poll_ids), (select o0 from poll_ids));
select tests.reset_role();
select tests.act_as((select c from ff));
select tests.assert_equals(
    (public.vote_poll((select poll from poll_ids), (select o0 from poll_ids)) ->> 'total_votes'), '2', 'second voter'
);
select tests.assert_equals(
    (public.vote_poll((select poll from poll_ids), (select o1 from poll_ids)) -> 'options' -> 1 ->> 'votes'), '1', 'vote changed'
);
select tests.assert_equals(
    (select (poll ->> 'my_option_id')::uuid from public.list_posts('GENERAL') where body like 'Kantin%'),
    (select o1 from poll_ids),
    'my vote shown'
);
select tests.expect_error(
    format($$select public.vote_poll(%L, %L)$$, (select poll from poll_ids), gen_random_uuid()), 'invalid_poll_option'
);
select public.vote_poll((select poll from poll_ids), null);
select tests.reset_role();
select tests.assert_equals(
    (select string_agg(vote_count::text, ',' order by position) from public.poll_options), '1,0,0', 'counts after withdraw'
);
update public.polls set closes_at = now() - interval '1 second';
select tests.act_as((select c from ff));
select tests.expect_error(
    format($$select public.vote_poll(%L, %L)$$, (select poll from poll_ids), (select o0 from poll_ids)), 'poll_closed'
);
select tests.reset_role();

-- No direct access to poll tables.
select tests.act_as((select b from ff));
select tests.expect_error($$insert into public.poll_votes (poll_id, user_id, option_id) select poll, auth.uid(), o1 from poll_ids$$,
    'new row violates row-level security policy for table "poll_votes"');
select tests.assert_equals((select count(*) from public.poll_votes), 0::bigint, 'votes not readable');
select tests.reset_role();
select tests.act_as((select pending from ff));
select tests.expect_error(
    format($$select public.vote_poll(%L, %L)$$, (select poll from poll_ids), (select o0 from poll_ids)), 'approved_student_required'
);
rollback;

-- 3. Events: only for the EVENT category, attendance counted, ended events closed.
begin;
select tests.act_as((select a from ff));
select tests.expect_error(
    $$select public.create_post('GENERAL', 'x', 'GENERAL', null, null, null, now() + interval '1 day')$$, 'invalid_event'
);
select tests.expect_error(
    $$select public.create_post('GENERAL', 'x', 'EVENT', null, null, null, now() + interval '1 day', now())$$, 'invalid_event'
);
select tests.expect_error(
    $$select public.create_post('GENERAL', 'x', 'EVENT', null, null, null, null, null, 'Kütüphane')$$, 'invalid_event'
);
select public.create_post('GENERAL', 'Satranç turnuvası', 'EVENT', null, null, null,
                          now() + interval '2 days', now() + interval '2 days 3 hours', '  Merkez Kütüphane  ');
select public.create_post('UNIVERSITY', 'Yarın kahvaltı', 'EVENT', null, null, null, now() + interval '1 day');
select tests.reset_role();

select tests.act_as((select b from ff));
select tests.assert_equals(
    public.set_event_attendance((select id from public.posts where body = 'Satranç turnuvası'), true), 1, 'b attends'
);
select tests.assert_equals(
    public.set_event_attendance((select id from public.posts where body = 'Satranç turnuvası'), true), 1, 'idempotent'
);
select tests.assert_equals(
    (select string_agg(body, ',') from public.list_upcoming_events()), 'Yarın kahvaltı,Satranç turnuvası', 'soonest first'
);
select tests.assert_equals(
    (select (event ->> 'attending') || '|' || (event ->> 'location') from public.list_upcoming_events() where body like 'Satranç%'),
    'true|Merkez Kütüphane',
    'event fields'
);
select tests.reset_role();
select tests.act_as((select c from ff));
select tests.assert_equals((select string_agg(body, ',') from public.list_upcoming_events()), 'Satranç turnuvası', 'other university');
select tests.expect_error(
    format($$select public.set_event_attendance(%L, true)$$, (select id from public.posts where body = 'Yarın kahvaltı')),
    'event_not_found'
);
select tests.reset_role();
update public.post_events set starts_at = now() - interval '3 days', ends_at = now() - interval '2 days';
select tests.act_as((select c from ff));
select tests.expect_error(
    format($$select public.set_event_attendance(%L, true)$$, (select id from public.posts where body = 'Satranç turnuvası')),
    'event_ended'
);
select tests.assert_equals((select count(*) from public.list_upcoming_events()), 0::bigint, 'ended events hidden');
rollback;

-- 4. Listings: price only on marketplace posts; only the seller marks sold.
begin;
select tests.act_as((select a from ff));
select tests.expect_error(
    $$select public.create_post('GENERAL', 'x', 'GENERAL', null, null, null, null, null, null, 1000)$$, 'invalid_price'
);
select tests.expect_error(
    $$select public.create_post('GENERAL', 'x', 'MARKETPLACE', null, null, null, null, null, null, -1)$$, 'invalid_price'
);
select public.create_post('GENERAL', 'Hesap makinesi', 'MARKETPLACE', null, null, null, null, null, null, 25000);
select tests.reset_role();
select tests.act_as((select b from ff));
select tests.expect_error(
    format($$select public.set_listing_sold(%L, true)$$, (select id from public.posts where body = 'Hesap makinesi')),
    'listing_not_found'
);
select tests.reset_role();
select tests.act_as((select a from ff));
select public.set_listing_sold((select id from public.posts where body = 'Hesap makinesi'), true);
select tests.assert_equals(
    (select listing::text from public.get_post((select id from public.posts where body = 'Hesap makinesi'))),
    '{"sold": true, "price_kurus": 25000}',
    'listing sold'
);
rollback;

-- 5. Saved posts: private to the saver, gone when the post is not visible.
begin;
select tests.act_as((select a from ff));
select public.create_post('GENERAL', 'Kaydetmelik', 'STUDY');
select public.create_post('UNIVERSITY', 'İTÜ özel', 'STUDY');
select tests.reset_role();
select tests.act_as((select b from ff));
select public.set_post_saved((select id from public.posts where body = 'Kaydetmelik'), true);
select public.set_post_saved((select id from public.posts where body = 'İTÜ özel'), true);
select public.set_post_saved((select id from public.posts where body = 'Kaydetmelik'), true);
select tests.reset_role();
update public.saved_posts set created_at = now() - interval '1 minute'
where post_id = (select id from public.posts where body = 'Kaydetmelik');
select tests.act_as((select b from ff));
select tests.assert_equals(
    (select string_agg(body || ':' || saved_by_me::text, ',') from public.list_saved_posts()),
    'İTÜ özel:true,Kaydetmelik:true',
    'saved newest first'
);
select tests.reset_role();
select tests.act_as((select c from ff));
select tests.expect_error(
    format($$select public.set_post_saved(%L, true)$$, (select id from public.posts where body = 'İTÜ özel')), 'post_not_found'
);
select tests.assert_equals((select count(*) from public.saved_posts), 0::bigint, 'others saves hidden');
select tests.reset_role();
select tests.act_as((select a from ff));
select public.block_user((select b from ff));
select tests.reset_role();
select tests.act_as((select b from ff));
select tests.assert_equals((select count(*) from public.list_saved_posts()), 0::bigint, 'blocked author hidden in saved');
rollback;

-- 6. Search: Turkish-insensitive, visibility and blocks respected, wildcards literal.
begin;
select tests.act_as((select a from ff));
select public.create_post('GENERAL', 'İSTANBUL''da ders çalışma grubu', 'STUDY');
select public.create_post('UNIVERSITY', 'Istanbul kampüs duyurusu', 'ANNOUNCEMENT');
select public.create_post('GENERAL', 'Yüzde 100_indirim', 'GENERAL');
select tests.reset_role();
select tests.act_as((select b from ff));
select tests.assert_equals((select count(*) from public.search_posts('istanbul')), 2::bigint, 'folded match');
select tests.assert_equals((select count(*) from public.search_posts('ÇALIŞMA')), 1::bigint, 'upper case Turkish');
select tests.assert_equals((select count(*) from public.search_posts('0_i')), 1::bigint, 'underscore literal');
select tests.assert_equals((select count(*) from public.search_posts('%%')), 0::bigint, 'percent literal');
select tests.expect_error($$select * from public.search_posts('a')$$, 'invalid_query');
select tests.assert_equals(
    (select string_agg(username, ',' order by username) from public.search_people('FF_')), 'ff_bir,ff_iki,ff_uc', 'people'
);
select tests.assert_equals((select username from public.search_people('ff_iki') limit 1), 'ff_iki', 'exact first');
select tests.assert_equals((select count(*) from public.search_people('pending')), 0::bigint, 'only approved people');
select tests.reset_role();
select tests.act_as((select c from ff));
select tests.assert_equals((select count(*) from public.search_posts('istanbul')), 1::bigint, 'university scope');
select public.block_user((select a from ff));
select tests.assert_equals((select count(*) from public.search_posts('istanbul')), 0::bigint, 'blocked author hidden');
select tests.assert_equals((select count(*) from public.search_people('ff_bir')), 0::bigint, 'blocked person hidden');
rollback;

-- 7. Other people's profiles and posts.
begin;
select tests.act_as((select a from ff));
select public.update_bio('Satranç ve kod');
select public.create_post('GENERAL', 'Genel bir', 'GENERAL');
select public.create_post('UNIVERSITY', 'Sadece İTÜ', 'GENERAL');
select tests.reset_role();
select tests.act_as((select c from ff));
select tests.assert_equals(
    (select username || '|' || bio || '|' || post_count::text || '|' || is_me::text from public.get_user_profile((select a from ff))),
    'ff_bir|Satranç ve kod|1|false',
    'profile seen from another university'
);
select tests.assert_equals((select string_agg(body, ',') from public.list_user_posts((select a from ff))), 'Genel bir', 'their posts');
select tests.expect_error(format($$select * from public.get_user_profile(%L)$$, (select pending from ff)), 'user_not_found');
select public.block_user((select a from ff));
select tests.expect_error(format($$select * from public.get_user_profile(%L)$$, (select a from ff)), 'user_not_found');
select tests.assert_equals((select count(*) from public.list_user_posts((select a from ff))), 0::bigint, 'blocked posts hidden');
select tests.reset_role();
select tests.act_as_anon();
select tests.expect_error(format($$select * from public.get_user_profile(%L)$$, (select a from ff)),
    'permission denied for function get_user_profile');
select tests.expect_error($$select * from public.post_details('{}')$$, 'permission denied for function post_details');
rollback;
