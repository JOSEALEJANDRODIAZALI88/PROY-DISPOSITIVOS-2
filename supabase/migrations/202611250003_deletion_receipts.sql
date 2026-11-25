-- Opaque retry receipt: no email/health/photo, owner is SET NULL when Auth deletion succeeds.
-- A lost HTTP response must not strand local health data behind an invalid/deleted JWT.
create table private.account_deletion_receipts (
  hash text primary key check(hash ~ '^[0-9a-f]{64}$'),
  owner_id uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now()
);
revoke all on private.account_deletion_receipts from public,anon,authenticated;
create function public.deletion_completed(p_hash text) returns boolean
language sql security definer set search_path='' as $$
 select exists(select 1 from private.account_deletion_receipts where hash=p_hash and owner_id is null);
$$;
create function public.begin_account_deletion(p_user uuid,p_hash text) returns void
language plpgsql security definer set search_path='' as $$
begin
  perform 1 from public.social_profiles where user_id=p_user for update;
  insert into private.account_deletion_receipts(hash,owner_id) values(p_hash,p_user)
    on conflict(hash) do nothing;
  if not exists(select 1 from private.account_deletion_receipts where hash=p_hash and owner_id=p_user) then
    raise exception 'Receipt unavailable';
  end if;
  update public.social_profiles set deletion_requested_at=now() where user_id=p_user;
end $$;
revoke all on function public.deletion_completed(text) from public,anon,authenticated;
revoke all on function public.begin_account_deletion(uuid,text) from public,anon,authenticated;
grant execute on function public.deletion_completed(text),public.begin_account_deletion(uuid,text) to service_role;
create or replace function public.purge_ai_usage() returns void
language plpgsql security definer set search_path='' as $$
begin
  delete from public.ai_usage where created_at < now()-interval '30 days';
  delete from private.ai_quota where hour < now()-interval '30 days';
  delete from private.account_deletion_receipts where created_at < now()-interval '30 days';
end $$;
