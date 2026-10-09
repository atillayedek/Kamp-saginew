-- Last applied Google event is metadata on the existing entitlement; its
-- retention/deletion follows the entitlement (account deletion CASCADE).
alter table public.entitlements add column billing_event_time_ms bigint not null default 0;

create function public.billing_owner_for_token(p_purchase_token text)
returns table(user_id uuid, product_id text)
language sql stable security definer set search_path = '' as $$
    select e.user_id, p.play_product_id from public.entitlements e
    join public.subscription_plans p on p.id = e.plan_id where e.purchase_token = p_purchase_token
$$;

create function public.reconcile_billing_entitlement(p_purchase_token text, p_expires_at timestamptz, p_event_time_ms bigint)
returns void language plpgsql security definer set search_path = '' as $$
begin
    if p_event_time_ms is null or p_event_time_ms <= 0 then
        raise exception using errcode = 'P0001', message = 'invalid_request';
    end if;
    -- Repeat delivery is idempotent. Older events cannot overwrite a newer one;
    -- the owner and plan cannot be reassigned through this function.
    update public.entitlements
    set expires_at = coalesce(p_expires_at, now()), billing_event_time_ms = p_event_time_ms, updated_at = now()
    where purchase_token = p_purchase_token and billing_event_time_ms < p_event_time_ms;
end;
$$;
revoke all on function public.billing_owner_for_token(text) from public, anon, authenticated;
revoke all on function public.reconcile_billing_entitlement(text, timestamptz, bigint) from public, anon, authenticated;
grant execute on function public.billing_owner_for_token(text) to service_role;
grant execute on function public.reconcile_billing_entitlement(text, timestamptz, bigint) to service_role;
