-- Web admin panel: overview, users, purchases, universities, broadcasts, marketing consent.

create function tests.ap_student(p_email text, p_username text, p_status public.account_status)
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    v_id := tests.create_user(p_email);
    update public.profiles
    set full_name = 'Kişi ' || p_username, username = p_username, department = 'Hukuk',
        university_id = (select id from public.universities where name = 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ'),
        account_status = p_status
    where id = v_id;
    return v_id;
end;
$$;

create temp table ap as
select tests.ap_student('ap1@example.edu.tr', 'apbir', 'APPROVED') as a,
       tests.ap_student('ap2@example.edu.tr', 'apiki', 'PENDING_REVIEW') as b,
       tests.ap_student('ap3@example.edu.tr', 'apuc', 'SUSPENDED') as c,
       tests.create_user('ap-admin@example.edu.tr') as admin;
grant select on ap to authenticated, service_role, anon;

insert into public.subscription_plans (play_product_id, name, description, is_active, ai_analyze_daily, ai_publish_daily, max_active_requirements)
values ('kampusagi.admin_test', 'Admin Test', 'Panel testi için plan', true, 10, 10, 10);

-- 1. Purchase events are recorded only by the service role, once per order (or token + period).
begin;
select tests.act_as((select a from ap));
select tests.expect_error(
    $$select public.record_purchase_event((select a from ap), 'kampusagi.admin_test', 'token-ap-1234567', 'GPA.1', now() + interval '30 days', 49990000, 'TRY')$$,
    'permission denied for function record_purchase_event'
);
select tests.assert_equals((select count(*) from public.purchase_events), 0::bigint, 'events not readable by users');
select tests.reset_role();
select tests.act_as_service();
select public.record_purchase_event((select a from ap), 'kampusagi.admin_test', 'token-ap-1234567', 'GPA.1', now() + interval '30 days', 49990000, 'TRY');
select public.record_purchase_event((select a from ap), 'kampusagi.admin_test', 'token-ap-1234567', 'GPA.1', now() + interval '30 days', 49990000, 'TRY');
select public.record_purchase_event((select a from ap), 'kampusagi.admin_test', 'token-ap-1234567', 'GPA.1..0', now() + interval '60 days', 49990000, 'TRY');
select public.record_purchase_event((select b from ap), 'kampusagi.admin_test', 'token-ap-7654321', null, now() + interval '30 days', null, null);
select public.record_purchase_event((select b from ap), 'kampusagi.admin_test', 'token-ap-7654321', null, now() + interval '30 days', null, null);
select tests.expect_error(
    $$select public.record_purchase_event((select a from ap), 'kampusagi.admin_test', 'token-ap-1234567', 'GPA.2', now() + interval '1 day', 1000, 'try')$$,
    'invalid_purchase_event'
);
select tests.expect_error(
    $$select public.record_purchase_event((select a from ap), 'kampusagi.missing', 'token-ap-1234567', 'GPA.3', now() + interval '1 day', 1000, 'TRY')$$,
    'plan_not_available'
);
select tests.reset_role();
select tests.assert_equals((select count(*) from public.purchase_events), 3::bigint, 'duplicates ignored');
select tests.assert_equals(
    (select sum(amount_micros) from public.purchase_events where currency = 'TRY'), 99980000::numeric, 'amounts stored'
);
rollback;

-- 2. Every admin RPC refuses anonymous callers and non-admins.
begin;
select tests.act_as_anon();
select tests.expect_error($$select public.admin_overview()$$, 'permission denied for function admin_overview');
select tests.reset_role();
select tests.act_as((select a from ap));
select tests.expect_error($$select public.admin_overview()$$, 'admin_required');
select tests.expect_error($$select * from public.admin_list_users(null, null, 50, 0)$$, 'admin_required');
select tests.expect_error($$select * from public.admin_list_purchases(50, 0)$$, 'admin_required');
select tests.expect_error($$select * from public.admin_university_stats()$$, 'admin_required');
select tests.expect_error($$select * from public.admin_list_broadcasts(20)$$, 'admin_required');
select tests.expect_error(format($$select public.admin_reinstate_user(%L)$$, (select c from ap)), 'admin_required');
rollback;

-- 3. Overview numbers come from the real tables.
begin;
select tests.act_as_service();
select public.record_purchase_event((select a from ap), 'kampusagi.admin_test', 'token-ap-1234567', 'GPA.1', now() + interval '30 days', 49990000, 'TRY');
select public.record_purchase_event((select b from ap), 'kampusagi.admin_test', 'token-ap-7654321', 'GPA.9', now() + interval '30 days', 5000000, 'USD');
select public.record_entitlement((select a from ap), 'kampusagi.admin_test', 'token-ap-1234567', now() + interval '30 days');
select tests.reset_role();
create temp table expected on commit drop as
select (select count(*) from public.profiles) as users,
       (select count(*) from public.profiles where account_status = 'APPROVED') as approved,
       (select count(*) from public.entitlements where expires_at > now()) as premium;
grant select on expected to authenticated;
select tests.act_as_admin((select admin from ap));
select tests.assert_equals((select (public.admin_overview() ->> 'total_users')::bigint), (select users from expected), 'total users');
select tests.assert_equals(
    (select (public.admin_overview() -> 'status_counts' ->> 'APPROVED')::bigint), (select approved from expected), 'approved count'
);
select tests.assert_equals((select (public.admin_overview() ->> 'active_premium')::bigint), (select premium from expected), 'premium');
select tests.assert_equals((select (public.admin_overview() ->> 'purchase_count')::bigint), 2::bigint, 'purchase count');
select tests.assert_equals(
    (select public.admin_overview() -> 'revenue' -> 'TRY' ->> 'total_micros'), '49990000', 'revenue per currency'
);
select tests.assert_equals(
    (select public.admin_overview() -> 'revenue' -> 'USD' ->> 'last_30_days_micros'), '5000000', 'recent revenue'
);
select tests.assert_equals(
    (select jsonb_array_length(public.admin_overview() -> 'daily')), 30, 'thirty days of history'
);
rollback;

-- 4. Users list: search, status filter, premium flag, total count.
begin;
select tests.act_as_service();
select public.record_entitlement((select a from ap), 'kampusagi.admin_test', 'token-ap-1234567', now() + interval '30 days');
select tests.reset_role();
select tests.act_as_admin((select admin from ap));
select tests.assert_equals(
    (select username || '|' || is_premium::text || '|' || total_count::text || '|' || university_name
     from public.admin_list_users('apbir', null, 50, 0)),
    'apbir|true|1|ORTA DOĞU TEKNİK ÜNİVERSİTESİ',
    'search by username'
);
select tests.assert_equals(
    (select count(*) from public.admin_list_users('ap2@example', null, 50, 0)), 1::bigint, 'search by email'
);
select tests.assert_equals(
    (select string_agg(username, ',') from public.admin_list_users('ap', 'SUSPENDED', 50, 0)), 'apuc', 'status filter'
);
select tests.expect_error($$select * from public.admin_list_users(null, null, 0, 0)$$, 'invalid_page');
select tests.expect_error($$select * from public.admin_list_users(null, null, 501, 0)$$, 'invalid_page');
rollback;

-- 5. Purchases list and university stats.
begin;
select tests.act_as_service();
select public.record_purchase_event((select a from ap), 'kampusagi.admin_test', 'token-ap-1234567', 'GPA.1', now() + interval '30 days', 49990000, 'TRY');
select public.record_entitlement((select a from ap), 'kampusagi.admin_test', 'token-ap-1234567', now() + interval '30 days');
select tests.reset_role();
create temp table metu on commit drop as
select count(*) as students,
       count(*) filter (where p.account_status = 'APPROVED') as approved,
       count(*) filter (where exists (select 1 from public.entitlements e where e.user_id = p.id and e.expires_at > now())) as premium
from public.profiles p join public.universities u on u.id = p.university_id
where u.name = 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ';
grant select on metu to authenticated;
select tests.act_as_admin((select admin from ap));
select tests.assert_equals(
    (select username || '|' || plan_name || '|' || amount_micros::text || '|' || currency || '|' || total_count::text
     from public.admin_list_purchases(50, 0)),
    'apbir|Admin Test|49990000|TRY|1',
    'purchase list'
);
select tests.assert_equals(
    (select students::text || '|' || approved::text || '|' || premium::text
     from public.admin_university_stats() where name = 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ'),
    (select students::text || '|' || approved::text || '|' || premium::text from metu),
    'university stats'
);
rollback;

-- 6. Reinstating a suspended account restores the status its verification implies.
begin;
insert into public.student_verifications (user_id, document_path, status, reviewed_at)
values ((select c from ap), (select c from ap)::text || '/doc.pdf', 'APPROVED', now());
select tests.act_as_admin((select admin from ap));
select public.admin_reinstate_user((select c from ap));
select tests.expect_error(format($$select public.admin_reinstate_user(%L)$$, (select a from ap)), 'user_not_suspended');
select tests.reset_role();
select tests.assert_equals(
    (select account_status::text from public.profiles where id = (select c from ap)), 'APPROVED', 'reinstated to approved'
);
rollback;

begin;
select tests.act_as_admin((select admin from ap));
select public.admin_reinstate_user((select c from ap));
select tests.reset_role();
select tests.assert_equals(
    (select account_status::text from public.profiles where id = (select c from ap)), 'DOCUMENT_REQUIRED',
    'without a verification the document is required again'
);
rollback;

-- 7. Marketing consent: only the person changes it; broadcasts respect it.
begin;
select tests.act_as((select a from ap));
select public.set_marketing_consent(true);
select tests.assert_equals(
    (select marketing_opt_in from public.profiles where id = (select a from ap)), true, 'own consent readable'
);
update public.profiles set marketing_opt_in = true where id = (select b from ap);
select tests.reset_role();
select tests.assert_equals(
    (select marketing_opt_in from public.profiles where id = (select b from ap)), false, 'no direct write to others'
);
select tests.act_as((select b from ap));
select public.set_marketing_consent(false);
select tests.reset_role();
select tests.act_as_service();
select tests.assert_equals(
    (select string_agg(email, ',' order by email collate "C") from public.broadcast_recipients('ALL', true) where email like 'ap%'),
    'ap1@example.edu.tr',
    'marketing only to people who opted in'
);
select tests.assert_equals(
    (select string_agg(email, ',' order by email collate "C") from public.broadcast_recipients('ALL', false) where email like 'ap%'),
    'ap-admin@example.edu.tr,ap1@example.edu.tr,ap2@example.edu.tr',
    'announcements skip suspended accounts'
);
select tests.assert_equals(
    (select string_agg(email, ',') from public.broadcast_recipients('PENDING_REVIEW', false) where email like 'ap%'),
    'ap2@example.edu.tr',
    'audience by status'
);
select tests.expect_error($$select * from public.broadcast_recipients('EVERYONE', false)$$, 'invalid_audience');
select tests.reset_role();
create temp table pending_count on commit drop as
select count(*) as n from public.profiles where account_status = 'PENDING_REVIEW';
grant select on pending_count to authenticated;
select tests.act_as_admin((select admin from ap));
select tests.expect_error($$select * from public.broadcast_recipients('ALL', false)$$, 'permission denied for function broadcast_recipients');
select tests.assert_equals(
    public.admin_broadcast_audience_size('PENDING_REVIEW', false),
    (select n from pending_count),
    'audience size for admins'
);
select tests.expect_error($$select public.admin_broadcast_audience_size('EVERYONE', false)$$, 'invalid_audience');
select tests.reset_role();
select tests.act_as((select a from ap));
select tests.expect_error($$select public.admin_broadcast_audience_size('ALL', false)$$, 'admin_required');
rollback;

-- 8. Unsubscribe by user id (the Edge Function checks the signed link first).
begin;
select tests.act_as((select a from ap));
select public.set_marketing_consent(true);
select tests.reset_role();
select tests.act_as_service();
select public.unsubscribe_marketing((select a from ap));
select tests.reset_role();
select tests.assert_equals(
    (select marketing_opt_in::text || '|' || (marketing_opt_in_at is null)::text from public.profiles where id = (select a from ap)),
    'false|true',
    'unsubscribed'
);
rollback;

-- 9. Broadcast history is visible to admins only.
begin;
insert into public.email_broadcasts (sent_by, audience, marketing, subject, body, recipient_count, sent_count, failed_count, status, finished_at)
values ((select admin from ap), 'ALL', false, 'Bakım', 'Yarın bakım var.', 3, 3, 0, 'SENT', now());
select tests.act_as((select a from ap));
select tests.assert_equals((select count(*) from public.email_broadcasts), 0::bigint, 'not readable by users');
select tests.reset_role();
select tests.act_as_admin((select admin from ap));
select tests.assert_equals(
    (select subject || '|' || status || '|' || sent_count::text from public.admin_list_broadcasts(20)),
    'Bakım|SENT|3',
    'history for admins'
);
rollback;

-- 10. Deleting an account keeps the revenue record without the person.
begin;
select tests.act_as_service();
select public.record_purchase_event((select a from ap), 'kampusagi.admin_test', 'token-ap-1234567', 'GPA.1', now() + interval '30 days', 49990000, 'TRY');
select tests.reset_role();
delete from auth.users where id = (select a from ap);
select tests.assert_equals(
    (select (user_id is null)::text || '|' || amount_micros::text from public.purchase_events), 'true|49990000', 'revenue kept'
);
rollback;
