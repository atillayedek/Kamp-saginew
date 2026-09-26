-- Crash reports: signed-in callers only, validated, rate limited, never readable by clients.

create temp table people as
select tests.create_user('crash1@example.edu.tr') as a;
grant select on people to authenticated, anon;

-- 1. A signed-in person reports a crash; fields are trimmed to their limits.
begin;
select tests.act_as((select a from people));
select public.report_client_error(
    '1.0.0 (27)', 34, 'Pixel 8', 'java.lang.IllegalStateException',
    repeat('m', 3000), repeat('s', 40000), now() - interval '1 hour'
);
select tests.assert_equals((select count(*) from public.client_errors), 0::bigint, 'reports are not readable by clients');
select tests.expect_error(
    $$insert into public.client_errors (app_version, exception_type, stacktrace, occurred_at) values ('x', 'x', 'x', now())$$,
    'new row violates row-level security policy for table "client_errors"'
);
select tests.reset_role();
select tests.assert_equals(
    (select user_id = (select a from people) and char_length(message) = 1000 and char_length(stacktrace) = 16000
            and android_sdk = 34 and device_model = 'Pixel 8'
     from public.client_errors),
    true,
    'stored with the caller and truncated'
);
rollback;

-- 2. Invalid reports are refused; the time cannot be in the future or ancient.
begin;
select tests.act_as((select a from people));
select tests.expect_error($$select public.report_client_error('1.0', 34, 'x', '', 'm', 's', now())$$, 'invalid_report');
select tests.expect_error($$select public.report_client_error('1.0', 34, 'x', 'E', 'm', '', now())$$, 'invalid_report');
select tests.expect_error($$select public.report_client_error('1.0', 34, 'x', 'E', 'm', 's', now() + interval '1 day')$$, 'invalid_report');
select tests.expect_error($$select public.report_client_error('1.0', 34, 'x', 'E', 'm', 's', now() - interval '60 days')$$, 'invalid_report');
rollback;

-- 3. At most 30 reports per person per hour.
begin;
select tests.act_as((select a from people));
select public.report_client_error('1.0', 34, 'x', 'E', 'm', 's', now()) from generate_series(1, 30);
select tests.expect_error($$select public.report_client_error('1.0', 34, 'x', 'E', 'm', 's', now())$$, 'rate_limited');
rollback;

-- 4. Anonymous callers cannot report; deleting the account deletes its reports.
begin;
select tests.act_as_anon();
select tests.expect_error(
    $$select public.report_client_error('1.0', 34, 'x', 'E', 'm', 's', now())$$,
    'permission denied for function report_client_error'
);
select tests.reset_role();
select tests.act_as((select a from people));
select public.report_client_error('1.0', 34, 'x', 'E', 'm', 's', now());
select tests.reset_role();
delete from auth.users where id = (select a from people);
select tests.assert_equals((select count(*) from public.client_errors), 0::bigint, 'reports removed with the account');
rollback;
