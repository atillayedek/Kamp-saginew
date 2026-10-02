-- Matching: other students' ACTIVE requirements ranked by real pgvector cosine
-- similarity to one of the caller's requirements.
--
-- The score shown in the app is 100 * (1 - cosine distance), rounded, straight
-- from this query; nothing is invented on the client. Candidates are limited to
-- the caller's university (the app is campus-first) and to a minimum
-- similarity so unrelated needs are not shown as "matches".

create function public.find_matches(p_requirement_id uuid, p_limit integer default 20)
returns table (
    requirement_id uuid,
    title text,
    description text,
    category public.requirement_category,
    tags text[],
    location_text text,
    starts_at timestamptz,
    participants_needed integer,
    created_at timestamptz,
    score integer,
    owner_id uuid,
    owner_full_name text,
    owner_username text,
    owner_department text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_source public.requirements%rowtype;
begin
    if not exists (select 1 from public.current_student()) then
        raise exception using errcode = 'P0001', message = 'approved_student_required';
    end if;

    select r.* into v_source
    from public.requirements r
    where r.id = p_requirement_id and r.owner_id = auth.uid();
    if not found then
        raise exception using errcode = 'P0001', message = 'requirement_not_found';
    end if;

    return query
    select c.id, c.title, c.description, c.category, c.tags, c.location_text, c.starts_at,
           c.participants_needed, c.created_at,
           round(100 * (1 - (c.embedding operator(extensions.<=>) v_source.embedding)))::integer,
           p.id, p.full_name, p.username, p.department
    from public.requirements c
    join public.profiles p on p.id = c.owner_id and p.account_status = 'APPROVED'
    where c.status = 'ACTIVE'
      and c.owner_id <> v_source.owner_id
      and c.university_id = v_source.university_id
      and (c.embedding operator(extensions.<=>) v_source.embedding) <= 0.5
    order by c.embedding operator(extensions.<=>) v_source.embedding, c.created_at desc
    limit least(greatest(coalesce(p_limit, 20), 1), 50);
end;
$$;

revoke all on function public.find_matches(uuid, integer) from public, anon;
grant execute on function public.find_matches(uuid, integer) to authenticated;
