-- Serialize publication/upload completion against account deletion. Service-role bypasses
-- RLS, so these checks must also apply to trusted server writes.
create function private.guard_city_owner() returns trigger
language plpgsql security definer set search_path = '' as $$
declare v_deleting timestamptz; v_owner uuid;
begin
  if tg_table_schema='storage' then
    if new.bucket_id <> 'city-photos' then return new; end if;
    if new.name !~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$' then raise exception 'Invalid photo path'; end if;
    v_owner := split_part(new.name,'/',1)::uuid;
  else v_owner := new.owner_id;
  end if;
  select deletion_requested_at into v_deleting from public.social_profiles
    where user_id=v_owner for update;
  if not found or v_deleting is not null then raise exception 'Account unavailable'; end if;
  return new;
end $$;
revoke all on function private.guard_city_owner() from public, anon, authenticated;
create trigger guard_city_write before insert or update on public.city_entries
for each row execute function private.guard_city_owner();
create trigger guard_city_upload before insert or update on storage.objects
for each row execute function private.guard_city_owner();

-- Daily automatic retention. Hosted Supabase supports pg_cron; keeps the task versioned.
create extension if not exists pg_cron with schema pg_catalog;
select cron.schedule('pacefit-ai-retention', '20 3 * * *', 'select public.purge_ai_usage()');
