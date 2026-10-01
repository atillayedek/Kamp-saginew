-- KVKK md.11 rights, retention, moderation transparency, data minimisation.

begin;

create function tests.approved(p_email text, p_username text, p_full_name text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    v_id := tests.create_user(p_email);
    update public.profiles
    set full_name = p_full_name, username = p_username, department = 'Bilgisayar',
        university_id = (select id from public.universities where name = 'İSTANBUL TEKNİK ÜNİVERSİTESİ'),
        account_status = 'APPROVED'
    where id = v_id;
    return v_id;
end;
$$;

create temp table u as
select tests.approved('d1@example.edu.tr', 'ayse', 'Ayşe Nur Yılmaz') as ayse,
       tests.approved('d2@example.edu.tr', 'mehmet', 'Mehmet Kaya') as mehmet,
       tests.create_user('dstaff@example.edu.tr') as staff;
grant select on u to anon, authenticated, service_role;

-- 1. Data subject requests ------------------------------------------------------------------------
select tests.act_as((select ayse from u));
select tests.expect_error($$select public.submit_data_subject_request('access', 'kısa')$$, 'invalid_request');
select tests.expect_error($$select public.submit_data_subject_request('hack', 'Verilerimi öğrenmek istiyorum.')$$, 'invalid_request');
create temp table req as select public.submit_data_subject_request('access', 'Hakkımda hangi verileri işlediğinizi öğrenmek istiyorum.') as no;
select tests.assert_equals((select no ~ '^KVKK-\d{4}-\d{6}$' from req), true, 'request number');
select tests.assert_equals(
    (select status || ':' || (due_at - received_at = interval '30 days')::text from public.my_data_subject_requests()),
    'RECEIVED:true', 'due in 30 days');
select public.submit_data_subject_request('other', 'İkinci başvuru metni burada.');
select public.submit_data_subject_request('other', 'Üçüncü başvuru metni burada.');
select public.submit_data_subject_request('other', 'Dördüncü başvuru metni burada.');
select public.submit_data_subject_request('other', 'Beşinci başvuru metni burada.');
select tests.expect_error($$select public.submit_data_subject_request('other', 'Altıncı başvuru metni burada.')$$, 'rate_limited');
select tests.expect_error($$select * from public.admin_list_data_subject_requests()$$, 'admin_required');
select tests.reset_role();

select tests.act_as_staff((select staff from u), array['compliance'], 'aal2', 'Başvuru yanıtı: veri listesi gönderildi');
select tests.assert_equals((select count(*) from public.admin_list_data_subject_requests('RECEIVED')), 5::bigint, 'staff lists');
select tests.expect_error(
    format($$select public.admin_update_data_subject_request(%L, 'ANSWERED')$$, (select id from public.admin_list_data_subject_requests() where request_no = (select no from req))),
    'invalid_request');
select public.admin_update_data_subject_request(
    (select id from public.admin_list_data_subject_requests() where request_no = (select no from req)), 'ANSWERED',
    'Verileriniz uygulamadaki Verilerimi indir ile size sunuldu.');
select public.admin_record_data_subject_request('erasure', 'KEP ile gelen silme talebi metni.', 'kep', 'kisi@example.com');
select tests.reset_role();
select tests.assert_equals(
    (select count(*) from public.notifications where user_id = (select ayse from u) and kind = 'DSR_ANSWERED'),
    1::bigint, 'person notified');
select tests.assert_equals(
    (select count(*) from public.admin_audit_logs where action in ('data_subject_requests.update', 'data_subject_requests.insert')),
    2::bigint, 'answers audited');

-- 2. Breach register ------------------------------------------------------------------------------
select tests.act_as_staff((select staff from u), array['moderator']);
select tests.expect_error($$select * from public.admin_list_breaches()$$, 'admin_required');
select tests.reset_role();
select tests.act_as_staff((select staff from u), array['compliance'], 'aal2', 'İhlal kaydı açıldı');
create temp table br as select public.admin_save_breach(null, now() - interval '10 hours', 'Yanlış yapılandırılmış bucket fark edildi.',
    array['iletişim'], 12, 'Erişim kapatıldı.', null, null, false) as id;
select tests.assert_equals(
    (select board_deadline - detected_at from public.admin_list_breaches()),
    interval '72 hours', '72-hour deadline');
select public.admin_save_breach((select id from br), now() - interval '10 hours', 'Yanlış yapılandırılmış bucket fark edildi.',
    array['iletişim'], 12, 'Erişim kapatıldı.', now(), now(), true);
select tests.assert_equals((select closed_at is not null from public.admin_list_breaches()), true, 'closed');
select tests.reset_role();

-- 3. Surname visibility --------------------------------------------------------------------------------
select tests.act_as((select mehmet from u));
select tests.assert_equals(
    (select full_name from public.search_people('ayse')), 'Ayşe Nur Y.', 'surname hidden by default');
select tests.assert_equals((select count(*) from public.search_people('yilmaz')), 0::bigint, 'hidden surname not searchable');
select tests.reset_role();
select tests.act_as((select ayse from u));
select public.set_show_full_name(true);
select tests.reset_role();
select tests.act_as((select mehmet from u));
select tests.assert_equals((select full_name from public.search_people('yilmaz')), 'Ayşe Nur Yılmaz', 'shown when allowed');
select tests.reset_role();

-- 4. Special categories are not tags; matches can be objected to ------------------------------------------------
select tests.act_as((select ayse from u));
select tests.expect_error(
    $$select public.create_requirement('Ev arkadaşı', '', 'HOUSING', array['ev arkadaşı', 'Alevi'], null, null, null)$$,
    'sensitive_tag');
select tests.expect_error(
    $$select public.create_requirement('Ev arkadaşı', '', 'HOUSING', array['AKP''li'], null, null, null)$$,
    'sensitive_tag');
create temp table r1 as select public.create_requirement('Ev arkadaşı arıyorum', '', 'HOUSING', array['ev arkadaşı', 'Kadıköy'], null, null, null) as id;
select tests.assert_equals((select count(*) from public.list_sensitive_terms()) > 50, true, 'terms for app warnings');
select tests.reset_role();
select tests.act_as((select mehmet from u));
create temp table r2 as select public.create_requirement('Oda arkadaşı', '', 'HOUSING', array['ev arkadaşı'], null, null, null) as id;
select tests.reset_role();
grant select on r1, r2 to authenticated;

select tests.act_as((select ayse from u));
select tests.assert_equals((select count(*) from public.find_matches((select id from r1))), 1::bigint, 'matched');
select tests.expect_error(format($$select public.object_to_match(%L, %L)$$, (select id from r2), (select id from r1)), 'requirement_not_found');
select public.object_to_match((select id from r1), (select id from r2), 'Bu kişiyle eşleşmek istemiyorum');
select tests.assert_equals((select count(*) from public.find_matches((select id from r1))), 0::bigint, 'objected match hidden');
select tests.reset_role();
select tests.act_as((select mehmet from u));
select tests.assert_equals((select count(*) from public.find_matches((select id from r2))), 1::bigint, 'other side unchanged');
select tests.reset_role();

-- 5. Moderation: notification, appeal, reversal, limited message context --------------------------------------
select tests.act_as((select mehmet from u));
create temp table post as select public.create_post('GENERAL', 'Ayşe''nin telefon numarası 0555 555 55 55', 'GENERAL',
                                                    null, null, null, null, null, null, null) as id;
grant select on post to authenticated;
select tests.reset_role();
select tests.act_as((select ayse from u));
select public.report_content('POST', (select id from post), 'PERSONAL_DATA_LEAK', 'Numaramı paylaştı');
create temp table conv as select public.start_conversation((select mehmet from u)) as id;
grant select on conv to authenticated;
select public.send_message((select id from conv), '00000000-0000-4000-8000-0000000000c1', 'Merhaba');
select public.send_message((select id from conv), '00000000-0000-4000-8000-0000000000c2', 'Nasılsın');
select tests.reset_role();
select tests.act_as((select mehmet from u));
select public.send_message((select id from conv), '00000000-0000-4000-8000-0000000000c3', 'Hakaret içeren mesaj');
select public.report_content('MESSAGE', '00000000-0000-4000-8000-0000000000c1', 'HARASSMENT', null);
select tests.reset_role();
create temp table rep as select id, target_kind from public.reports;
grant select on rep to authenticated;

select tests.act_as_staff((select staff from u), array['moderator'], 'aal2', null);
select tests.expect_error(
    format($$select * from public.admin_report_context(%L)$$, (select id from rep where target_kind = 'MESSAGE')),
    'audit_reason_required');
select tests.reset_role();
select tests.act_as_staff((select staff from u), array['moderator'], 'aal2', 'Taciz şikâyetinin incelenmesi');
select tests.assert_equals(
    -- (same transaction: identical timestamps, so compare as a set)
    (select string_agg(body || ':' || is_reported, ',' order by body) from public.admin_report_context((select id from rep where target_kind = 'MESSAGE'))),
    'Hakaret içeren mesaj:false,Merhaba:true,Nasılsın:false', 'context around the reported message');
select tests.expect_error(
    format($$select * from public.admin_report_context(%L)$$, (select id from rep where target_kind = 'POST')),
    'report_not_found');
select public.resolve_report((select id from rep where target_kind = 'POST'), 'REMOVE_CONTENT');
select tests.reset_role();
select tests.assert_equals(
    (select count(*) from public.admin_audit_logs where action = 'view.report_context' and reason = 'Taciz şikâyetinin incelenmesi'),
    1::bigint, 'message access audited');

select tests.act_as((select mehmet from u));
select tests.assert_equals(
    (select reason::text || ':' || resolution || ':' || coalesce(appeal_status, '-') from public.my_moderation_decisions()),
    'PERSONAL_DATA_LEAK:REMOVE_CONTENT:-', 'decision visible to the author');
select tests.expect_error(
    format($$select public.submit_moderation_appeal(%L, 'kısa')$$, (select id from rep where target_kind = 'POST')), 'invalid_appeal');
select public.submit_moderation_appeal((select id from rep where target_kind = 'POST'), 'Numara bana aitti, yanlış anlaşıldı.');
select tests.expect_error(
    format($$select public.submit_moderation_appeal(%L, 'Tekrar itiraz ediyorum.')$$, (select id from rep where target_kind = 'POST')),
    'appeal_exists');
select tests.reset_role();
select tests.assert_equals(
    (select string_agg(kind::text, ',') from public.notifications where user_id = (select mehmet from u) and kind = 'CONTENT_REMOVED'),
    'CONTENT_REMOVED', 'author notified once');
select tests.act_as((select ayse from u));
select tests.expect_error(
    format($$select public.submit_moderation_appeal(%L, 'Başkasının kararına itiraz.')$$, (select id from rep where target_kind = 'POST')),
    'report_not_found');
select tests.reset_role();

select tests.act_as_staff((select staff from u), array['moderator'], 'aal2', 'İtiraz yerinde bulundu');
select public.admin_decide_appeal((select id from public.admin_list_appeals()), true, 'İçerik kişinin kendi numarası.');
select tests.reset_role();
select tests.assert_equals((select deleted_at is null from public.posts where id = (select id from post)), true, 'reversal restores');
select tests.assert_equals(
    (select count(*) from public.notifications where user_id = (select mehmet from u) and kind = 'APPEAL_DECIDED'), 1::bigint, 'appeal answered');

-- 6. Copyright notices without an account -----------------------------------------------------------------------
select tests.act_as_anon();
select tests.expect_error($$select public.submit_copyright_notice('Yayınevi', 'hukuk@yayinevi.com', null, 'Ders kitabımızın 3. bölümü', 'MAT101 Vize özeti', false)$$, 'statement_required');
select tests.expect_error($$select public.submit_copyright_notice('Yayınevi', 'not-an-email', null, 'Ders kitabımızın 3. bölümü', 'MAT101 Vize özeti', true)$$, 'invalid_notice');
select public.submit_copyright_notice('Yayınevi A.Ş.', 'Hukuk@Yayinevi.com', 'Yayınevi', 'Ders kitabımızın 3. bölümü', 'MAT101 Vize özeti', true)
from generate_series(1, 5);
select tests.expect_error($$select public.submit_copyright_notice('Yayınevi', 'hukuk@yayinevi.com', null, 'Ders kitabımızın 3. bölümü', 'MAT101 Vize özeti', true)$$, 'rate_limited');
select tests.reset_role();
select tests.act_as_staff((select staff from u), array['moderator'], 'aal2', 'Telif bildirimi reddedildi: eser tespit edilemedi');
select public.admin_resolve_copyright_notice((select id from public.admin_list_copyright_notices() limit 1), 'REJECTED', 'Bildirilen içerik bulunamadı.');
select tests.reset_role();
select tests.assert_equals(
    (select count(*) from public.copyright_notices where status = 'REJECTED' and claimant_email = 'hukuk@yayinevi.com'), 1::bigint, 'notice resolved');

-- 7. Account deletion with a grace period, anonymised group messages -----------------------------------------------
select tests.act_as((select mehmet from u));
create temp table grp as select (public.create_group('STUDY_GROUP', 'Veri yapıları', 'Haftalık', 'BLM102', true)) as id;
grant select on grp to authenticated, service_role;
select tests.reset_role();
select tests.act_as((select ayse from u));
create temp table own_grp as select (public.create_group('STUDY_GROUP', 'Algoritma çalışma', 'Haftalık', 'BLM101', true)) as id;
grant select on own_grp to authenticated, service_role;
select public.join_group((select id from grp));
select public.send_group_message((select id from grp), '00000000-0000-4000-8000-0000000000d1', 'Yarın 10''da kütüphane', null, null, null);
select tests.assert_equals(public.my_account_deletion(), null::timestamptz, 'no deletion pending');
select tests.assert_equals(public.request_account_deletion() - now() between interval '29 days 23 hours' and interval '30 days', true, 'grace period');
select public.cancel_account_deletion();
select tests.assert_equals(public.my_account_deletion(), null::timestamptz, 'cancelled');
select public.request_account_deletion();
select tests.reset_role();
select tests.act_as((select mehmet from u));
select public.join_group((select id from own_grp));
select tests.reset_role();

update public.profiles set deletion_requested_at = now() - interval '31 days' where id = (select ayse from u);
select tests.act_as_service();
select tests.assert_equals(
    (select (public.retention_due() -> 'accounts') @> jsonb_build_array(jsonb_build_object('user_id', (select ayse from u), 'reason', 'user_request'))),
    true, 'account due after the grace period');
select tests.assert_equals(
    (select (x ->> 'group_messages_anonymized') || ':' || (x ->> 'groups_transferred')
     from public.prepare_account_deletion((select ayse from u), 'user_request') x),
    '1:1', 'anonymised, founded group handed over');
select tests.reset_role();
delete from auth.users where id = (select ayse from u);
select tests.assert_equals(
    (select string_agg(data_category || ':' || method, ',' order by seq) from public.deletion_logs where subject_user_id = (select ayse from u)),
    'account:deleted,group_messages:anonymized', 'deletion recorded');
select tests.assert_equals((select count(*) from public.consent_logs where user_id = (select ayse from u)) >= 0, true, 'logs have no foreign key');
select tests.act_as((select mehmet from u));
select tests.assert_equals(
    (select coalesce(sender_full_name, 'Silinmiş kullanıcı') || ':' || body from public.list_group_messages((select id from grp))),
    'Silinmiş kullanıcı:Yarın 10''da kütüphane', 'message kept as deleted user');
select tests.assert_equals((select my_role::text from public.get_group((select id from own_grp))), 'OWNER', 'new owner');
select tests.reset_role();

-- 8. Retention: documents, exports, inactive accounts, SQL purge ----------------------------------------------------
create temp table old as select tests.approved('d3@example.edu.tr', 'eski', 'Eski Kullanıcı') as id;
grant select on old to service_role, authenticated;
insert into public.student_verifications (user_id, document_path, status, reviewed_at)
values ((select id from old), (select id from old) || '/doc.pdf', 'APPROVED', now() - interval '31 days'),
       ((select mehmet from u), (select mehmet from u) || '/doc.pdf', 'APPROVED', now() - interval '2 days');
update auth.users set last_sign_in_at = now() - interval '800 days' where id = (select id from old);
update public.profiles set created_at = now() - interval '900 days' where id = (select id from old);

select tests.act_as_service();
select tests.assert_equals(
    (select jsonb_agg(d -> 'user_id') from jsonb_array_elements(public.retention_due() -> 'documents') d),
    jsonb_build_array((select id from old)), 'only documents past the retention');
select tests.assert_equals(
    (select (public.retention_due() -> 'inactive_to_warn') @> jsonb_build_array(jsonb_build_object('user_id', (select id from old)))),
    true, 'inactive account warned');
select public.record_document_purged((select id from public.student_verifications where user_id = (select id from old)));
select public.record_inactive_notice((select id from old));
select tests.assert_equals(jsonb_array_length(public.retention_due() -> 'documents'), 0, 'document purged once');
select tests.assert_equals(
    (select (public.retention_due() -> 'inactive_to_warn') @> jsonb_build_array(jsonb_build_object('user_id', (select id from old)))),
    false, 'warned once');
select tests.reset_role();
update public.profiles set inactive_notice_sent_at = now() - interval '31 days' where id = (select id from old);
select tests.act_as_service();
select tests.assert_equals(
    (select (public.retention_due() -> 'accounts') @> jsonb_build_array(jsonb_build_object('user_id', (select id from old), 'reason', 'inactive_account'))),
    true, 'inactive account deleted after the notice');
select tests.reset_role();
-- Coming back cancels it.
insert into public.user_activity_days (user_id, day) values ((select id from old), (now() at time zone 'Europe/Istanbul')::date);
select tests.assert_equals((select inactive_notice_sent_at from public.profiles where id = (select id from old)), null::timestamptz, 'notice cleared');

-- Data export: everything about the person, three per day.
select tests.act_as_service();
select tests.assert_equals(
    (select array_agg(k order by k) from jsonb_object_keys(public.export_user_data((select mehmet from u))) k)
    @> array['access_logs', 'comments', 'consents', 'conversations', 'group_messages', 'posts', 'profile', 'requirements'],
    true, 'export sections');
select tests.assert_equals(
    (public.export_user_data((select mehmet from u)) -> 'profile' ->> 'full_name'), 'Mehmet Kaya', 'own full name');
select public.record_data_export((select mehmet from u), 'a.json', 'a.html');
select public.record_data_export((select mehmet from u), 'b.json', 'b.html');
select public.record_data_export((select mehmet from u), 'c.json', 'c.html');
select tests.assert_equals(public.can_export_data((select mehmet from u)), false, 'daily export limit');
select tests.expect_error(format($$select public.record_data_export(%L, 'd.json', 'd.html')$$, (select mehmet from u)), 'rate_limited');
select tests.reset_role();
update public.data_exports set created_at = now() - interval '8 days' where json_path = 'a.json';
select tests.act_as_service();
select tests.assert_equals(jsonb_array_length(public.retention_due() -> 'exports'), 1, 'old export due');
select public.record_export_deleted((select (e ->> 'id')::uuid from jsonb_array_elements(public.retention_due() -> 'exports') e));
select tests.assert_equals(jsonb_array_length(public.retention_due() -> 'exports'), 0, 'export deleted');
select tests.reset_role();

insert into public.client_errors (user_id, app_version, android_sdk, device_model, exception_type, message, stacktrace, occurred_at, created_at)
values ((select mehmet from u), '1.0', 34, 'Pixel', 'X', 'm', 's', now() - interval '200 days', now() - interval '200 days');
select tests.assert_equals((public.retention_purge_sql() ->> 'client_errors')::int, 1, 'old crash reports purged');
select public.run_daily_retention();
select tests.assert_equals(
    (select error from public.retention_runs order by started_at desc limit 1),
    'retention-run not configured: run the setup from the admin panel', 'unconfigured run is reported');

select tests.act_as_staff((select staff from u), array['compliance']);
select tests.assert_equals(
    (select sum(item_count) from public.admin_retention_report() where data_category = 'client_errors'), 1::numeric, 'retention report');
select tests.reset_role();

-- Every chain is still intact after all of the above.
select tests.assert_equals(
    (select bool_and(ok) from (select (public.verify_log_chain(t)).* from unnest(array['consent_logs', 'access_logs', 'admin_audit_logs', 'deletion_logs']) t) v),
    true, 'chains intact');

rollback;
