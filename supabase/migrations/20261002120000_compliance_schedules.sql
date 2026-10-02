-- The access-log copy and the daily destruction run on pg_cron. When pg_cron is enabled after
-- the earlier migrations ran (Dashboard -> Database -> Extensions), compliance staff create the
-- schedules from the panel (KVKK ve Uyum -> İmha -> "Zamanlamayı kur"); calling it again is harmless.

create function public.admin_ensure_schedules()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_jobs jsonb := '[]';
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if not exists (select 1 from pg_extension where extname = 'pg_cron') then
        if not exists (select 1 from pg_available_extensions where name = 'pg_cron') then
            raise exception using errcode = 'P0001', message = 'pg_cron_unavailable';
        end if;
        begin
            create extension if not exists pg_cron;
        exception when others then
            raise exception using errcode = 'P0001', message = 'pg_cron_not_enabled';
        end;
    end if;
    -- cron.schedule replaces a job with the same name.
    execute $q$select cron.schedule('kvkk-sync-access-logs', '*/5 * * * *', 'select public.sync_auth_access_logs()')$q$;
    execute $q$select cron.schedule('kvkk-daily-retention', '17 3 * * *', 'select public.run_daily_retention()')$q$;
    execute $q$select coalesce(jsonb_agg(jsonb_build_object('name', jobname, 'schedule', schedule, 'active', active)), '[]')
               from cron.job where jobname like 'kvkk-%'$q$ into v_jobs;
    perform public.write_admin_audit('schedules.ensure', 'cron', null,
                                     coalesce(public.request_audit_reason(), 'Zamanlama kurulumu'), jsonb_build_object('jobs', v_jobs));
    return jsonb_build_object('jobs', v_jobs);
end;
$$;

-- What is scheduled right now (for the panel).
create function public.admin_schedule_status()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_jobs jsonb := '[]';
begin
    if not public.has_staff_role('compliance') then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        execute $q$select coalesce(jsonb_agg(jsonb_build_object('name', jobname, 'schedule', schedule, 'active', active)), '[]')
                   from cron.job where jobname like 'kvkk-%'$q$ into v_jobs;
    end if;
    return jsonb_build_object('jobs', v_jobs);
end;
$$;

revoke all on function public.admin_ensure_schedules() from public, anon;
grant execute on function public.admin_ensure_schedules() to authenticated;
revoke all on function public.admin_schedule_status() from public, anon;
grant execute on function public.admin_schedule_status() to authenticated;
