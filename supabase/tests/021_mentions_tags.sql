-- Northern Cyprus universities, @mentions and #topic tags in posts and comments.

create temp table mt as
select tests.approved_student('mt1@example.edu.tr', 'mt_ayse', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as a,
       tests.approved_student('mt2@example.edu.tr', 'mt_mehmet', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as b,
       tests.approved_student('mt3@example.edu.tr', 'mt_odtu', 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ') as c,
       tests.approved_student('mt4@example.edu.tr', 'mt_engel', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as blocked,
       tests.approved_student('mt5@example.edu.tr', 'mt_kibris', 'DOĞU AKDENİZ ÜNİVERSİTESİ') as cyprus,
       tests.create_user('mt-pending@example.edu.tr') as pending;
update public.profiles set username = 'mt_bekleyen' where id = (select pending from mt);
grant select on mt to authenticated, anon;

-- 1. Northern Cyprus universities are listed with "(KKTC)" in the city; the Turkish list is unchanged.
begin;
select tests.assert_equals(
    (select count(*) from public.universities where city like '% (KKTC)'), 19::bigint, 'KKTC universities');
select tests.assert_equals(
    (select city || '|' || coalesce(website_domain, '-') from public.universities where name = 'DOĞU AKDENİZ ÜNİVERSİTESİ'),
    'Gazimağusa (KKTC)|emu.edu.tr', 'city and domain');
select tests.assert_equals(
    (select count(*) from public.universities where city not like '% (KKTC)'), 206::bigint, 'Turkish list unchanged');
select tests.assert_equals(
    (select count(*) from public.universities where city like '% (KKTC)' and not is_active), 0::bigint, 'all active');
rollback;

-- 2. Tags and mentions are stored from the post text; the mentioned person is notified once.
begin;
select tests.act_as((select blocked from mt));
select public.block_user((select a from mt));
select tests.act_as((select a from mt));
create temp table p1 on commit drop as
select public.create_post('GENERAL',
    '#Kampüs etkinliği için @mt_mehmet ve @MT_ODTU gelsin. @mt_mehmet tekrar, @mt_engel, @mt_bekleyen, ' ||
    '@yok_boyle, kendim @mt_ayse. e-posta a@mt_mehmet.com #kampus #1 #ab #Bahar_Şenliği') as id;
grant select on p1 to authenticated;
select tests.reset_role();
select tests.assert_equals(
    (select string_agg(tag, ',' order by tag) from public.post_tags where post_id = (select id from p1)),
    'ab,bahar_senligi,kampus', 'folded tags, deduplicated, numbers-only skipped');
select tests.assert_equals(
    (select string_agg(p.username, ',' order by p.username)
     from public.post_mentions m join public.profiles p on p.id = m.user_id where m.post_id = (select id from p1)),
    'mt_mehmet,mt_odtu', 'only visible, unblocked, approved others; not e-mail addresses');
select tests.assert_equals(
    (select string_agg(p.username, ',' order by p.username)
     from public.notifications n join public.profiles p on p.id = n.user_id
     where n.kind = 'MENTIONED' and n.post_id = (select id from p1) and n.actor_id = (select a from mt)),
    'mt_mehmet,mt_odtu', 'one notification each');
rollback;

-- 3. A university-only post cannot mention someone from another university.
begin;
select tests.act_as((select a from mt));
select public.create_post('UNIVERSITY', 'Sadece İTÜ: @mt_mehmet @mt_odtu @mt_kibris');
select tests.reset_role();
select tests.assert_equals(
    (select string_agg(p.username, ',') from public.post_mentions m join public.profiles p on p.id = m.user_id),
    'mt_mehmet', 'same university only');
rollback;

-- 4. Comments: mentions notify, but not the post author (they already get NEW_COMMENT) or the commenter.
begin;
select tests.act_as((select a from mt));
create temp table p2 on commit drop as select public.create_post('GENERAL', 'Soru var') as id;
grant select on p2 to authenticated;
select tests.act_as((select b from mt));
select public.add_comment((select id from p2), '@mt_odtu bak, @mt_ayse sordu, @mt_mehmet ben');
select tests.reset_role();
select tests.assert_equals(
    (select string_agg(p.username, ',' order by p.username)
     from public.comment_mentions m join public.profiles p on p.id = m.user_id),
    'mt_ayse,mt_odtu', 'comment mentions stored (author of the post included)');
select tests.assert_equals(
    (select string_agg(p.username || ':' || n.kind::text, ',' order by p.username)
     from public.notifications n join public.profiles p on p.id = n.user_id
     where n.post_id = (select id from p2)),
    'mt_ayse:NEW_COMMENT,mt_odtu:MENTIONED', 'no double notification for the post author');
rollback;

-- 5. Tag feed and popular tags follow post visibility; blocked authors are hidden.
begin;
select tests.act_as((select a from mt));
select public.create_post('GENERAL', 'Genel #sinav haftası');
select public.create_post('UNIVERSITY', 'İTÜ içi #sınav');
select tests.act_as((select c from mt));
select public.create_post('UNIVERSITY', 'ODTÜ içi #SINAV');
select public.create_post('GENERAL', 'ODTÜ genel #kahve');
select tests.act_as((select b from mt));
select tests.assert_equals(
    (select string_agg(body, ',' order by body) from public.list_tag_posts('Sınav')),
    'Genel #sinav haftası,İTÜ içi #sınav', 'visible posts with the folded tag');
select tests.assert_equals(
    (select string_agg(body, ',' order by body) from public.list_tag_posts('#SINAV')),
    'Genel #sinav haftası,İTÜ içi #sınav', 'leading # and case are ignored');
select tests.assert_equals(
    (select string_agg(tag || ':' || post_count, ',' order by post_count desc, tag) from public.popular_tags(10)),
    'sinav:2,kahve:1', 'popular tags count only visible posts');
select public.block_user((select c from mt));
select tests.assert_equals(
    (select string_agg(tag, ',') from public.popular_tags(10)), 'sinav', 'blocked author''s tags hidden');
select tests.expect_error($$select * from public.list_tag_posts('a')$$, 'invalid_tag');
select tests.act_as((select pending from mt));
select tests.expect_error($$select * from public.list_tag_posts('sinav')$$, 'approved_student_required');
select tests.expect_error($$select * from public.popular_tags(10)$$, 'approved_student_required');
rollback;

-- 6. Suggestions while typing @: approved, unblocked people; university-only posts suggest classmates.
begin;
select tests.act_as((select blocked from mt));
select public.block_user((select a from mt));
select tests.act_as((select a from mt));
select tests.assert_equals(
    (select string_agg(username, ',' order by username) from public.suggest_mentions('mt_', 'GENERAL')),
    'mt_kibris,mt_mehmet,mt_odtu', 'not me, not blocked, not pending');
select tests.assert_equals(
    (select string_agg(username, ',' order by username) from public.suggest_mentions('MT', 'UNIVERSITY')),
    'mt_mehmet', 'same university for university posts');
select tests.assert_equals(
    (select display_name from public.suggest_mentions('mt_meh', 'GENERAL')),
    'Öğrenci m.', 'surname masked');
select tests.assert_equals(
    (select count(*) from public.suggest_mentions('', 'GENERAL')), 0::bigint, 'empty query returns nothing');
rollback;

-- 7. Opening a mention: username to profile id, with the profile visibility rules.
begin;
select tests.act_as((select blocked from mt));
select public.block_user((select a from mt));
select tests.act_as((select a from mt));
select tests.assert_equals(public.resolve_username('MT_Mehmet'), (select b from mt), 'case-insensitive');
select tests.assert_equals(public.resolve_username('@mt_odtu.'), (select c from mt), 'leading @ and trailing dot');
select tests.expect_error($$select public.resolve_username('mt_engel')$$, 'user_not_found');
select tests.expect_error($$select public.resolve_username('mt_bekleyen')$$, 'user_not_found');
select tests.expect_error($$select public.resolve_username('yok_boyle')$$, 'user_not_found');
rollback;

-- 8. The new tables are closed to the API; deleting a post or account removes its rows.
begin;
select tests.act_as((select a from mt));
create temp table p3 on commit drop as select public.create_post('GENERAL', '#silinecek @mt_mehmet') as id;
grant select on p3 to authenticated;
select tests.expect_error($$select * from public.post_tags$$, 'permission denied for table post_tags');
select tests.expect_error($$select * from public.post_mentions$$, 'permission denied for table post_mentions');
select tests.expect_error($$select * from public.comment_mentions$$, 'permission denied for table comment_mentions');
select tests.reset_role();
delete from auth.users where id = (select b from mt);
select tests.assert_equals((select count(*) from public.post_mentions where post_id = (select id from p3)), 0::bigint, 'mention gone with account');
delete from public.posts where id = (select id from p3);
select tests.assert_equals((select count(*) from public.post_tags where post_id = (select id from p3)), 0::bigint, 'tags gone with post');
rollback;
