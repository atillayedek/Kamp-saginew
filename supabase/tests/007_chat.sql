-- Chat: pair uniqueness, membership, idempotent sends, receipts, unread counts.

create function tests.chat_student(p_email text, p_username text, p_status public.account_status default 'APPROVED')
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    v_id := tests.create_user(p_email);
    update public.profiles
    set full_name = 'Kişi ' || p_username, username = p_username, department = 'Hukuk',
        university_id = (select id from public.universities where name = 'İSTANBUL TEKNİK ÜNİVERSİTESİ'),
        account_status = p_status
    where id = v_id;
    return v_id;
end;
$$;

create temp table people as
select tests.chat_student('c1@example.edu.tr', 'cbir') as a,
       tests.chat_student('c2@example.edu.tr', 'ciki') as b,
       tests.chat_student('c3@example.edu.tr', 'cuc') as outsider,
       tests.chat_student('c4@example.edu.tr', 'cdort', 'PENDING_REVIEW') as pending;
grant select on people to authenticated, anon;

-- 1. One conversation per pair, whoever starts it; no chats with self or unapproved users.
begin;
select tests.act_as((select a from people));
create temp table conv as select public.start_conversation((select b from people)) as id;
grant select on conv to authenticated, anon;
select tests.assert_equals(public.start_conversation((select b from people)), (select id from conv), 'same id again');
select tests.expect_error(format($$select public.start_conversation(%L)$$, (select a from people)), 'recipient_not_available');
select tests.expect_error(format($$select public.start_conversation(%L)$$, (select pending from people)), 'recipient_not_available');
select tests.reset_role();
select tests.act_as((select b from people));
select tests.assert_equals(public.start_conversation((select a from people)), (select id from conv), 'same id from the other side');
select tests.reset_role();
select tests.act_as((select pending from people));
select tests.expect_error(format($$select public.start_conversation(%L)$$, (select a from people)), 'approved_student_required');
rollback;

-- 2. Only members read or write; direct table writes are impossible.
begin;
select tests.act_as((select a from people));
create temp table conv as select public.start_conversation((select b from people)) as id;
grant select on conv to authenticated, anon;
select public.send_message((select id from conv), gen_random_uuid(), 'Merhaba');
select tests.expect_error(
    format($$insert into public.messages (id, conversation_id, sender_id, body) values (gen_random_uuid(), %L, %L, 'sahte')$$,
           (select id from conv), (select b from people)),
    'new row violates row-level security policy for table "messages"'
);
select tests.reset_role();
select tests.act_as((select outsider from people));
select tests.assert_equals((select count(*) from public.messages), 0::bigint, 'outsider sees no messages');
select tests.assert_equals((select count(*) from public.conversations), 0::bigint, 'outsider sees no conversations');
select tests.expect_error(format($$select public.send_message(%L, gen_random_uuid(), 'x')$$, (select id from conv)), 'conversation_not_found');
select tests.expect_error(format($$select * from public.list_messages(%L)$$, (select id from conv)), 'conversation_not_found');
select tests.reset_role();
select tests.act_as((select b from people));
select tests.assert_equals((select count(*) from public.messages), 1::bigint, 'member sees message');
select tests.reset_role();
select tests.act_as_anon();
select tests.assert_equals((select count(*) from public.messages), 0::bigint, 'anon sees nothing');
rollback;

-- 3. Idempotent send, validation, receipts and unread counts.
begin;
select tests.act_as((select a from people));
create temp table conv as select public.start_conversation((select b from people)) as id;
create temp table ids as select gen_random_uuid() as m1;
grant select on conv, ids to authenticated;
select public.send_message((select id from conv), (select m1 from ids), '  Selam  ');
select public.send_message((select id from conv), (select m1 from ids), 'Selam');
select tests.expect_error(format($$select public.send_message(%L, gen_random_uuid(), '   ')$$, (select id from conv)), 'invalid_message');
select tests.assert_equals(
    (select count(*)::text || '|' || min(body) || '|' || bool_and(is_mine)::text || '|' || bool_or(read_by_other)::text
     from public.list_messages((select id from conv))),
    '1|Selam|true|false',
    'one message, sent but not read'
);
select tests.reset_role();

select tests.act_as((select b from people));
select tests.assert_equals(
    (select unread_count || '|' || last_message_body || '|' || other_username || '|' || last_message_is_mine::text
     from public.list_conversations()),
    '1|Selam|cbir|false',
    'unread for recipient'
);
-- The same id cannot be reused by someone else.
select tests.expect_error(format($$select public.send_message(%L, %L, 'x')$$, (select id from conv), (select m1 from ids)), 'invalid_message');
select public.mark_conversation_read((select id from conv));
select tests.assert_equals((select unread_count from public.list_conversations()), 0, 'read clears unread');
select tests.reset_role();

select tests.act_as((select a from people));
select tests.assert_equals((select read_by_other from public.list_messages((select id from conv))), true, 'read receipt');
select tests.assert_equals((select unread_count from public.list_conversations()), 0, 'own messages are not unread');
rollback;

-- 4. Messages to someone who is no longer approved are refused; rate limit applies.
begin;
select tests.act_as((select a from people));
create temp table conv as select public.start_conversation((select b from people)) as id;
grant select on conv to authenticated;
select public.send_message((select id from conv), gen_random_uuid(), 'mesaj ' || n) from generate_series(1, 30) n;
select tests.expect_error(format($$select public.send_message(%L, gen_random_uuid(), 'fazla')$$, (select id from conv)), 'rate_limited');
select tests.reset_role();
update public.profiles set account_status = 'SUSPENDED' where id = (select b from people);
update public.messages set created_at = now() - interval '2 minutes';
select tests.act_as((select a from people));
select tests.expect_error(format($$select public.send_message(%L, gen_random_uuid(), 'selam')$$, (select id from conv)), 'recipient_not_available');
rollback;

-- 5. Pagination returns every message once, newest first.
begin;
select tests.act_as((select a from people));
create temp table conv as select public.start_conversation((select b from people)) as id;
grant select on conv to authenticated;
select tests.reset_role();
insert into public.messages (id, conversation_id, sender_id, body, created_at)
select gen_random_uuid(), (select id from conv), (select a from people), 'm' || n, timestamptz '2026-09-01' + n * interval '1 second'
from generate_series(1, 120) n;
select tests.act_as((select b from people));
create temp table page1 as select * from public.list_messages((select id from conv), null, null, 100);
grant all on page1 to authenticated;
select tests.assert_equals((select count(*) from page1), 100::bigint, 'page capped at 100');
select tests.assert_equals((select body from page1 order by created_at desc limit 1), 'm120', 'newest first');
select tests.assert_equals(
    (select count(*) from public.list_messages(
        (select id from conv),
        (select created_at from page1 order by created_at, id limit 1),
        (select id from page1 order by created_at, id limit 1),
        100)),
    20::bigint,
    'second page'
);
rollback;

-- 6. Messages are published to Realtime.
begin;
select tests.assert_equals(
    (select string_agg(tablename, ',' order by tablename) from pg_publication_tables where pubname = 'supabase_realtime'),
    'conversation_members,messages',
    'realtime publication'
);
rollback;
