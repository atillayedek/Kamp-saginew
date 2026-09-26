-- No SECURITY DEFINER function in the API schema may run for anonymous callers,
-- and trigger functions are not callable by any API role. (Supabase grants
-- EXECUTE to anon/authenticated by default, so every function must revoke it.)

begin;
select tests.assert_equals(
    (select coalesce(string_agg(p.proname, ',' order by p.proname), '')
     from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.prosecdef
       and has_function_privilege('anon', p.oid, 'execute')),
    '',
    'security definer functions executable by anon'
);
select tests.assert_equals(
    (select coalesce(string_agg(p.proname, ',' order by p.proname), '')
     from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.prorettype = 'trigger'::regtype and p.prosecdef
       and (has_function_privilege('anon', p.oid, 'execute')
            or has_function_privilege('authenticated', p.oid, 'execute'))),
    '',
    'trigger functions executable by API roles'
);
-- Every foreign key has an index on its referencing columns.
select tests.assert_equals(
    (select coalesce(string_agg(c.conrelid::regclass || '.' || c.conname, ',' order by 1), '')
     from pg_constraint c
     where c.contype = 'f' and c.connamespace = 'public'::regnamespace
       and not exists (
           select 1 from pg_index i
           where i.indrelid = c.conrelid
             and (i.indkey::int2[])[0:cardinality(c.conkey) - 1] = c.conkey
       )),
    '',
    'foreign keys without an index'
);
rollback;
