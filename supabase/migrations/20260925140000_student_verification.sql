-- Student verification: PDF upload -> backend validation -> admin review.
--
-- Flow
--   1. The app uploads the PDF to storage `student-documents/<user id>/<uuid>.pdf`
--      (storage RLS: own folder only, only while verification is allowed).
--   2. The app calls the `submit-student-document` Edge Function. It checks the
--      file really is a PDF within the size limit, then calls
--      submit_student_document() with the service role.
--   3. An admin (JWT app_metadata.role = 'admin', settable only with the service
--      role) reviews it with review_student_verification().
-- Nothing on the client can approve an account.

create type public.verification_status as enum ('PENDING', 'APPROVED', 'REJECTED');

-- Admins are marked in auth.users.raw_app_meta_data, which users cannot edit.
create function public.is_admin()
returns boolean
language sql
stable
set search_path = ''
as $$
    select coalesce((auth.jwt() -> 'app_metadata' ->> 'role') = 'admin', false)
$$;

create table public.student_verifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    document_path text not null unique,
    status public.verification_status not null default 'PENDING',
    rejection_reason text check (rejection_reason is null or char_length(btrim(rejection_reason)) between 3 and 500),
    reviewed_by uuid references auth.users (id) on delete set null,
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    constraint student_verifications_reason_only_when_rejected
        check ((status = 'REJECTED') = (rejection_reason is not null)),
    constraint student_verifications_review_fields
        check ((status = 'PENDING') = (reviewed_at is null))
);

-- At most one open request per person.
create unique index student_verifications_one_pending_idx
    on public.student_verifications (user_id) where status = 'PENDING';
create index student_verifications_user_created_idx
    on public.student_verifications (user_id, created_at desc);
create index student_verifications_pending_created_idx
    on public.student_verifications (created_at) where status = 'PENDING';
create index student_verifications_reviewed_by_idx
    on public.student_verifications (reviewed_by);

alter table public.student_verifications enable row level security;

create policy student_verifications_select_own on public.student_verifications
    for select to authenticated
    using (user_id = (select auth.uid()));

-- Storage -------------------------------------------------------------------

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('student-documents', 'student-documents', false, 10485760, array['application/pdf'])
on conflict (id) do update
    set public = false,
        file_size_limit = excluded.file_size_limit,
        allowed_mime_types = excluded.allowed_mime_types;

-- Whether the signed-in person may upload a new document right now.
create function public.can_submit_student_document()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.profiles
        where id = auth.uid() and account_status in ('DOCUMENT_REQUIRED', 'REJECTED')
    )
$$;

revoke all on function public.can_submit_student_document() from public, anon;
grant execute on function public.can_submit_student_document() to authenticated;

create policy student_documents_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'student-documents'
        and (storage.foldername(name))[1] = (select auth.uid())::text
        and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.pdf$'
        and (select public.can_submit_student_document())
    );

create policy student_documents_select_own_or_admin on storage.objects
    for select to authenticated
    using (
        bucket_id = 'student-documents'
        and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or (select public.is_admin())
        )
    );

-- No update/delete policies: an uploaded document is evidence for the review.

-- submit_student_document (service role only) ---------------------------------

create function public.submit_student_document(p_user_id uuid, p_path text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_status public.account_status;
    v_id uuid;
begin
    if p_user_id is null or p_path is null
        or p_path !~ ('^' || p_user_id::text || '/[0-9a-f-]{36}\.pdf$') then
        raise exception using errcode = 'P0001', message = 'invalid_document_path';
    end if;

    if not exists (
        select 1 from storage.objects where bucket_id = 'student-documents' and name = p_path
    ) then
        raise exception using errcode = 'P0001', message = 'document_not_found';
    end if;

    select account_status into v_status from public.profiles where id = p_user_id for update;
    if not found then
        raise exception using errcode = 'P0001', message = 'profile_not_found';
    end if;
    if v_status not in ('DOCUMENT_REQUIRED', 'REJECTED') then
        raise exception using errcode = 'P0001', message = 'verification_not_allowed';
    end if;

    insert into public.student_verifications (user_id, document_path)
    values (p_user_id, p_path)
    returning id into v_id;

    update public.profiles set account_status = 'PENDING_REVIEW' where id = p_user_id;
    return v_id;
exception
    when unique_violation then
        raise exception using errcode = 'P0001', message = 'verification_already_pending';
end;
$$;

revoke all on function public.submit_student_document(uuid, text) from public, anon, authenticated;
grant execute on function public.submit_student_document(uuid, text) to service_role;

-- Admin review -------------------------------------------------------------------

create function public.list_pending_verifications()
returns table (
    verification_id uuid,
    user_id uuid,
    email text,
    full_name text,
    username text,
    university_name text,
    department text,
    document_path text,
    submitted_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    return query
    select v.id, p.id, p.email, p.full_name, p.username, u.name, p.department, v.document_path, v.created_at
    from public.student_verifications v
    join public.profiles p on p.id = v.user_id
    left join public.universities u on u.id = p.university_id
    where v.status = 'PENDING'
    order by v.created_at;
end;
$$;

revoke all on function public.list_pending_verifications() from public, anon;
grant execute on function public.list_pending_verifications() to authenticated;

create function public.review_student_verification(
    p_verification_id uuid,
    p_approve boolean,
    p_reason text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid;
    v_reason text := nullif(btrim(coalesce(p_reason, '')), '');
begin
    if not public.is_admin() then
        raise exception using errcode = 'P0001', message = 'admin_required';
    end if;
    if p_approve is null then
        raise exception using errcode = 'P0001', message = 'invalid_decision';
    end if;
    if not p_approve and (v_reason is null or char_length(v_reason) not between 3 and 500) then
        raise exception using errcode = 'P0001', message = 'rejection_reason_required';
    end if;

    select user_id into v_user_id
    from public.student_verifications
    where id = p_verification_id and status = 'PENDING'
    for update;
    if not found then
        raise exception using errcode = 'P0001', message = 'verification_not_pending';
    end if;

    update public.student_verifications
    set status = case when p_approve then 'APPROVED'::public.verification_status else 'REJECTED' end,
        rejection_reason = case when p_approve then null else v_reason end,
        reviewed_by = auth.uid(),
        reviewed_at = now()
    where id = p_verification_id;

    update public.profiles
    set account_status = case when p_approve then 'APPROVED'::public.account_status else 'REJECTED' end
    where id = v_user_id;
end;
$$;

revoke all on function public.review_student_verification(uuid, boolean, text) from public, anon;
grant execute on function public.review_student_verification(uuid, boolean, text) to authenticated;

-- A rejected person may correct their profile before uploading again ----------

create or replace function public.complete_profile(
    p_full_name text,
    p_username text,
    p_university_id uuid,
    p_department text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_status public.account_status;
    v_full_name text := btrim(coalesce(p_full_name, ''));
    v_username text := lower(btrim(coalesce(p_username, '')));
    v_department text := btrim(coalesce(p_department, ''));
begin
    if v_uid is null then
        raise exception using errcode = 'P0001', message = 'not_authenticated';
    end if;

    select account_status into v_status
    from public.profiles
    where id = v_uid
    for update;

    if not found then
        raise exception using errcode = 'P0001', message = 'profile_not_found';
    end if;

    if v_status not in ('PROFILE_INCOMPLETE', 'DOCUMENT_REQUIRED', 'REJECTED') then
        raise exception using errcode = 'P0001', message = 'profile_locked';
    end if;

    if char_length(v_full_name) not between 2 and 100 then
        raise exception using errcode = 'P0001', message = 'invalid_full_name';
    end if;

    if v_username !~ '^[a-z0-9_.]{3,30}$' then
        raise exception using errcode = 'P0001', message = 'invalid_username';
    end if;

    if char_length(v_department) not between 2 and 120 then
        raise exception using errcode = 'P0001', message = 'invalid_department';
    end if;

    if p_university_id is null or not exists (
        select 1 from public.universities where id = p_university_id and is_active
    ) then
        raise exception using errcode = 'P0001', message = 'university_not_found';
    end if;

    if exists (select 1 from public.profiles where username = v_username and id <> v_uid) then
        raise exception using errcode = 'P0001', message = 'username_taken';
    end if;

    update public.profiles
    set full_name = v_full_name,
        username = v_username,
        university_id = p_university_id,
        department = v_department,
        account_status = 'DOCUMENT_REQUIRED'
    where id = v_uid;
exception
    when unique_violation then
        raise exception using errcode = 'P0001', message = 'username_taken';
end;
$$;
