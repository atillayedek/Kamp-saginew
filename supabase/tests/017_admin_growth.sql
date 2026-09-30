-- Premium gifts and promo codes, activity statistics and in-app announcements.

create temp table ag as
select tests.approved_student('ag1@example.edu.tr', 'ag_bir', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as a,
       tests.approved_student('ag2@example.edu.tr', 'ag_iki', 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ') as b,
       tests.create_user('ag-admin@example.edu.tr') as admin,
       tests.create_user('ag-pending@example.edu.tr') as pending;
grant select on ag to authenticated, anon, service_role;

insert into public.subscription_plans (play_product_id, name, description, is_active, max_active_requirements)
values ('kampusagi.ag.premium', 'Premium', 'Test planı', true, 60),
       ('kampusagi.ag.old', 'Eski plan', 'Pasif', false, 5);
create temp table ap as
select (select id from public.subscription_plans where play_product_id = 'kampusagi.ag.premium') as active,
       (select id from public.subscription_plans where play_product_id = 'kampusagi.ag.old') as inactive;
grant select on ap to authenticated;

-- 1. Admin gifts Premium; it unlocks channels and limits, stacks, and can be revoked.
begin;
select tests.act_as((select a from ag));
select tests.expect_error(
    format($$select public.admin_grant_premium(%L, %L, 30)$$, (select a from ag), (select active from ap)), 'admin_required'
);
select tests.assert_equals(public.am_i_premium(), false, 'not premium yet');
select tests.reset_role();

select tests.act_as_admin((select admin from ag));
select tests.expect_error(
    format($$select public.admin_grant_premium(%L, %L, 30)$$, (select a from ag), (select inactive from ap)), 'plan_not_available'
);
select tests.expect_error(
    format($$select public.admin_grant_premium(%L, %L, 0)$$, (select a from ag), (select active from ap)), 'invalid_grant'
);
select public.admin_grant_premium((select a from ag), (select active from ap), 30, 'Kampüs elçisi');
select public.admin_grant_premium((select a from ag), (select active from ap), 10);
select tests.assert_equals(
    (select count(*) from public.admin_list_grants() where source = 'ADMIN' and note = 'Kampüs elçisi'), 1::bigint, 'grant listed'
);
select tests.reset_role();
select tests.assert_equals(
    (select max(expires_at) from public.premium_grants where user_id = (select a from ag)) > now() + interval '39 days',
    true,
    'gifts stack'
);

select tests.act_as((select a from ag));
select tests.assert_equals(public.am_i_premium(), true, 'gift makes premium');
select tests.assert_equals(
    (select source || '|' || max_active_requirements::text from public.my_subscription()),
    'ADMIN|60',
    'plan limits apply'
);
select public.create_group('CHANNEL', 'Hediye kanalı');
select tests.reset_role();

select tests.act_as_admin((select admin from ag));
select public.admin_revoke_grant(g.id) from (select id from public.admin_list_grants()) g;
select tests.reset_role();
select tests.act_as((select a from ag));
select tests.assert_equals(public.am_i_premium(), false, 'revoked');
select tests.assert_equals(
    (select coalesce(source, '-') || '|' || max_active_requirements::text from public.my_subscription()), '-|20', 'free limits again'
);
rollback;

-- 2. Promo codes: once per person, limited, expirable, disabled by admin.
begin;
select tests.act_as_admin((select admin from ag));
select public.admin_create_promo_code('kampus-2026', (select active from ap), 7, 1);
select tests.expect_error(
    format($$select public.admin_create_promo_code('KAMPUS-2026', %L, 7, 1)$$, (select active from ap)), 'promo_code_taken'
);
select tests.expect_error(
    format($$select public.admin_create_promo_code('x', %L, 7, 1)$$, (select active from ap)), 'invalid_promo_code'
);
select tests.assert_equals(
    char_length(public.admin_create_promo_code(null, (select active from ap), 30, 100)), 10, 'generated code length'
);
select tests.reset_role();

select tests.act_as((select a from ag));
select tests.expect_error($$select public.redeem_promo_code('YOK-BOYLE-KOD')$$, 'promo_code_invalid');
select public.redeem_promo_code(' kampus-2026 ');
select tests.assert_equals(public.am_i_premium(), true, 'code gives premium');
select tests.expect_error($$select public.redeem_promo_code('KAMPUS-2026')$$, 'promo_code_used');
select tests.reset_role();

select tests.act_as((select b from ag));
select tests.expect_error($$select public.redeem_promo_code('KAMPUS-2026')$$, 'promo_code_exhausted');
select tests.reset_role();
select tests.act_as((select pending from ag));
select tests.expect_error($$select public.redeem_promo_code('KAMPUS-2026')$$, 'approved_student_required');
select tests.reset_role();

select tests.act_as_admin((select admin from ag));
select tests.assert_equals(
    (select redemption_count || '/' || max_redemptions from public.admin_list_promo_codes() where code = 'KAMPUS-2026'), '1/1', 'counted'
);
select public.admin_disable_promo_code(c.id) from public.admin_list_promo_codes() c where c.days = 30;
select tests.reset_role();
select tests.act_as((select b from ag));
select tests.expect_error(
    format($$select public.redeem_promo_code(%L)$$, (select code from public.promo_codes where days = 30)), 'promo_code_invalid'
);
select tests.assert_equals((select count(*) from public.promo_codes), 0::bigint, 'codes not readable');
rollback;

-- 3. Admin lists count gifted Premium too.
begin;
select tests.act_as_admin((select admin from ag));
select public.admin_grant_premium((select b from ag), (select active from ap), 5);
select tests.assert_equals(
    (select is_premium from public.admin_list_users('ag_iki', null, 10, 0)), true, 'user list shows premium'
);
select tests.assert_equals(
    (select premium from public.admin_university_stats() where name = 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ') >= 1, true, 'university premium'
);
select tests.reset_role();
select tests.act_as_service();
select tests.assert_equals(
    (select count(*) from public.broadcast_recipients('PREMIUM', false) where user_id = (select b from ag)), 1::bigint, 'premium audience'
);
rollback;

-- 4. Activity: one row per person per day; only admins read the statistics.
begin;
select tests.act_as((select a from ag));
select public.touch_activity();
select public.touch_activity();
select tests.expect_error($$select public.admin_activity_stats()$$, 'admin_required');
select tests.reset_role();
select tests.act_as((select b from ag));
select public.touch_activity();
select tests.reset_role();
insert into public.user_activity_days (user_id, day)
values ((select a from ag), (now() at time zone 'Europe/Istanbul')::date - 3);
select tests.act_as_admin((select admin from ag));
select tests.assert_equals(
    (select (s ->> 'today') || '|' || (s ->> 'last_7_days') || '|' || jsonb_array_length(s -> 'daily')::text
     from (select public.admin_activity_stats() as s) x),
    '2|2|30',
    'activity counts'
);
select tests.assert_equals(
    (select public.admin_activity_stats() -> 'top_universities' -> 0 ->> 'active'), '1', 'top universities'
);
select tests.reset_role();
select tests.act_as((select a from ag));
select tests.assert_equals((select count(*) from public.user_activity_days), 0::bigint, 'activity not readable');
select tests.reset_role();
select tests.act_as_anon();
select tests.expect_error($$select public.touch_activity()$$, 'permission denied for function touch_activity');
rollback;

-- 5. Announcements: audience by university, dismissable, ended by admin.
begin;
select tests.act_as((select a from ag));
select tests.expect_error(
    $$select public.admin_create_announcement('Başlık', 'Metin', null, now() + interval '1 day')$$, 'admin_required'
);
select tests.reset_role();
select tests.act_as_admin((select admin from ag));
select tests.expect_error(
    $$select public.admin_create_announcement('Başlık', 'Metin', null, now() + interval '91 days')$$, 'invalid_announcement'
);
select public.admin_create_announcement('Herkese', 'Bakım çalışması var', null, now() + interval '1 day');
select public.admin_create_announcement('İTÜ', 'İTÜ öğrencilerine özel', (select id from public.universities where name = 'İSTANBUL TEKNİK ÜNİVERSİTESİ'), now() + interval '2 days');
select tests.reset_role();

select tests.act_as((select a from ag));
select tests.assert_equals((select string_agg(title, ',' order by title) from public.active_announcements()), 'Herkese,İTÜ', 'itu sees both');
select tests.assert_equals((select count(*) from public.announcements), 2::bigint, 'table read for realtime');
select public.dismiss_announcement((select id from public.active_announcements() where title = 'Herkese'));
select tests.assert_equals((select string_agg(title, ',') from public.active_announcements()), 'İTÜ', 'dismissed hidden');
select tests.reset_role();

select tests.act_as((select b from ag));
select tests.assert_equals((select string_agg(title, ',') from public.active_announcements()), 'Herkese', 'odtu sees general only');
select tests.assert_equals((select count(*) from public.announcements), 1::bigint, 'odtu table read');
select tests.reset_role();

select tests.act_as_admin((select admin from ag));
select tests.assert_equals(
    (select dismissed_count from public.admin_list_announcements() where title = 'Herkese'), 1::bigint, 'dismiss counted'
);
select public.admin_end_announcement((select id from public.admin_list_announcements() where title = 'Herkese'));
select tests.reset_role();
select tests.act_as((select b from ag));
select tests.assert_equals((select count(*) from public.active_announcements()), 0::bigint, 'ended announcement gone');
select tests.reset_role();
select tests.act_as((select pending from ag));
select tests.expect_error($$select * from public.active_announcements()$$, 'approved_student_required');
select tests.assert_equals((select count(*) from public.announcements), 0::bigint, 'pending reads none');
rollback;
