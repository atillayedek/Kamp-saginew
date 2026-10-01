-- Schedules: only compliance staff; without pg_cron (local test database) the call says so.
begin;
create temp table c as select tests.create_user('cron-staff@example.edu.tr') as staff, tests.create_user('cron-student@example.edu.tr') as student;
grant select on c to authenticated;

select tests.act_as((select student from c));
select tests.expect_error($$select public.admin_ensure_schedules()$$, 'admin_required');
select tests.reset_role();

select tests.act_as_staff((select staff from c), array['compliance'], 'aal2', 'Zamanlama kurulumu');
select tests.assert_equals(public.admin_schedule_status() -> 'jobs', '[]'::jsonb, 'nothing scheduled without pg_cron');
select tests.expect_error($$select public.admin_ensure_schedules()$$,
    case when exists (select 1 from pg_available_extensions where name = 'pg_cron') then 'pg_cron_not_enabled' else 'pg_cron_unavailable' end);
select tests.reset_role();
rollback;
