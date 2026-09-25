-- Test helpers (test database only).

create schema tests;
grant usage on schema tests to anon, authenticated, service_role;

-- Creates an auth user (the trigger creates the profile) and returns its id.
create function tests.create_user(p_email text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    insert into auth.users (email) values (p_email) returning id into v_id;
    return v_id;
end;
$$;

-- Switches the current transaction to an API role acting as the given user.
create function tests.act_as(p_user uuid) returns void
language plpgsql as $$
begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
end;
$$;

create function tests.act_as_anon() returns void
language plpgsql as $$
begin
    perform set_config('request.jwt.claims', '{"role":"anon"}', true);
    execute 'set local role anon';
end;
$$;

create function tests.reset_role() returns void
language plpgsql as $$
begin
    execute 'reset role';
    perform set_config('request.jwt.claims', '', true);
end;
$$;

-- Runs a statement and asserts it fails with the given error message.
create function tests.expect_error(p_sql text, p_message text) returns void
language plpgsql as $$
begin
    begin
        execute p_sql;
    exception when others then
        if sqlerrm = p_message then
            return;
        end if;
        raise exception 'expected error "%" but got "%" (%) for: %', p_message, sqlerrm, sqlstate, p_sql;
    end;
    raise exception 'expected error "%" but the statement succeeded: %', p_message, p_sql;
end;
$$;

create function tests.assert_equals(p_actual anyelement, p_expected anyelement, p_label text) returns void
language plpgsql as $$
begin
    if p_actual is distinct from p_expected then
        raise exception 'assertion failed [%]: expected %, got %', p_label, p_expected, p_actual;
    end if;
end;
$$;

grant execute on all functions in schema tests to anon, authenticated, service_role;
