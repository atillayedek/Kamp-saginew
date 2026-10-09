begin;
create temp table billing_person as select tests.create_user('rtdn@example.edu.tr') as id;
grant select on billing_person to authenticated, service_role;
insert into public.subscription_plans (play_product_id, name, description, is_active, max_active_requirements)
values ('kampusagi.rtdn', 'RTDN test', 'RTDN test', true, 25);
insert into public.entitlements(user_id, plan_id, purchase_token, expires_at)
select id, (select id from public.subscription_plans where play_product_id = 'kampusagi.rtdn'), 'rtdn-token-12345', now() + interval '30 days'
from billing_person;

select tests.act_as((select id from billing_person));
select tests.expect_error($$select * from public.billing_owner_for_token('rtdn-token-12345')$$,
    'permission denied for function billing_owner_for_token');
select tests.expect_error($$select public.reconcile_billing_entitlement('rtdn-token-12345', now() + interval '365 days', 1)$$,
    'permission denied for function reconcile_billing_entitlement');
select tests.reset_role();
select tests.act_as_service();
select tests.assert_equals((select count(*) from public.billing_owner_for_token('rtdn-token-12345')), 1::bigint, 'service lookup');
select tests.assert_equals((select count(*) from public.billing_owner_for_token('unknown-token')), 0::bigint, 'unknown token');
select public.reconcile_billing_entitlement('rtdn-token-12345', null, 2000);
select public.reconcile_billing_entitlement('rtdn-token-12345', now() + interval '365 days', 1000);
select public.reconcile_billing_entitlement('rtdn-token-12345', now() + interval '365 days', 2000);
select tests.reset_role();
select tests.assert_equals((select expires_at <= now() from public.entitlements where purchase_token = 'rtdn-token-12345'), true,
    'revoked access cannot be resurrected by older or duplicate events');
select tests.act_as_service();
select public.reconcile_billing_entitlement('rtdn-token-12345', now() + interval '60 days', 3000);
select tests.reset_role();
select tests.assert_equals((select expires_at > now() + interval '59 days' from public.entitlements where purchase_token = 'rtdn-token-12345'), true,
    'renewal updates expiry');
select tests.assert_equals((select user_id from public.entitlements where purchase_token = 'rtdn-token-12345'), (select id from billing_person),
    'owner remains unchanged');
rollback;
