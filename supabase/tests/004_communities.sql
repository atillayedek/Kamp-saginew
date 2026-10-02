-- Communities: visibility, approval requirement, counters, pagination, rate limits.

create function tests.approved_student(p_email text, p_username text, p_university_name text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
    v_id := tests.create_user(p_email);
    update public.profiles
    set full_name = 'Öğrenci ' || p_username, username = p_username, department = 'Hukuk',
        university_id = (select id from public.universities where name = p_university_name),
        account_status = 'APPROVED'
    where id = v_id;
    return v_id;
end;
$$;
grant execute on function tests.approved_student(text, text, text) to authenticated, service_role;

-- Two students at İTÜ, one at ODTÜ, one pending.
create temp table people as
select tests.approved_student('itu1@example.edu.tr', 'itu_bir', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as itu1,
       tests.approved_student('itu2@example.edu.tr', 'itu_iki', 'İSTANBUL TEKNİK ÜNİVERSİTESİ') as itu2,
       tests.approved_student('odtu@example.edu.tr', 'odtu_bir', 'ORTA DOĞU TEKNİK ÜNİVERSİTESİ') as odtu,
       tests.create_user('pending@example.edu.tr') as pending;
grant select on people to authenticated;

-- 1. Posting sets author and university from the profile; scopes are enforced.
begin;
select tests.act_as((select itu1 from people));
select public.create_post('GENERAL', '  Herkese merhaba  ');
select public.create_post('UNIVERSITY', 'Sadece İTÜ');
select tests.expect_error($$select public.create_post('GENERAL', '   ')$$, 'invalid_post_body');
select tests.expect_error(format($$select public.create_post('GENERAL', %L)$$, repeat('a', 2001)), 'invalid_post_body');
select tests.reset_role();
select tests.assert_equals(
    (select string_agg(scope::text || ':' || coalesce(university_id::text, '-') || ':' || body, ',' order by scope)
     from public.posts),
    'GENERAL:-:Herkese merhaba,UNIVERSITY:' ||
        (select id::text from public.universities where name = 'İSTANBUL TEKNİK ÜNİVERSİTESİ') || ':Sadece İTÜ',
    'stored posts'
);

-- Same-university student sees both; other university only the general one.
select tests.act_as((select itu2 from people));
select tests.assert_equals((select count(*) from public.list_posts('UNIVERSITY')), 1::bigint, 'itu2 sees university post');
select tests.assert_equals((select count(*) from public.posts), 2::bigint, 'itu2 table access');
select tests.reset_role();
select tests.act_as((select odtu from people));
select tests.assert_equals((select count(*) from public.list_posts('UNIVERSITY')), 0::bigint, 'odtu sees no itu posts');
select tests.assert_equals((select count(*) from public.list_posts('GENERAL')), 1::bigint, 'odtu sees general');
select tests.assert_equals((select count(*) from public.posts), 1::bigint, 'odtu table access');
select tests.expect_error(
    format($$select * from public.get_post(%L)$$, (select id from public.posts where scope = 'UNIVERSITY')),
    'post_not_found'
);
select tests.expect_error(
    format($$select public.add_comment(%L, 'x')$$, (select id from public.posts where scope = 'UNIVERSITY')),
    'post_not_found'
);
rollback;

-- 2. Unapproved and anonymous users get nothing.
begin;
select tests.act_as((select itu1 from people));
select public.create_post('GENERAL', 'Merhaba');
select tests.reset_role();
select tests.act_as((select pending from people));
select tests.expect_error($$select public.create_post('GENERAL', 'x')$$, 'approved_student_required');
select tests.expect_error($$select * from public.list_posts('GENERAL')$$, 'approved_student_required');
select tests.assert_equals((select count(*) from public.posts), 0::bigint, 'pending sees no posts');
select tests.reset_role();
select tests.act_as_anon();
select tests.expect_error($$select * from public.list_posts('GENERAL')$$, 'permission denied for function list_posts');
select tests.assert_equals((select count(*) from public.posts), 0::bigint, 'anon sees no posts');
rollback;

-- 3. Direct writes are impossible; counters and authorship come from the server.
begin;
select tests.act_as((select itu1 from people));
create temp table p as select public.create_post('GENERAL', 'Sayaç') as id;
select tests.expect_error(
    format($$insert into public.posts (author_id, scope, body) values (%L, 'GENERAL', 'sahte')$$, (select itu2 from people)),
    'new row violates row-level security policy for table "posts"'
);
update public.posts set like_count = 999;
select tests.expect_error(
    format($$insert into public.post_likes (post_id, user_id) values (%L, %L)$$, (select id from p), (select itu2 from people)),
    'new row violates row-level security policy for table "post_likes"'
);
select public.set_post_like((select id from p), true);
select public.set_post_like((select id from p), true);
select public.add_comment((select id from p), 'ilk yorum');
select tests.reset_role();
select tests.act_as((select odtu from people));
select tests.assert_equals(public.set_post_like((select id from p), true), 2, 'two likes');
create temp table c as select public.add_comment((select id from p), 'ikinci yorum') as id;
select tests.assert_equals(
    (select like_count || '/' || comment_count || '/' || liked_by_me::text from public.get_post((select id from p))),
    '2/2/true',
    'counters and liked_by_me'
);
-- A comment can only be deleted by its author.
select tests.reset_role();
select tests.act_as((select itu1 from people));
select tests.expect_error(format($$select public.delete_comment(%L)$$, (select id from c)), 'comment_not_found');
select tests.expect_error(format($$select public.set_post_like(%L, false)$$, gen_random_uuid()), 'post_not_found');
select tests.reset_role();
select tests.act_as((select odtu from people));
select public.delete_comment((select id from c));
select public.set_post_like((select id from p), false);
select public.set_post_like((select id from p), false);
select tests.assert_equals(
    (select like_count || '/' || comment_count from public.get_post((select id from p))),
    '1/1',
    'counters after removal'
);
select tests.assert_equals((select count(*) from public.list_comments((select id from p))), 1::bigint, 'deleted comment hidden');
-- Only the author deletes a post; deleted posts disappear.
select tests.expect_error(format($$select public.delete_post(%L)$$, (select id from p)), 'post_not_found');
select tests.reset_role();
select tests.act_as((select itu1 from people));
select public.delete_post((select id from p));
select tests.assert_equals((select count(*) from public.list_posts('GENERAL')), 0::bigint, 'deleted post hidden');
select tests.expect_error(format($$select * from public.list_comments(%L)$$, (select id from p)), 'post_not_found');
rollback;

-- 4. Keyset pagination returns every post exactly once, newest first.
begin;
insert into public.posts (author_id, scope, body, created_at)
select (select itu1 from people), 'GENERAL', 'gönderi ' || n, timestamptz '2026-09-01' + n * interval '1 minute'
from generate_series(1, 45) n;
-- Two posts with the same timestamp must not be skipped.
insert into public.posts (author_id, scope, body, created_at)
values ((select itu2 from people), 'GENERAL', 'eş zamanlı', timestamptz '2026-09-01' + 20 * interval '1 minute');
select tests.act_as((select odtu from people));
create temp table seen (id uuid, created_at timestamptz, page integer);
grant all on seen to authenticated;
do $$
declare
    v_before timestamptz;
    v_before_id uuid;
    v_page integer := 0;
    v_count integer;
begin
    loop
        v_page := v_page + 1;
        insert into seen
        select l.id, l.created_at, v_page from public.list_posts('GENERAL', v_before, v_before_id, 20) l;
        get diagnostics v_count = row_count;
        exit when v_count < 20;
        select s.created_at, s.id into v_before, v_before_id
        from seen s where s.page = v_page order by s.created_at, s.id limit 1;
    end loop;
end;
$$;
select tests.assert_equals((select count(*) from seen), 46::bigint, 'all posts');
select tests.assert_equals((select count(distinct id) from seen), 46::bigint, 'no duplicates');
select tests.assert_equals((select max(page) from seen), 3, 'three pages');
select tests.reset_role();
insert into public.posts (author_id, scope, body)
select (select itu1 from people), 'GENERAL', 'ek ' || n from generate_series(1, 10) n;
select tests.act_as((select odtu from people));
select tests.assert_equals((select count(*) from public.list_posts('GENERAL', null, null, 500)), 50::bigint, 'page size capped');
rollback;

-- 5. Rate limit: 10 posts per hour.
begin;
select tests.act_as((select itu1 from people));
select public.create_post('GENERAL', 'gönderi ' || n) from generate_series(1, 10) n;
select tests.expect_error($$select public.create_post('GENERAL', 'on birinci')$$, 'rate_limited');
rollback;

-- 6. Account deletion removes the person's posts, comments and likes.
begin;
select tests.act_as((select itu1 from people));
create temp table p as select public.create_post('GENERAL', 'silinecek') as id;
select tests.reset_role();
select tests.act_as((select odtu from people));
select public.add_comment((select id from p), 'yorum');
select public.set_post_like((select id from p), true);
select tests.reset_role();
delete from auth.users where id = (select odtu from people);
select tests.assert_equals(
    (select like_count || '/' || comment_count || '/' || (select count(*) from public.comments)::text
     from public.posts where id = (select id from p)),
    '0/0/0',
    'cascade keeps counters right'
);
rollback;
