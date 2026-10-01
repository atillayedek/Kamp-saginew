-- Notifications: created from real events, private, collapsed per conversation.

create function tests.n_student(p_email text, p_username text)
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
select tests.n_student('n1@example.edu.tr', 'nbir') as a,
       tests.n_student('n2@example.edu.tr', 'niki') as b;
grant select on people to authenticated, service_role;

-- 1. Messages notify the recipient only, once per conversation until read.
begin;
select tests.act_as((select a from people));
create temp table conv as select public.start_conversation((select b from people)) as id;
grant select on conv to authenticated;
select public.send_message((select id from conv), gen_random_uuid(), 'bir');
select public.send_message((select id from conv), gen_random_uuid(), 'iki');
select tests.assert_equals((select count(*) from public.list_notifications()), 0::bigint, 'sender gets none');
select tests.reset_role();
select tests.act_as((select b from people));
select tests.assert_equals(
    (select count(*)::text || '|' || min(kind::text) || '|' || min(actor_username) from public.list_notifications()),
    '1|NEW_MESSAGE|nbir',
    'one collapsed notification'
);
-- Reading the conversation clears it; a later message creates a new one.
select public.mark_conversation_read((select id from conv));
select tests.assert_equals((select count(*) from public.list_notifications() where read_at is null), 0::bigint, 'cleared by reading');
select tests.reset_role();
select tests.act_as((select a from people));
select public.send_message((select id from conv), gen_random_uuid(), 'üç');
select tests.reset_role();
select tests.act_as((select b from people));
select tests.assert_equals((select count(*) from public.list_notifications() where read_at is null), 1::bigint, 'new after read');
select tests.assert_equals((select count(*) from public.list_notifications()), 2::bigint, 'history kept');
rollback;

-- 2. Comments notify the post author, but not for their own comments.
begin;
select tests.act_as((select a from people));
create temp table p as select public.create_post('GENERAL', 'Gönderi') as id;
grant select on p to authenticated;
select public.add_comment((select id from p), 'kendi yorumum');
select tests.assert_equals((select count(*) from public.list_notifications()), 0::bigint, 'no self notification');
select tests.reset_role();
select tests.act_as((select b from people));
select public.add_comment((select id from p), 'güzel');
select tests.reset_role();
select tests.act_as((select a from people));
select tests.assert_equals(
    (select kind::text || '|' || (post_id = (select id from p))::text from public.list_notifications()),
    'NEW_COMMENT|true',
    'comment notification'
);
rollback;

-- 3. Verification decisions notify the applicant.
begin;
create temp table v as select tests.create_user('n3@example.edu.tr') as applicant, tests.create_user('nadmin@example.edu.tr') as admin;
grant select on v to authenticated, service_role;
update public.profiles set full_name = 'Başvuran', username = 'basvuran', department = 'Hukuk',
    university_id = (select id from public.universities limit 1), account_status = 'DOCUMENT_REQUIRED'
where id = (select applicant from v);
insert into storage.objects (bucket_id, name, owner)
values ('student-documents', (select applicant from v)::text || '/' || gen_random_uuid()::text || '.pdf', (select applicant from v));
select tests.act_as_service();
create temp table req as select public.submit_student_document(
    (select applicant from v),
    (select name from storage.objects where owner = (select applicant from v))
) as id;
select tests.reset_role();
grant select on req to authenticated;
select tests.act_as_admin((select admin from v));
select public.review_student_verification((select id from req), true);
select tests.reset_role();
select tests.act_as((select applicant from v));
select tests.assert_equals((select kind::text from public.list_notifications()), 'VERIFICATION_APPROVED', 'approval notification');
rollback;

-- 4. Notifications are private; marking read works; clients cannot write them.
begin;
select tests.act_as((select a from people));
create temp table conv as select public.start_conversation((select b from people)) as id;
grant select on conv to authenticated;
select public.send_message((select id from conv), gen_random_uuid(), 'selam');
select tests.assert_equals((select count(*) from public.notifications), 0::bigint, 'a cannot read b''s notifications');
select tests.expect_error(
    format($$insert into public.notifications (user_id, kind) values (%L, 'VERIFICATION_APPROVED')$$, (select a from people)),
    'new row violates row-level security policy for table "notifications"'
);
select tests.reset_role();
select tests.act_as((select b from people));
select public.mark_notifications_read();
select tests.assert_equals((select count(*) from public.list_notifications() where read_at is null), 0::bigint, 'mark all read');
rollback;

-- 5. Device tokens (D55): registration moves a token to its latest owner; push data is service-role only.
begin;
select tests.act_as((select a from people));
select public.register_device_token('token-aaaaaaaaaaaaaaaaaaaaaaaaaaaa');
select tests.expect_error($$select public.register_device_token('short')$$, 'invalid_token');
select tests.assert_equals((select count(*) from public.device_tokens), 0::bigint, 'tokens not readable by clients');
select tests.expect_error(format($$select * from public.push_payload(%L)$$, gen_random_uuid()), 'permission denied for function push_payload');
select tests.reset_role();
select tests.act_as((select b from people));
select public.register_device_token('token-aaaaaaaaaaaaaaaaaaaaaaaaaaaa');
create temp table conv as select public.start_conversation((select a from people)) as id;
select public.send_message((select id from conv), gen_random_uuid(), 'selam');
select tests.reset_role();
select tests.assert_equals(
    (select user_id from public.device_tokens where token = 'token-aaaaaaaaaaaaaaaaaaaaaaaaaaaa'),
    (select b from people),
    'token moved to latest owner'
);
-- a no longer owns the token, so the notification for a has no device.
select tests.act_as_service();
select tests.assert_equals(
    (select kind::text || '|' || actor_name || '|' || cardinality(tokens)::text
     from public.push_payload((select id from public.notifications where user_id = (select a from people)))),
    'NEW_MESSAGE|Kişi niki|0',
    'push payload'
);
select public.delete_device_tokens(array['token-aaaaaaaaaaaaaaaaaaaaaaaaaaaa']);
select tests.reset_role();
select tests.assert_equals((select count(*) from public.device_tokens), 0::bigint, 'invalid tokens removed');
rollback;

-- 6. An announcement goes to phones once, only to approved students it is meant for.
begin;
select tests.act_as((select a from people));
select public.register_device_token('token-announce-aaaaaaaaaaaaaaaaaaaa');
select tests.expect_error(format($$select * from public.claim_announcement_push(%L)$$, gen_random_uuid()),
    'permission denied for function claim_announcement_push');
select tests.reset_role();
select tests.act_as((select b from people));
select public.register_device_token('token-announce-bbbbbbbbbbbbbbbbbbbb');
select tests.reset_role();
update public.profiles set account_status = 'SUSPENDED' where id = (select b from people);
insert into public.announcements (title, body, university_id, ends_at)
values ('Herkese', 'Genel duyuru', null, now() + interval '1 day'),
       ('ODTÜ', 'Yalnızca ODTÜ', (select id from public.universities where name = 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ'), now() + interval '1 day'),
       ('Bitti', 'Süresi geçti', null, now() + interval '1 second');
update public.announcements set starts_at = now() - interval '2 days', ends_at = now() - interval '1 day' where title = 'Bitti';
select tests.act_as_service();
select tests.assert_equals(
    (select title || '|' || array_to_string(tokens, ',') from public.claim_announcement_push((select id from public.announcements where title = 'Herkese'))),
    'Herkese|token-announce-aaaaaaaaaaaaaaaaaaaa',
    'approved students only'
);
select tests.expect_error(format($$select * from public.claim_announcement_push(%L)$$, (select id from public.announcements where title = 'Herkese')),
    'announcement_already_pushed');
select tests.assert_equals(
    (select cardinality(tokens) from public.claim_announcement_push((select id from public.announcements where title = 'ODTÜ'))),
    0, 'other university gets nothing'
);
select tests.expect_error(format($$select * from public.claim_announcement_push(%L)$$, (select id from public.announcements where title = 'Bitti')),
    'announcement_not_found');
select public.record_announcement_push((select id from public.announcements where title = 'Herkese'), 1);
select tests.reset_role();
select tests.act_as_admin((select a from people));
select tests.assert_equals(
    (select (pushed_at is not null)::text || '|' || push_sent from public.admin_list_announcements() where title = 'Herkese'),
    'true|1', 'admin sees the push'
);
select tests.reset_role();
rollback;
