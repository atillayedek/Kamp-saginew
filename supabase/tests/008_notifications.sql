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

-- 5. There is no push delivery (D30): no device tokens or push functions exist.
begin;
select tests.assert_equals(to_regclass('public.device_tokens')::text, null::text, 'device_tokens dropped');
select tests.assert_equals(
    (select count(*) from pg_proc
     where pronamespace = 'public'::regnamespace
       and proname in ('register_device_token', 'unregister_device_token', 'push_payload', 'delete_device_tokens')),
    0::bigint,
    'push functions dropped'
);
rollback;
