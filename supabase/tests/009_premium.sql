-- Premium: plans as data, server-only entitlements, limits enforced in the database.

create function tests.p_student(p_email text, p_username text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    v_id := tests.create_user(p_email);
    update public.profiles
    set full_name = 'Kişi ' || p_username, username = p_username, department = 'Hukuk',
        university_id = (select id from public.universities where name = 'İSTANBUL TEKNİK ÜNİVERSİTESİ'),
        account_status = 'APPROVED'
    where id = v_id;
    return v_id;
end;
$$;

create temp table people as
select tests.p_student('p1@example.edu.tr', 'pbir') as a, tests.p_student('p2@example.edu.tr', 'piki') as b;
grant select on people to authenticated, service_role;

insert into public.subscription_plans (play_product_id, name, description, is_active, ai_analyze_daily, ai_publish_daily, max_active_requirements)
values ('kampusagi.plus', 'Plus', 'Daha fazla yapay zekâ analizi', true, 40, 15, 25),
       ('kampusagi.draft', 'Taslak', 'Henüz satışta değil', false, 99, 99, 99);

-- 1. Only active plans are listed, only to approved students.
begin;
select tests.act_as((select a from people));
select tests.assert_equals((select string_agg(play_product_id, ',') from public.list_plans()), 'kampusagi.plus', 'active plans');
select tests.assert_equals((select count(*) from public.subscription_plans), 0::bigint, 'table not readable');
select tests.assert_equals(
    (select coalesce(plan_name, '-') || '|' || ai_analyze_daily from public.my_subscription()),
    '-|30',
    'free limits'
);
rollback;

-- 2. Entitlements are written only by the service role, for active plans and live purchases.
begin;
select tests.act_as((select a from people));
select tests.expect_error(
    format($$select public.record_entitlement(%L, 'kampusagi.plus', 'token-1234567890', now() + interval '30 days')$$, (select a from people)),
    'permission denied for function record_entitlement'
);
select tests.expect_error(
    format($$insert into public.entitlements (user_id, plan_id, purchase_token, expires_at)
             values (%L, (select id from public.subscription_plans limit 1), 'token-x-123456', now() + interval '1 day')$$, (select a from people)),
    'new row violates row-level security policy for table "entitlements"'
);
select tests.reset_role();
select tests.act_as_service();
select tests.expect_error(
    format($$select public.record_entitlement(%L, 'kampusagi.draft', 'token-1234567890', now() + interval '30 days')$$, (select a from people)),
    'plan_not_available'
);
select tests.expect_error(
    format($$select public.record_entitlement(%L, 'kampusagi.plus', 'token-1234567890', now() - interval '1 minute')$$, (select a from people)),
    'purchase_not_active'
);
select public.record_entitlement((select a from people), 'kampusagi.plus', 'token-1234567890', now() + interval '30 days');
-- The same purchase token cannot unlock a second account.
select tests.expect_error(
    format($$select public.record_entitlement(%L, 'kampusagi.plus', 'token-1234567890', now() + interval '30 days')$$, (select b from people)),
    'purchase_belongs_to_another_account'
);
-- Renewal of the same purchase updates the expiry.
select public.record_entitlement((select a from people), 'kampusagi.plus', 'token-1234567890', now() + interval '60 days');
select tests.reset_role();
select tests.act_as((select a from people));
select tests.assert_equals(
    (select plan_name || '|' || ai_analyze_daily || '|' || max_active_requirements from public.my_subscription()),
    'Plus|40|25',
    'plan limits'
);
rollback;

-- 3. The AI quota follows the plan and falls back when the entitlement expires.
begin;
select tests.act_as_service();
select public.record_entitlement((select a from people), 'kampusagi.plus', 'token-1234567890', now() + interval '30 days');
select public.consume_ai_quota((select a from people), 'analyze') from generate_series(1, 40);
select tests.expect_error(format($$select public.consume_ai_quota(%L, 'analyze')$$, (select a from people)), 'ai_quota_exceeded');
select public.consume_ai_quota((select b from people), 'analyze') from generate_series(1, 30);
select tests.expect_error(format($$select public.consume_ai_quota(%L, 'analyze')$$, (select b from people)), 'ai_quota_exceeded');
select tests.reset_role();
delete from public.ai_usage;
update public.entitlements set expires_at = now() - interval '1 second';
select tests.act_as_service();
select public.consume_ai_quota((select a from people), 'analyze') from generate_series(1, 30);
select tests.expect_error(format($$select public.consume_ai_quota(%L, 'analyze')$$, (select a from people)), 'ai_quota_exceeded');
rollback;

-- 4. Deactivating a plan removes its benefits immediately.
begin;
select tests.act_as_service();
select public.record_entitlement((select a from people), 'kampusagi.plus', 'token-1234567890', now() + interval '30 days');
select tests.reset_role();
update public.subscription_plans set is_active = false where play_product_id = 'kampusagi.plus';
select tests.act_as((select a from people));
select tests.assert_equals((select ai_analyze_daily from public.my_subscription()), 30, 'inactive plan gives free limits');
rollback;
