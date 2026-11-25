-- Serialize profile acceptance/deletion even if email was confirmed but profile never accepted.
create or replace function public.accept_social_profile(p_display_name text) returns void
language plpgsql security definer set search_path='' as $$
begin
  if auth.uid() is null then raise exception 'Unauthorized'; end if;
  perform 1 from auth.users where id=auth.uid() for update;
  if not found then raise exception 'Unauthorized'; end if;
  if exists(select 1 from private.account_deletion_receipts where owner_id=auth.uid())
     or exists(select 1 from public.social_profiles where user_id=auth.uid() and deletion_requested_at is not null)
    then raise exception 'Deletion pending'; end if;
  insert into public.social_profiles(user_id,display_name) values(auth.uid(),trim(p_display_name))
  on conflict(user_id) do update set display_name=excluded.display_name;
end $$;
create or replace function public.begin_account_deletion(p_user uuid,p_hash text) returns void
language plpgsql security definer set search_path='' as $$
begin
  perform 1 from auth.users where id=p_user for update;
  if not found then raise exception 'Account unavailable'; end if;
  perform 1 from public.social_profiles where user_id=p_user for update;
  insert into private.account_deletion_receipts(hash,owner_id) values(p_hash,p_user)
    on conflict(hash) do nothing;
  if not exists(select 1 from private.account_deletion_receipts where hash=p_hash and owner_id=p_user) then
    raise exception 'Receipt unavailable';
  end if;
  update public.social_profiles set deletion_requested_at=now() where user_id=p_user;
end $$;
