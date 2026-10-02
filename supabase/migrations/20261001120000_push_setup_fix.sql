-- set_push_functions_url updated push_settings without a WHERE clause. Supabase
-- runs pg-safeupdate for API sessions, which rejects such statements, so the
-- admin panel's "Bildirimleri etkinleştir" failed with a server error.

create or replace function public.set_push_functions_url(p_url text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_url is null or p_url !~ '^https://[a-z0-9-]+\.supabase\.co/functions/v1$' then
        raise exception using errcode = 'P0001', message = 'invalid_functions_url';
    end if;
    update public.push_settings set functions_url = p_url, updated_at = now() where id;
end;
$$;

revoke all on function public.set_push_functions_url(text) from public, anon, authenticated;
grant execute on function public.set_push_functions_url(text) to service_role;
