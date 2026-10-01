-- KVKK foundation: settings, staff roles + MFA, audited staff actions, append-only hash-chained
-- logs, versioned legal texts, sign-up consents, consent changes, access logs.

begin;

-- 0. Settings ------------------------------------------------------------------------------------
select tests.act_as_anon();
select tests.assert_equals((public.public_compliance_config() ->> 'document_retention_days')::int, 30, 'public config');
select tests.expect_error($$select public.compliance_value('min_age')$$, 'permission denied for function compliance_value');
select tests.reset_role();

-- 1. Legal documents -------------------------------------------------------------------------------
select tests.act_as_anon();
select tests.assert_equals((select count(*) from public.list_legal_documents()), 13::bigint, 'every text published');
select tests.assert_equals(
    (select content_sha256 = encode(sha256(convert_to(content, 'UTF8')), 'hex') and content like '%TASLAK — AVUKAT ONAYI GEREKLİ%'
     from public.get_legal_document('aydinlatma_metni')),
    true, 'hash and draft banner');
select tests.reset_role();

create temp table staff as
select tests.create_user('super@example.edu.tr') as super_id,
       tests.create_user('verifier@example.edu.tr') as verifier_id,
       tests.create_user('moderator@example.edu.tr') as moderator_id,
       tests.create_user('compliance@example.edu.tr') as compliance_id;
grant select on staff to authenticated, anon, service_role;

-- Only compliance (or superadmin) in an MFA session publishes; the reason is recorded.
select tests.act_as_staff((select moderator_id from staff), array['moderator']);
select tests.expect_error($$select public.admin_publish_legal_document('kullanim_kosullari', 'Koşullar', repeat('x', 60))$$, 'admin_required');
select tests.reset_role();
select tests.act_as_staff((select compliance_id from staff), array['compliance'], 'aal1');
select tests.expect_error($$select public.admin_publish_legal_document('kullanim_kosullari', 'Koşullar', repeat('x', 60))$$, 'admin_required');
select tests.reset_role();
select tests.act_as_staff((select compliance_id from staff), array['compliance'], 'aal2', null);
select tests.expect_error($$select public.admin_publish_legal_document('kullanim_kosullari', 'Koşullar', repeat('x', 60))$$, 'audit_reason_required');
select tests.reset_role();

-- 2. Sign-up with consents ---------------------------------------------------------------------------
create function tests.signup(p_email text, p_kvkk jsonb) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    insert into auth.users (email, raw_user_meta_data) values (p_email, jsonb_build_object('kvkk', p_kvkk)) returning id into v_id;
    return v_id;
end;
$$;
grant execute on function tests.signup(text, jsonb) to anon, authenticated;

create temp table form as
select jsonb_build_object(
    'birth_date', ((now() at time zone 'Europe/Istanbul')::date - interval '20 years')::date,
    'accepted', jsonb_build_object('kullanim_kosullari', 1, 'topluluk_kurallari', 1),
    'informed', jsonb_build_object('aydinlatma_metni', 1, 'gizlilik_politikasi', 1),
    'consents', jsonb_build_object(
        'acik_riza_pazarlama_eposta', jsonb_build_object('version', 1, 'granted', true),
        'acik_riza_pazarlama_bildirim', jsonb_build_object('version', 1, 'granted', false)),
    'app_version', '1.4.0', 'platform', 'android', 'user_agent', 'KampusAgi/1.4.0 (Pixel 8; Android 15)'
) as kvkk;
grant select on form to anon, authenticated;

select tests.expect_error(
    format($$select tests.signup('young@example.edu.tr', %L)$$,
           (select kvkk || jsonb_build_object('birth_date', ((now() at time zone 'Europe/Istanbul')::date - interval '17 years 11 months')::date) from form)),
    'underage');
select tests.expect_error(format($$select tests.signup('nobirth@example.edu.tr', %L)$$, (select kvkk - 'birth_date' from form)), 'birth_date_required');
select tests.expect_error(
    format($$select tests.signup('noterms@example.edu.tr', %L)$$, (select kvkk #- '{accepted,topluluk_kurallari}' from form)),
    'terms_required');
select tests.expect_error(
    format($$select tests.signup('badversion@example.edu.tr', %L)$$, (select jsonb_set(kvkk, '{accepted,kullanim_kosullari}', '99') from form)),
    'legal_document_not_found');

create temp table people as
select tests.signup('consent@example.edu.tr', (select kvkk from form)) as student;
grant select on people to anon, authenticated, service_role;

select tests.assert_equals(
    (select string_agg(document_type || ':' || action || ':' || channel || ':' || document_version, ',' order by document_type)
     from public.consent_logs where user_id = (select student from people)),
    'acik_riza_pazarlama_bildirim:declined:register:1,acik_riza_pazarlama_eposta:accepted:register:1,'
    || 'aydinlatma_metni:informed:register:1,gizlilik_politikasi:informed:register:1,'
    || 'kullanim_kosullari:accepted:register:1,topluluk_kurallari:accepted:register:1',
    'sign-up consents');
select tests.assert_equals(
    (select bool_and(c.content_sha256 = d.content_sha256 and c.app_version = '1.4.0' and c.user_agent like 'KampusAgi/%')
     from public.consent_logs c join public.legal_documents d on d.doc_type = c.document_type and d.version = c.document_version
     where c.user_id = (select student from people)),
    true, 'consent points to the exact text');
select tests.assert_equals(
    (select (raw_user_meta_data -> 'kvkk') ? 'birth_date' from auth.users where id = (select student from people)),
    false, 'birth date not stored');
select tests.assert_equals(
    (select marketing_opt_in::text || marketing_push_opt_in::text from public.profiles where id = (select student from people)),
    'truefalse', 'consent flags');

-- An invite without the form is allowed; texts are asked at first sign-in.
select tests.create_user('invited@example.edu.tr');

-- 3. Pending texts, acknowledgement, consent changes -------------------------------------------------
select tests.act_as((select student from people));
select tests.assert_equals((select count(*) from public.pending_legal_documents()), 0::bigint, 'nothing pending');
select tests.reset_role();

select tests.act_as_staff((select compliance_id from staff), array['compliance'], 'aal2', 'Avukat onaylı yeni sürüm');
select tests.assert_equals(public.admin_publish_legal_document('kullanim_kosullari', 'Kullanım Koşulları', repeat('Yeni koşullar. ', 10)), 2, 'version 2');
select tests.reset_role();
select tests.assert_equals(
    (select string_agg(version || ':' || is_active, ',' order by version) from public.legal_documents where doc_type = 'kullanim_kosullari'),
    '1:false,2:true', 'old version kept, inactive');
select tests.assert_equals(
    (select action || ':' || target_type || ':' || reason || ':' || host(ip) from public.admin_audit_logs
     where action = 'legal_documents.insert'),
    'legal_documents.insert:legal_documents:Avukat onaylı yeni sürüm:203.0.113.7', 'publish audited');
select tests.expect_error($$update public.legal_documents set content = repeat('y', 60) where doc_type = 'kullanim_kosullari' and version = 1$$, 'legal_document_immutable');
select tests.expect_error($$delete from public.legal_documents where doc_type = 'kullanim_kosullari'$$, 'legal_document_immutable');
select tests.act_as_anon();
select tests.assert_equals((select version from public.get_legal_document('kullanim_kosullari', 1)), 1, 'old version readable');
select tests.assert_equals((select count(*) from public.list_legal_document_versions('kullanim_kosullari')), 2::bigint, 'versions listed');
select tests.reset_role();

select tests.act_as((select student from people));
select tests.assert_equals(
    (select string_agg(doc_type || ':' || kind || ':' || version, ',') from public.pending_legal_documents()),
    'kullanim_kosullari:agreement:2', 'new terms pending');
select tests.expect_error($$select public.acknowledge_legal_document('kullanim_kosullari', 1)$$, 'legal_document_not_found');
select tests.expect_error($$select public.acknowledge_legal_document('acik_riza_pazarlama_eposta', 1)$$, 'legal_document_not_found');
select tests.expect_error($$select public.acknowledge_legal_document('mesafeli_sozlesme', 1, 'login')$$, 'invalid_channel');
select public.acknowledge_legal_document('kullanim_kosullari', 2, 'login', '1.5.0', 'android');
select public.acknowledge_legal_document('mesafeli_sozlesme', 1, 'purchase', '1.5.0', 'android');
select tests.assert_equals((select count(*) from public.pending_legal_documents()), 0::bigint, 'accepted');

select public.set_consent('acik_riza_pazarlama_eposta', false);
select public.set_consent('acik_riza_pazarlama_bildirim', true);
select tests.assert_equals(
    (select string_agg(doc_type || ':' || granted, ',' order by doc_type) from public.my_consents()),
    'acik_riza_pazarlama_bildirim:true,acik_riza_pazarlama_eposta:false', 'my consents');
select tests.assert_equals(
    (select string_agg(action, ',' order by created_at) from public.my_consent_history() where document_type = 'acik_riza_pazarlama_eposta'),
    'accepted,withdrawn', 'withdrawal recorded');
select tests.expect_error($$select public.set_consent('kullanim_kosullari', false)$$, 'legal_document_not_found');
-- Older apps: the marketing switch is the e-mail consent.
select public.set_marketing_consent(true);
select tests.reset_role();
select tests.assert_equals(
    (select marketing_opt_in::text || marketing_push_opt_in::text from public.profiles where id = (select student from people)),
    'truetrue', 'flags follow consents');
select tests.assert_equals(
    (select host(ip) || '|' || user_agent || '|' || channel from public.consent_logs
     where user_id = (select student from people) and document_type = 'kullanim_kosullari' and document_version = 2),
    '198.51.100.20|KampusAgi/test|login', 'ip and agent from the gateway');

select tests.act_as_service();
select public.unsubscribe_marketing((select student from people));
select tests.reset_role();
select tests.assert_equals(
    (select action || ':' || channel from public.consent_logs
     where user_id = (select student from people) and document_type = 'acik_riza_pazarlama_eposta' order by seq desc limit 1),
    'withdrawn:email_link', 'unsubscribe link withdraws');

-- 4. Append-only logs ---------------------------------------------------------------------------------
select tests.act_as((select student from people));
select tests.expect_error($$select count(*) from public.consent_logs$$, 'permission denied for table consent_logs');
select tests.expect_error(
    format($$insert into public.consent_logs (user_id, document_type, action, channel) values (%L, 'x', 'accepted', 'login')$$, (select student from people)),
    'permission denied for table consent_logs');
select tests.reset_role();
select tests.act_as_service();
select tests.expect_error($$delete from public.access_logs$$, 'permission denied for table access_logs');
select tests.expect_error($$update public.admin_audit_logs set reason = 'x'$$, 'permission denied for table admin_audit_logs');
select tests.reset_role();
-- Not even the table owner can change or delete a row outside the retention purge.
select tests.expect_error($$update public.consent_logs set action = 'declined'$$, 'log_is_append_only');
select tests.expect_error($$delete from public.consent_logs$$, 'log_is_append_only');
select tests.expect_error($$truncate public.consent_logs$$, 'log_is_append_only');

-- 5. Access logs ----------------------------------------------------------------------------------------
select tests.act_as((select student from people));
select public.log_access_event('login', 'Pixel 8 / Android 15', '1.5.0', 'android');
select tests.expect_error($$select public.log_access_event('hack')$$, 'invalid_event');
select tests.reset_role();
select tests.act_as_anon();
select public.log_failed_login('  Consent@Example.edu.tr ', 'Pixel 8', '1.5.0', 'android');
select tests.reset_role();
select tests.assert_equals(
    (select email_sha256 = encode(sha256(convert_to('consent@example.edu.tr', 'UTF8')), 'hex') and user_id is null
     from public.access_logs where event = 'login_failed'),
    true, 'failed login keeps only a hash');

insert into auth.audit_log_entries (id, payload, created_at, ip_address) values
    ('00000000-0000-0000-0000-0000000000a1', json_build_object('action', 'login', 'actor_id', (select student from people)), now() - interval '2 minutes', '192.0.2.44'),
    ('00000000-0000-0000-0000-0000000000a2', json_build_object('action', 'token_refreshed', 'actor_id', (select student from people)), now() - interval '1 minute', '192.0.2.44'),
    ('00000000-0000-0000-0000-0000000000a3', json_build_object('action', 'user_recovery_requested', 'actor_id', (select student from people)), now(), 'not-an-ip');
select tests.assert_equals(public.sync_auth_access_logs(), 2, 'auth events copied');
select tests.assert_equals(public.sync_auth_access_logs(), 0, 'copied once');
select tests.assert_equals(
    (select string_agg(event || ':' || coalesce(host(ip), '-'), ',' order by occurred_at) from public.access_logs where source = 'auth_server'),
    'login:192.0.2.44,password_recovery:-', 'server-side events');

select tests.act_as((select student from people));
select tests.assert_equals((select count(*) from public.my_access_logs()), 3::bigint, 'own access logs');
select tests.reset_role();

-- 6. Staff roles and audited staff actions -----------------------------------------------------------------
create temp table s as
select tests.create_user('roles@example.edu.tr') as applicant;
grant select on s to authenticated;
update public.profiles set full_name = 'Belge Sahibi', username = 'belgesahibi', account_status = 'PENDING_REVIEW',
    university_id = (select id from public.universities limit 1), department = 'Bölüm'
where id = (select applicant from s);
insert into public.student_verifications (user_id, document_path) values ((select applicant from s), (select applicant from s) || '/doc.pdf');
create temp table ver as select id from public.student_verifications where user_id = (select applicant from s);
grant select on ver to authenticated;

select tests.act_as_staff((select moderator_id from staff), array['moderator']);
select tests.expect_error($$select * from public.list_pending_verifications()$$, 'admin_required');
select tests.expect_error($$select public.admin_list_logs('consent_logs')$$, 'admin_required');
select tests.assert_equals((select count(*) from public.list_open_reports()), 0::bigint, 'moderator reads reports');
select tests.reset_role();

select tests.act_as_staff((select verifier_id from staff), array['verifier']);
select tests.expect_error($$select * from public.list_open_reports()$$, 'admin_required');
select tests.expect_error($$select public.admin_grant_premium(gen_random_uuid(), gen_random_uuid(), 30)$$, 'admin_required');
select tests.assert_equals((select count(*) from public.list_pending_verifications()), 1::bigint, 'verifier lists documents');
select tests.assert_equals((public.admin_overview() ->> 'pending_verifications') is not null, true, 'overview for every staff role');
select tests.reset_role();
select tests.act_as_staff((select verifier_id from staff), array['verifier'], 'aal2', null);
select tests.expect_error(
    format($$select public.review_student_verification(%L, true)$$, (select id from ver)),
    'audit_reason_required');
select tests.reset_role();
select tests.act_as_staff((select verifier_id from staff), array['verifier'], 'aal2', 'Belge geçerli, üniversite eşleşiyor');
select public.review_student_verification((select id from ver), true);
select tests.reset_role();
select tests.assert_equals(
    (select string_agg(action || '|' || reason, ',' order by seq) from public.admin_audit_logs where admin_id = (select verifier_id from staff)),
    'view.list_pending_verifications|Test işlemi,student_verifications.update|Belge geçerli, üniversite eşleşiyor,'
    || 'profiles.update|Belge geçerli, üniversite eşleşiyor',
    'verifier actions audited');
select tests.assert_equals(
    (select details -> 'changed' ->> 'account_status' from public.admin_audit_logs where action = 'profiles.update' and admin_id = (select verifier_id from staff)),
    'APPROVED', 'change recorded');

-- Without MFA nothing works, not even for the superadmin.
select tests.act_as_staff((select super_id from staff), array['superadmin'], 'aal1');
select tests.expect_error($$select public.admin_overview()$$, 'admin_required');
select tests.reset_role();
select tests.act_as((select student from people));
select tests.assert_equals(public.my_staff_access() -> 'roles', '[]'::jsonb, 'student has no staff role');
select tests.reset_role();

-- Compliance: settings, logs, exports, chain verification.
select tests.act_as_staff((select compliance_id from staff), array['compliance'], 'aal2', 'Avukat görüşü: belge 15 gün');
select tests.expect_error($$select public.admin_set_compliance_setting('document_retention_days', '"on beş"')$$, 'invalid_setting_value');
select tests.expect_error($$select public.admin_set_compliance_setting('document_retention_days', '-1')$$, 'invalid_setting_value');
select public.admin_set_compliance_setting('document_retention_days', '15');
select tests.assert_equals(
    (select count(*) from public.admin_list_logs('consent_logs', null, null, (select student from people))),
    12::bigint, 'consent log view');
select public.record_admin_action('export.consent_logs', 'consent_logs', null, '{"rows": 12, "format": "csv"}');
select tests.expect_error($$select public.record_admin_action('drop table', 'x', null)$$, 'invalid_action');
select tests.assert_equals((select bool_and(ok and head_matches) from public.admin_verify_log_chains()), true, 'chains intact');
select tests.reset_role();
select tests.assert_equals(public.compliance_int('document_retention_days'), 15, 'setting changed');
select tests.assert_equals(
    (select string_agg(action, ',' order by seq) from public.admin_audit_logs where admin_id = (select compliance_id from staff)),
    'legal_documents.insert,compliance_settings.update,view.consent_logs,export.consent_logs,verify.log_chains', 'compliance actions audited');

-- 7. Tampering is detected; the retention purge keeps the chain verifiable -----------------------------------
savepoint tamper;
alter table public.consent_logs disable trigger consent_logs_append_only;
update public.consent_logs set action = 'accepted' where action = 'declined';
select tests.assert_equals(
    (select ok::text || ':' || (first_bad_seq is not null)::text from public.verify_log_chain('consent_logs')),
    'false:true', 'tampering detected');
rollback to savepoint tamper;

select tests.assert_equals(public.purge_log('access_logs', now() + interval '1 day') > 0, true, 'purged');
select tests.act_as((select student from people));
select public.log_access_event('logout');
select tests.reset_role();
select tests.assert_equals(
    (select ok::text || ':' || rows_checked from public.verify_log_chain('access_logs')),
    'true:1', 'chain continues after purge');
select tests.assert_equals(
    (select anchor_hash from public.verify_log_chain('access_logs')),
    (select details ->> 'anchor_hash' from public.deletion_logs where data_category = 'access_logs'),
    'anchor recorded in deletion log');
select tests.assert_equals((select bool_and(ok and head_matches) from (select (public.verify_log_chain(t)).* from unnest(array['consent_logs', 'admin_audit_logs', 'deletion_logs']) t) v), true, 'all chains');

-- 8. Staff cannot read student documents directly from storage --------------------------------------------------
insert into storage.objects (bucket_id, name, owner) values ('student-documents', (select applicant from s) || '/doc.pdf', (select applicant from s));
select tests.act_as_admin((select super_id from staff));
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'student-documents'), 0::bigint, 'no direct document read');
select tests.reset_role();

rollback;
