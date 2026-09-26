-- Moderation: blocking hides people from each other everywhere; reports reach admins only.

create function tests.mod_student(p_email text, p_username text)
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

create function tests.mod_req(p_owner uuid, p_title text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    insert into public.requirements (owner_id, university_id, original_text, title, description, category, embedding, embedding_model)
    values (p_owner, (select university_id from public.profiles where id = p_owner), 'on karakterden uzun metin', p_title,
            'açıklama', 'SPORTS', ('[1' || repeat(',0', 1535) || ']')::extensions.vector, 'test-model')
    returning id into v_id;
    return v_id;
end;
$$;

create temp table people as
select tests.mod_student('b1@example.edu.tr', 'bbir') as a,
       tests.mod_student('b2@example.edu.tr', 'biki') as b,
       tests.mod_student('b3@example.edu.tr', 'buc') as c,
       tests.create_user('badmin@example.edu.tr') as admin;
grant select on people to authenticated, anon;

-- 1. Blocking hides posts, comments, matches, conversations and notifications both ways.
begin;
select tests.act_as((select b from people));
create temp table ids as
select public.create_post('GENERAL', 'B gönderisi') as b_post,
       tests.mod_req((select b from people), 'B ilanı') as b_req,
       tests.mod_req((select a from people), 'A ilanı') as a_req;
select tests.reset_role();
select tests.act_as((select c from people));
create temp table c_ids as select public.create_post('GENERAL', 'C gönderisi') as c_post;
select tests.reset_role();
grant select on ids, c_ids to authenticated;

-- A talks to B and comments on C's post before blocking.
select tests.act_as((select a from people));
create temp table conv as select public.start_conversation((select b from people)) as id;
grant select on conv to authenticated;
select public.create_post('GENERAL', 'A gönderisi');
select tests.reset_role();
select tests.act_as((select b from people));
select public.send_message((select id from conv), gen_random_uuid(), 'Selam A');
select public.add_comment((select c_post from c_ids), 'B yorumu');
select tests.reset_role();

select tests.act_as((select a from people));
select tests.assert_equals((select count(*) from public.list_notifications()), 1::bigint, 'message notification before block');
select public.block_user((select b from people));
select public.block_user((select b from people));
select tests.assert_equals(
    (select string_agg(username, ',') from public.list_blocked_users()), 'biki', 'blocked list, idempotent'
);
select tests.assert_equals(
    (select count(*) from public.list_posts('GENERAL') where author_username = 'biki'), 0::bigint, 'feed hides blocked author'
);
select tests.assert_equals((select count(*) from public.list_posts('GENERAL')), 2::bigint, 'other posts still shown');
select tests.expect_error(format($$select * from public.get_post(%L)$$, (select b_post from ids limit 1)), 'post_not_found');
select tests.expect_error(format($$select public.add_comment(%L, 'x')$$, (select b_post from ids limit 1)), 'post_not_found');
select tests.expect_error(format($$select public.set_post_like(%L, true)$$, (select b_post from ids limit 1)), 'post_not_found');
select tests.assert_equals(
    (select count(*) from public.list_comments((select c_post from c_ids))), 0::bigint, 'comments by blocked user hidden'
);
select tests.assert_equals((select count(*) from public.comments), 0::bigint, 'RLS hides blocked comments too');
select tests.assert_equals(
    (select count(*) from public.find_matches((select a_req from ids limit 1))), 0::bigint, 'matches hide blocked owner'
);
select tests.assert_equals((select count(*) from public.list_conversations()), 0::bigint, 'conversation hidden');
select tests.assert_equals((select count(*) from public.list_notifications()), 0::bigint, 'notification hidden');
select tests.expect_error(format($$select public.start_conversation(%L)$$, (select b from people)), 'recipient_not_available');
select tests.expect_error(format($$select public.send_message(%L, gen_random_uuid(), 'x')$$, (select id from conv)), 'recipient_not_available');
select tests.reset_role();

-- The blocked side is affected the same way, but cannot see or undo the block.
select tests.act_as((select b from people));
select tests.assert_equals((select count(*) from public.list_blocked_users()), 0::bigint, 'blocked person sees no list');
select tests.assert_equals((select count(*) from public.user_blocks), 0::bigint, 'blocked person cannot read the block');
select public.unblock_user((select a from people));
select tests.assert_equals((select count(*) from public.list_conversations()), 0::bigint, 'hidden for the blocked side');
select tests.expect_error(format($$select public.send_message(%L, gen_random_uuid(), 'x')$$, (select id from conv)), 'recipient_not_available');
select tests.expect_error(format($$select public.start_conversation(%L)$$, (select a from people)), 'recipient_not_available');
select tests.assert_equals(
    (select count(*) from public.list_posts('GENERAL') where author_username = 'bbir'), 0::bigint, 'blocked side feed'
);
select tests.expect_error(
    $$insert into public.user_blocks (blocker_id, blocked_id) select b, a from people$$,
    'new row violates row-level security policy for table "user_blocks"'
);
select tests.reset_role();

-- Unblocking restores everything.
select tests.act_as((select a from people));
select tests.expect_error(format($$select public.block_user(%L)$$, (select a from people)), 'user_not_found');
select public.unblock_user((select b from people));
select tests.assert_equals((select count(*) from public.list_posts('GENERAL')), 3::bigint, 'unblocked feed');
select tests.assert_equals((select count(*) from public.list_conversations()), 1::bigint, 'conversation back');
select public.send_message((select id from conv), gen_random_uuid(), 'Tekrar selam');
rollback;

-- 2. Reports: only visible content, no self reports, idempotent while open, rate limited.
begin;
select tests.act_as((select b from people));
create temp table ids as select public.create_post('GENERAL', 'Spam gönderi') as post;
grant select on ids to authenticated;
select tests.reset_role();

select tests.act_as((select a from people));
select public.report_content('POST', (select post from ids), 'SPAM', '  reklam  ');
select public.report_content('POST', (select post from ids), 'SPAM', null);
select public.report_content('USER', (select b from people), 'FAKE_PROFILE');
select tests.expect_error(format($$select public.report_content('POST', %L, 'SPAM')$$, gen_random_uuid()), 'report_target_not_found');
select tests.expect_error(format($$select public.report_content('USER', %L, 'SPAM')$$, (select a from people)), 'report_target_not_found');
select tests.expect_error(format($$select public.report_content('POST', %L, 'SPAM', %L)$$, (select post from ids), repeat('x', 501)), 'invalid_report');
select tests.expect_error($$select * from public.list_open_reports()$$, 'admin_required');
select tests.assert_equals((select count(*) from public.reports), 0::bigint, 'reports are not readable directly');
select tests.reset_role();
select tests.assert_equals(
    (select string_agg(target_kind::text || ':' || target_excerpt || ':' || coalesce(details, '-'), ',' order by target_kind)
     from public.reports),
    'POST:Spam gönderi:reklam,USER:Kişi biki (@biki):-',
    'stored once with a snapshot'
);

-- A message can be reported only by a member of its conversation.
select tests.act_as((select a from people));
create temp table conv as select public.start_conversation((select b from people)) as id;
grant select on conv to authenticated;
select tests.reset_role();
select tests.act_as((select b from people));
create temp table msg as select gen_random_uuid() as id;
grant select on msg to authenticated;
select public.send_message((select id from conv), (select id from msg), 'kaba mesaj');
select tests.reset_role();
select tests.act_as((select c from people));
select tests.expect_error(format($$select public.report_content('MESSAGE', %L, 'HARASSMENT')$$, (select id from msg)), 'report_target_not_found');
select tests.expect_error(format($$select public.conversation_partner(%L)$$, (select id from conv)), 'conversation_not_found');
select tests.reset_role();
select tests.act_as((select a from people));
select tests.assert_equals(public.conversation_partner((select id from conv)), (select b from people), 'partner of a member');
select tests.reset_role();
select tests.act_as((select a from people));
select public.report_content('MESSAGE', (select id from msg), 'HARASSMENT');
select tests.reset_role();

update public.reports set created_at = now() - interval '1 hour';
insert into public.reports (reporter_id, target_kind, target_id, target_user_id, target_excerpt, reason, status)
select (select a from people), 'USER', gen_random_uuid(), (select b from people), 'x', 'OTHER', 'DISMISSED'
from generate_series(1, 17);
select tests.act_as((select c from people));
create temp table cpost as select public.create_post('GENERAL', 'C gönderisi') as id;
grant select on cpost to authenticated;
select tests.reset_role();
select tests.act_as((select a from people));
select tests.expect_error(format($$select public.report_content('POST', %L, 'OTHER')$$, (select id from cpost)), 'rate_limited');
rollback;

-- 3. Admin actions: remove content, suspend, dismiss; related open reports close together.
begin;
select tests.act_as((select b from people));
create temp table ids as
select public.create_post('GENERAL', 'Kötü gönderi') as bad_post,
       public.create_post('GENERAL', 'Normal gönderi') as ok_post;
grant select on ids to authenticated;
select tests.reset_role();
select tests.act_as((select a from people));
select public.report_content('POST', (select bad_post from ids), 'INAPPROPRIATE');
select public.report_content('POST', (select ok_post from ids), 'OTHER');
select tests.reset_role();
select tests.act_as((select c from people));
select public.report_content('POST', (select bad_post from ids), 'HARASSMENT');
select tests.expect_error(
    format($$select public.resolve_report(%L, 'DISMISS')$$, (select id from public.reports limit 1)), 'admin_required'
);
select tests.reset_role();

select tests.act_as_admin((select admin from people));
select tests.assert_equals(
    (select string_agg(target_excerpt || ':' || report_count || ':' || target_username, ',' order by target_excerpt, reporter_username)
     from public.list_open_reports()),
    'Kötü gönderi:2:biki,Kötü gönderi:2:biki,Normal gönderi:1:biki',
    'open reports with counts'
);
select tests.expect_error(
    format($$select public.resolve_report(%L, 'DELETE_EVERYTHING')$$,
           (select report_id from public.list_open_reports() where target_excerpt = 'Kötü gönderi' limit 1)),
    'invalid_action'
);
select public.resolve_report(
    (select report_id from public.list_open_reports() where target_excerpt = 'Kötü gönderi' limit 1), 'REMOVE_CONTENT'
);
select tests.assert_equals(
    (select string_agg(target_excerpt, ',') from public.list_open_reports()), 'Normal gönderi', 'both reports on the post closed'
);
select public.resolve_report((select report_id from public.list_open_reports()), 'DISMISS');
select tests.assert_equals((select count(*) from public.list_open_reports()), 0::bigint, 'queue empty');
select tests.expect_error(
    format($$select public.resolve_report(%L, 'DISMISS')$$, (select id from public.reports limit 1)), 'report_not_found'
);
select tests.reset_role();
select tests.assert_equals(
    (select string_agg(body || ':' || (deleted_at is not null)::text, ',' order by body) from public.posts),
    'Kötü gönderi:true,Normal gönderi:false',
    'only the reported post removed'
);
select tests.assert_equals(
    (select string_agg(status::text || ':' || resolution || ':' || (resolved_by = (select admin from people))::text, ',' order by status)
     from public.reports),
    'RESOLVED:REMOVE_CONTENT:true,RESOLVED:REMOVE_CONTENT:true,DISMISSED:DISMISS:true',
    'resolution recorded'
);

-- Suspending the author closes every open report about them and locks the account.
select tests.act_as((select a from people));
select public.report_content('USER', (select b from people), 'HARASSMENT');
select public.report_content('POST', (select ok_post from ids), 'SPAM');
select tests.reset_role();
select tests.act_as_admin((select admin from people));
select tests.expect_error(
    format($$select public.resolve_report(%L, 'REMOVE_CONTENT')$$,
           (select report_id from public.list_open_reports() where target_kind = 'USER')),
    'invalid_action'
);
select public.resolve_report((select report_id from public.list_open_reports() where target_kind = 'USER'), 'SUSPEND_USER');
select tests.assert_equals((select count(*) from public.list_open_reports()), 0::bigint, 'all reports about the user closed');
select tests.reset_role();
select tests.assert_equals(
    (select account_status from public.profiles where id = (select b from people)), 'SUSPENDED'::public.account_status, 'suspended'
);
select tests.act_as((select b from people));
select tests.expect_error($$select public.create_post('GENERAL', 'yine ben')$$, 'approved_student_required');
rollback;

-- 4. A reported message can be removed by an admin.
begin;
select tests.act_as((select a from people));
create temp table conv as select public.start_conversation((select b from people)) as id;
grant select on conv to authenticated;
select tests.reset_role();
select tests.act_as((select b from people));
create temp table msg as select gen_random_uuid() as id;
grant select on msg to authenticated;
select public.send_message((select id from conv), (select id from msg), 'kaba mesaj');
select tests.reset_role();
select tests.act_as((select a from people));
select public.report_content('MESSAGE', (select id from msg), 'HARASSMENT');
select tests.reset_role();
select tests.act_as_admin((select admin from people));
select public.resolve_report((select report_id from public.list_open_reports()), 'REMOVE_CONTENT');
select tests.reset_role();
select tests.assert_equals((select count(*) from public.messages), 0::bigint, 'message removed');
select tests.assert_equals((select target_excerpt from public.reports), 'kaba mesaj', 'snapshot kept for the record');
rollback;

-- 5. Anonymous callers can use none of it.
begin;
select tests.act_as_anon();
select tests.expect_error(format($$select public.block_user(%L)$$, (select b from people)), 'permission denied for function block_user');
select tests.expect_error($$select * from public.list_open_reports()$$, 'permission denied for function list_open_reports');
rollback;
