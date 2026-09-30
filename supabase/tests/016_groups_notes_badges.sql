-- Channels (Premium), study groups, course notes, badges and the new report targets.

create temp table gg as
select tests.approved_student('gg1@example.edu.tr', 'gg_bir', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as a,
       tests.approved_student('gg2@example.edu.tr', 'gg_iki', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as b,
       tests.approved_student('gg3@example.edu.tr', 'gg_uc', 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ') as c,
       tests.create_user('gg-pending@example.edu.tr') as pending;
grant select on gg to authenticated, anon, service_role;

-- Premium for a: an active plan and a current entitlement.
insert into public.subscription_plans (play_product_id, name, description, is_active, ai_analyze_daily, ai_publish_daily, max_active_requirements)
values ('kampusagi.test.premium', 'Premium', 'Test planı', true, 100, 50, 50);
insert into public.entitlements (user_id, plan_id, purchase_token, expires_at)
values ((select a from gg), (select id from public.subscription_plans where play_product_id = 'kampusagi.test.premium'),
        'test-token-0000000001', now() + interval '30 days');

-- 1. Channels need Premium; study groups are free and stay in one university.
begin;
select tests.act_as((select b from gg));
select tests.assert_equals(public.am_i_premium(), false, 'b not premium');
select tests.expect_error($$select public.create_group('CHANNEL', 'Kanalım')$$, 'premium_required');
select public.create_group('STUDY_GROUP', 'Fizik 1 çalışma', 'Vize hazırlığı', 'fiz-101');
select tests.expect_error($$select public.create_group('STUDY_GROUP', 'x')$$, 'invalid_group');
select tests.expect_error($$select public.create_group('STUDY_GROUP', 'Kod', null, 'ÇOK-UZUN-DERS-KODU-123')$$, 'invalid_group');
select tests.reset_role();
select tests.assert_equals(
    (select course_code || '|' || member_count::text || '|' || (university_id is not null)::text from public.groups where name = 'Fizik 1 çalışma'),
    'FIZ101|1|true',
    'study group stored'
);
select tests.act_as((select a from gg));
select tests.assert_equals(public.am_i_premium(), true, 'a premium');
select public.create_group('CHANNEL', 'Kampüs Duyuruları', 'Resmi olmayan duyurular', null, false);
select public.create_group('CHANNEL', 'İTÜ Kanalı', null, null, true);
select tests.reset_role();

-- ODTÜ student sees the open channel only; not the İTÜ channel or İTÜ study group.
select tests.act_as((select c from gg));
select tests.assert_equals(
    (select string_agg(name, ',' order by name) from public.discover_groups('CHANNEL')), 'Kampüs Duyuruları', 'c discovers open channel'
);
select tests.assert_equals((select count(*) from public.discover_groups('STUDY_GROUP')), 0::bigint, 'c sees no itu study group');
select tests.expect_error(
    format($$select public.join_group(%L)$$, (select id from public.groups where name = 'İTÜ Kanalı')), 'group_not_found'
);
select public.join_group((select id from public.groups where name = 'Kampüs Duyuruları'));
select public.join_group((select id from public.groups where name = 'Kampüs Duyuruları'));
select tests.reset_role();
select tests.assert_equals(
    (select member_count from public.groups where name = 'Kampüs Duyuruları'), 2, 'join is idempotent'
);

-- b searches study groups by course code in any spelling.
select tests.act_as((select b from gg));
select tests.assert_equals((select count(*) from public.discover_groups('STUDY_GROUP', 'FİZ 101')), 0::bigint, 'already a member');
select tests.reset_role();
select tests.act_as((select a from gg));
select tests.assert_equals((select name from public.discover_groups('STUDY_GROUP', 'fiz 101')), 'Fizik 1 çalışma', 'course code search');
select tests.assert_equals((select name from public.discover_groups('STUDY_GROUP', 'FİZİK')), 'Fizik 1 çalışma', 'name search');
rollback;

-- 2. Channel posting: owner/admins only, polls allowed, Premium must stay active.
begin;
select tests.act_as((select a from gg));
select public.create_group('CHANNEL', 'Kanal', null, null, false);
select tests.reset_role();
create temp table ch as select id from public.groups where name = 'Kanal';
grant select on ch to authenticated;

select tests.act_as((select b from gg));
select public.join_group((select id from ch));
select tests.expect_error(
    format($$select public.send_group_message(%L, gen_random_uuid(), 'merhaba')$$, (select id from ch)), 'group_not_allowed'
);
select tests.assert_equals(
    (select can_post::text || '|' || my_role::text from public.get_group((select id from ch))), 'false|MEMBER', 'member cannot post'
);
select tests.reset_role();

select tests.act_as((select a from gg));
select public.send_group_message((select id from ch), '10000000-0000-4000-8000-000000000001', 'İlk duyuru');
select public.send_group_message((select id from ch), '10000000-0000-4000-8000-000000000001', 'İlk duyuru');
select public.send_group_message((select id from ch), '10000000-0000-4000-8000-000000000002', 'Hangi gün?', null, array['Pazartesi', 'Cuma']);
select tests.expect_error(
    format($$select public.send_group_message(%L, gen_random_uuid(), null)$$, (select id from ch)), 'invalid_message'
);
select tests.expect_error(
    format($$select public.send_group_message(%L, gen_random_uuid(), 'x', null, array['Tek'])$$, (select id from ch)), 'invalid_poll'
);
select public.set_group_member_role((select id from ch), (select b from gg), 'ADMIN');
select public.pin_group_message((select id from ch), '10000000-0000-4000-8000-000000000001');
select tests.reset_role();
create temp table cp as
select p.id as poll, (select o.id from public.poll_options o where o.poll_id = p.id and o.position = 1) as o1
from public.polls p where p.group_message_id = '10000000-0000-4000-8000-000000000002';
grant select on cp to authenticated;

select tests.act_as((select b from gg));
select tests.assert_equals(
    (select string_agg(body || ':' || (poll is not null)::text, ',') from public.list_group_messages((select id from ch))),
    'Hangi gün?:true,İlk duyuru:false',
    'messages newest first with poll'
);
select tests.assert_equals(
    (select pinned_message_body from public.get_group((select id from ch))), 'İlk duyuru', 'pinned message'
);
-- b is admin now and may post; b votes in the poll.
select public.send_group_message((select id from ch), gen_random_uuid(), 'Yönetici mesajı');
select public.vote_poll((select poll from cp), (select o1 from cp));
select tests.assert_equals(
    public.set_group_message_liked('10000000-0000-4000-8000-000000000001', true), 1, 'like'
);
select tests.reset_role();
select tests.assert_equals((select count(*) from public.poll_votes), 1::bigint, 'channel poll vote');

-- Non-members cannot read messages, vote or see through the table.
select tests.act_as((select c from gg));
select tests.expect_error(format($$select * from public.list_group_messages(%L)$$, (select id from ch)), 'group_not_found');
select tests.assert_equals((select count(*) from public.group_messages), 0::bigint, 'c reads no messages');
select tests.expect_error(
    format($$select public.vote_poll(%L, null)$$, (select poll from cp)),
    'poll_not_found'
);
select tests.reset_role();

-- Premium ends: the channel can no longer post, members still read.
update public.entitlements set expires_at = now() - interval '1 minute' where user_id = (select a from gg);
select tests.act_as((select a from gg));
select tests.expect_error(
    format($$select public.send_group_message(%L, gen_random_uuid(), 'x')$$, (select id from ch)), 'premium_required'
);
select tests.expect_error(format($$select public.set_group_photo(%L, null)$$, (select id from ch)), 'premium_required');
select tests.assert_equals((select count(*) from public.list_group_messages((select id from ch))), 3::bigint, 'still readable');
rollback;

-- 3. Study groups: every member writes; roles, removal, leaving, photos, unread counts.
begin;
select tests.act_as((select a from gg));
select public.create_group('STUDY_GROUP', 'Veri Yapıları', null, 'BLM212');
select tests.reset_role();
create temp table sg as select id from public.groups where name = 'Veri Yapıları';
grant select on sg to authenticated;

select tests.act_as((select b from gg));
select public.join_group((select id from sg));
select public.send_group_message((select id from sg), gen_random_uuid(), 'Selam, notlar kimde?');
select tests.expect_error(
    format($$select public.send_group_message(%L, gen_random_uuid(), 'anket', null, array['A', 'B'])$$, (select id from sg)),
    'invalid_poll'
);
select tests.expect_error(format($$select public.remove_group_member(%L, %L)$$, (select id from sg), (select a from gg)), 'group_not_allowed');
select tests.expect_error(format($$select public.set_group_photo(%L, null)$$, (select id from sg)), 'group_not_allowed');
select tests.reset_role();
-- Everything in one transaction shares now(); the owner joined earlier in reality.
update public.group_members set joined_at = now() - interval '1 hour' where user_id = (select a from gg);

select tests.act_as((select a from gg));
select tests.assert_equals(
    (select unread_count from public.list_my_groups('STUDY_GROUP') where name = 'Veri Yapıları'), 1, 'unread for owner'
);
select public.mark_group_read((select id from sg));
select tests.assert_equals(
    (select unread_count from public.list_my_groups('STUDY_GROUP') where name = 'Veri Yapıları'), 0, 'read'
);
-- Group photo: own uploaded file only.
insert into storage.objects (bucket_id, name, owner)
values ('group-media', (select a from gg)::text || '/20000000-0000-4000-8000-000000000001.jpg', (select a from gg));
select tests.expect_error(
    format($$select public.set_group_photo(%L, %L)$$, (select id from sg), (select a from gg)::text || '/20000000-0000-4000-8000-000000000009.jpg'),
    'invalid_media'
);
select public.set_group_photo((select id from sg), (select a from gg)::text || '/20000000-0000-4000-8000-000000000001.jpg');
select tests.expect_error(format($$select public.leave_group(%L)$$, (select id from sg)), 'owner_cannot_leave');
select tests.reset_role();

select tests.act_as((select b from gg));
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'group-media'), 1::bigint, 'member sees group photo');
select tests.reset_role();
select tests.act_as((select c from gg));
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'group-media'), 0::bigint, 'other university does not');
select tests.reset_role();

select tests.act_as((select a from gg));
select public.remove_group_member((select id from sg), (select b from gg));
select tests.reset_role();
select tests.assert_equals((select member_count from public.groups where id = (select id from sg)), 1, 'member removed');

-- A blocked owner's group disappears for the blocker.
select tests.act_as((select b from gg));
select public.block_user((select a from gg));
select tests.expect_error(format($$select * from public.get_group(%L)$$, (select id from sg)), 'group_not_found');
rollback;

-- 4. Course notes: recorded by the service role after the PDF check, visible to the same university.
begin;
insert into storage.objects (bucket_id, name, owner)
values ('course-notes', (select a from gg)::text || '/30000000-0000-4000-8000-000000000001.pdf', (select a from gg));
select tests.act_as((select a from gg));
select tests.expect_error(
    format($$select public.create_course_note(%L, 'MAT101', 'Matematik', 'Özet', null, %L, 1000)$$,
           (select a from gg), (select a from gg)::text || '/30000000-0000-4000-8000-000000000001.pdf'),
    'permission denied for function create_course_note'
);
select tests.reset_role();
select tests.act_as_service();
select tests.expect_error(
    format($$select public.create_course_note(%L, 'MAT101', 'Matematik', 'Özet', null, %L, 1000)$$,
           (select a from gg), (select b from gg)::text || '/30000000-0000-4000-8000-000000000001.pdf'),
    'invalid_document_path'
);
select tests.expect_error(
    format($$select public.create_course_note(%L, '', 'Matematik', 'Özet', null, %L, 1000)$$,
           (select a from gg), (select a from gg)::text || '/30000000-0000-4000-8000-000000000001.pdf'),
    'invalid_note'
);
select public.create_course_note((select a from gg), 'mat 101', 'Matematik I', 'Vize özeti', 'Limit ve türev',
                                 (select a from gg)::text || '/30000000-0000-4000-8000-000000000001.pdf', 123456);
select tests.reset_role();
create temp table cn as select id from public.course_notes;
grant select on cn to authenticated, service_role;
select tests.act_as_service();
select tests.expect_error(
    format($$select public.create_course_note(%L, 'MAT101', 'Matematik', 'Tekrar', null, %L, 1000)$$,
           (select a from gg), (select a from gg)::text || '/30000000-0000-4000-8000-000000000001.pdf'),
    'invalid_document_path'
);
select tests.reset_role();

select tests.act_as((select b from gg));
select tests.assert_equals(
    (select course_code || '|' || title || '|' || is_mine::text from public.list_course_notes()), 'MAT101|Vize özeti|false', 'b lists note'
);
select tests.assert_equals((select count(*) from public.list_course_notes('Mat-101')), 1::bigint, 'filter by course');
select tests.assert_equals((select count(*) from public.list_course_notes(null, 'türev')), 1::bigint, 'text search');
select tests.assert_equals((select course_code || ':' || note_count from public.list_note_courses()), 'MAT101:1', 'courses');
select tests.assert_equals(
    public.open_course_note((select id from cn)),
    (select a from gg)::text || '/30000000-0000-4000-8000-000000000001.pdf',
    'open returns the file'
);
select public.open_course_note((select id from cn));
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'course-notes'), 1::bigint, 'b reads the pdf');
select tests.reset_role();
select tests.assert_equals((select download_count from public.course_notes), 1, 'opened counted once per person');

select tests.act_as((select c from gg));
select tests.assert_equals((select count(*) from public.list_course_notes()), 0::bigint, 'other university sees none');
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'course-notes'), 0::bigint, 'nor the file');
select tests.expect_error(format($$select public.open_course_note(%L)$$, (select id from cn)), 'note_not_found');
select tests.reset_role();

select tests.act_as((select b from gg));
select tests.expect_error(format($$select public.delete_course_note(%L)$$, (select id from cn)), 'note_not_found');
select tests.reset_role();
select tests.act_as((select a from gg));
select public.delete_course_note((select id from cn));
select tests.reset_role();
select tests.act_as((select b from gg));
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'course-notes'), 0::bigint, 'deleted note hides file');
rollback;

-- 5. Badges come from real activity.
begin;
select tests.act_as((select b from gg));
select public.create_post('GENERAL', 'Soru ' || i, 'QUESTION') from generate_series(1, 3) i;
select tests.reset_role();
select tests.act_as((select a from gg));
select public.add_comment(p.id, 'Cevap') from public.posts p, generate_series(1, 4) i where p.author_id = (select b from gg);
select tests.assert_equals(
    (select points::text || '|' || array_to_string(badges, ',') from public.user_badges((select a from gg))),
    '36|PREMIUM,HELPFUL',
    'helpful answers and premium'
);
select tests.reset_role();
select tests.act_as((select c from gg));
select tests.assert_equals((select array_to_string(badges, ',') from public.user_badges((select b from gg))), '', 'no badges yet');
select tests.expect_error(format($$select * from public.user_badges(%L)$$, (select pending from gg)), 'user_not_found');
rollback;

-- 6. Reports on group messages, groups and notes; admins remove them.
begin;
select tests.act_as((select a from gg));
select public.create_group('STUDY_GROUP', 'Rapor grubu', null, null);
select tests.reset_role();
select tests.act_as((select b from gg));
select public.join_group((select id from public.groups where name = 'Rapor grubu'));
select public.send_group_message((select id from public.groups where name = 'Rapor grubu'), '40000000-0000-4000-8000-000000000001', 'Kaba mesaj');
select tests.reset_role();
select tests.act_as((select a from gg));
select public.report_content('GROUP_MESSAGE', '40000000-0000-4000-8000-000000000001', 'HARASSMENT');
select tests.reset_role();
select tests.act_as((select c from gg));
select tests.expect_error(
    $$select public.report_content('GROUP_MESSAGE', '40000000-0000-4000-8000-000000000001', 'SPAM')$$, 'report_target_not_found'
);
select tests.reset_role();
select tests.act_as((select b from gg));
select public.report_content('GROUP', (select id from public.groups where name = 'Rapor grubu'), 'INAPPROPRIATE');
select tests.reset_role();
select tests.act_as_admin((select c from gg));
select public.resolve_report((select report_id from public.list_open_reports() where target_kind = 'GROUP_MESSAGE'), 'REMOVE_CONTENT');
select public.resolve_report((select report_id from public.list_open_reports() where target_kind = 'GROUP'), 'REMOVE_CONTENT');
select tests.reset_role();
select tests.assert_equals(
    (select (select deleted_at is not null from public.group_messages) || '|' || (select deleted_at is not null from public.groups where name = 'Rapor grubu')),
    'true|true',
    'removed by admin'
);
rollback;

-- 7. Anonymous callers reach none of it.
begin;
select tests.act_as_anon();
select tests.expect_error($$select public.am_i_premium()$$, 'permission denied for function am_i_premium');
select tests.expect_error($$select * from public.list_course_notes()$$, 'permission denied for function list_course_notes');
select tests.expect_error($$select * from public.discover_groups('CHANNEL')$$, 'permission denied for function discover_groups');
rollback;
