-- Student verification: storage rules, submission (service role) and admin review.

-- Shared setup helper: a user with a completed profile (DOCUMENT_REQUIRED).
create function tests.document_required_user(p_email text, p_username text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    v_id := tests.create_user(p_email);
    update public.profiles
    set full_name = 'Test Kişi', username = p_username, department = 'Hukuk',
        university_id = (select id from public.universities order by name limit 1),
        account_status = 'DOCUMENT_REQUIRED'
    where id = v_id;
    return v_id;
end;
$$;
grant execute on function tests.document_required_user(text, text) to authenticated, service_role;

-- Uploads an object the way the storage API does for the given user.
create function tests.upload(p_user uuid, p_name text) returns void
language plpgsql as $$
begin
    insert into storage.objects (bucket_id, name, owner) values ('student-documents', p_name, p_user);
end;
$$;
grant execute on function tests.upload(uuid, text) to authenticated, service_role;

-- 1. Bucket is private and PDF-only with a 10 MB limit.
begin;
select tests.assert_equals(
    (select public::text || '|' || file_size_limit::text || '|' || array_to_string(allowed_mime_types, ',')
     from storage.buckets where id = 'student-documents'),
    'false|10485760|application/pdf',
    'bucket settings'
);
rollback;

-- 2. Storage: a person may upload only into their own folder, only while allowed.
begin;
create temp table ids as
select tests.document_required_user('s1@example.edu.tr', 'sbir') as a,
       tests.document_required_user('s2@example.edu.tr', 'siki') as b,
       tests.create_user('s3@example.edu.tr') as incomplete;
grant select on ids to authenticated;

select tests.act_as((select a from ids));
select tests.upload((select a from ids), (select a from ids)::text || '/' || gen_random_uuid()::text || '.pdf');
select tests.expect_error(
    format($$select tests.upload(%L, %L)$$, (select a from ids), (select b from ids)::text || '/' || gen_random_uuid()::text || '.pdf'),
    'new row violates row-level security policy for table "objects"'
);
select tests.expect_error(
    format($$select tests.upload(%L, %L)$$, (select a from ids), (select a from ids)::text || '/belge.exe'),
    'new row violates row-level security policy for table "objects"'
);
-- A person cannot read someone else's document.
select tests.reset_role();
select tests.upload((select b from ids), (select b from ids)::text || '/' || gen_random_uuid()::text || '.pdf');
select tests.act_as((select a from ids));
select tests.assert_equals((select count(*) from storage.objects), 1::bigint, 'only own documents visible');
-- Nor delete their own evidence.
delete from storage.objects;
select tests.reset_role();
select tests.assert_equals((select count(*) from storage.objects), 2::bigint, 'no deletes');

-- Profile not completed yet: no upload.
select tests.act_as((select incomplete from ids));
select tests.expect_error(
    format($$select tests.upload(%L, %L)$$, (select incomplete from ids), (select incomplete from ids)::text || '/' || gen_random_uuid()::text || '.pdf'),
    'new row violates row-level security policy for table "objects"'
);
rollback;

-- 3. Only the service role can submit, and it validates path, object and status.
begin;
create temp table ids as select tests.document_required_user('s4@example.edu.tr', 'sdort') as a;
create temp table paths as select (select a from ids)::text || '/' || gen_random_uuid()::text || '.pdf' as p;
grant select on ids, paths to authenticated, service_role;
select tests.upload((select a from ids), (select p from paths));

select tests.act_as((select a from ids));
select tests.expect_error(
    format($$select public.submit_student_document(%L, %L)$$, (select a from ids), (select p from paths)),
    'permission denied for function submit_student_document'
);
select tests.reset_role();

select tests.act_as_service();
select tests.expect_error(
    format($$select public.submit_student_document(%L, %L)$$, (select a from ids), 'baskasi/' || gen_random_uuid()::text || '.pdf'),
    'invalid_document_path'
);
select tests.expect_error(
    format($$select public.submit_student_document(%L, %L)$$, (select a from ids), (select a from ids)::text || '/' || gen_random_uuid()::text || '.pdf'),
    'document_not_found'
);
select public.submit_student_document((select a from ids), (select p from paths));
select tests.reset_role();
select tests.assert_equals(
    (select account_status::text from public.profiles where id = (select a from ids)),
    'PENDING_REVIEW',
    'profile moves to review'
);
-- A second submission while one is pending is refused.
select tests.act_as_service();
select tests.expect_error(
    format($$select public.submit_student_document(%L, %L)$$, (select a from ids), (select p from paths)),
    'verification_not_allowed'
);
select tests.reset_role();
-- The person sees their own request; profile is now locked.
select tests.act_as((select a from ids));
select tests.assert_equals((select status::text from public.student_verifications), 'PENDING', 'own request visible');
select tests.expect_error(
    $$select public.complete_profile('Yeni Ad', 'sdort', (select id from public.universities order by name limit 1), 'Hukuk')$$,
    'profile_locked'
);
-- And no upload while pending.
select tests.expect_error(
    format($$select tests.upload(%L, %L)$$, (select a from ids), (select a from ids)::text || '/' || gen_random_uuid()::text || '.pdf'),
    'new row violates row-level security policy for table "objects"'
);
rollback;

-- 4. Admin review: only admins, reasons required for rejection, re-submission after rejection.
begin;
create temp table ids as
select tests.document_required_user('s5@example.edu.tr', 'sbes') as a,
       tests.create_user('admin@example.edu.tr') as admin;
create temp table paths as
select (select a from ids)::text || '/' || gen_random_uuid()::text || '.pdf' as first,
       (select a from ids)::text || '/' || gen_random_uuid()::text || '.pdf' as second;
grant select on ids, paths to authenticated, service_role;
select tests.upload((select a from ids), (select first from paths));
create temp table v as select public.submit_student_document((select a from ids), (select first from paths)) as id;
grant select on v to authenticated;

-- The applicant cannot review, list or approve themselves.
select tests.act_as((select a from ids));
select tests.expect_error($$select * from public.list_pending_verifications()$$, 'admin_required');
select tests.expect_error(format($$select public.review_student_verification(%L, true)$$, (select id from v)), 'admin_required');
update public.student_verifications set status = 'APPROVED';
select tests.reset_role();
select tests.assert_equals((select status::text from public.student_verifications), 'PENDING', 'no direct update');

-- Admin lists and sees the document.
select tests.act_as_admin((select admin from ids));
select tests.assert_equals((select count(*) from public.list_pending_verifications()), 1::bigint, 'pending listed');
select tests.assert_equals(
    (select full_name || '|' || email from public.list_pending_verifications()),
    'Test Kişi|s5@example.edu.tr',
    'listing details'
);
select tests.assert_equals((select count(*) from storage.objects where bucket_id = 'student-documents'), 1::bigint, 'admin reads documents');
select tests.expect_error(format($$select public.review_student_verification(%L, false, ' ')$$, (select id from v)), 'rejection_reason_required');
select public.review_student_verification((select id from v), false, 'Belge okunamıyor');
select tests.expect_error(format($$select public.review_student_verification(%L, true)$$, (select id from v)), 'verification_not_pending');
select tests.reset_role();
select tests.assert_equals(
    (select p.account_status::text || '|' || v2.rejection_reason || '|' || (v2.reviewed_by = (select admin from ids))::text
     from public.profiles p join public.student_verifications v2 on v2.user_id = p.id where p.id = (select a from ids)),
    'REJECTED|Belge okunamıyor|true',
    'rejected with reason'
);

-- After rejection the person may fix the profile, upload again and get approved.
select tests.act_as((select a from ids));
select public.complete_profile('Test Kişi Düzeltilmiş', 'sbes', (select id from public.universities order by name limit 1), 'Hukuk');
select tests.upload((select a from ids), (select second from paths));
select tests.reset_role();
select tests.act_as_service();
create temp table v2 as select public.submit_student_document((select a from ids), (select second from paths)) as id;
select tests.reset_role();
grant select on v2 to authenticated;
select tests.act_as_admin((select admin from ids));
select public.review_student_verification((select id from v2), true);
select tests.reset_role();
select tests.assert_equals(
    (select account_status::text from public.profiles where id = (select a from ids)),
    'APPROVED',
    'approved after re-submission'
);
rollback;

-- 5. Anonymous callers get nothing.
begin;
select tests.act_as_anon();
select tests.expect_error($$select * from public.list_pending_verifications()$$, 'permission denied for function list_pending_verifications');
select tests.assert_equals((select count(*) from public.student_verifications), 0::bigint, 'anon sees no requests');
rollback;
