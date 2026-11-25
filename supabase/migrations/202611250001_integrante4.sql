-- Only public/social data. Never health profiles, private routes or raw sensor data.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table public.social_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null check (char_length(display_name) between 2 and 50),
  consent_at timestamptz not null default now(),
  deletion_requested_at timestamptz
);
create table public.city_entries (
  id uuid primary key,
  owner_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('PLOGGING', 'REPORT')),
  label text not null,
  description text not null default '' check (char_length(description) <= 500),
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  photo_path text not null unique,
  resolved boolean not null default false,
  created_at timestamptz not null default now(),
  points integer generated always as (
    case when kind = 'PLOGGING' then
      case label when 'LITTLE' then 5 when 'MEDIUM' then 10 when 'MUCH' then 20 else 0 end
    else 0 end
  ) stored,
  check ((kind = 'PLOGGING' and label in ('LITTLE', 'MEDIUM', 'MUCH')) or
         (kind = 'REPORT' and label in ('POTHOLE', 'WASTE', 'LIGHT', 'OTHER'))),
  check (photo_path = owner_id::text || '/' || id::text || '.jpg')
);
create index city_entries_created on public.city_entries(created_at desc, id);
create index city_entries_owner on public.city_entries(owner_id);
create table public.ai_usage (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  task text not null,
  created_at timestamptz not null default now()
);
create index ai_usage_retention on public.ai_usage(created_at);
create table private.ai_quota (
  user_id uuid not null references auth.users(id) on delete cascade,
  hour timestamptz not null,
  used integer not null check (used between 1 and 40),
  primary key(user_id, hour)
);
alter table public.social_profiles enable row level security;
alter table public.city_entries enable row level security;
alter table public.ai_usage enable row level security;
alter table private.ai_quota enable row level security;
revoke all on public.social_profiles, public.city_entries, public.ai_usage from anon, authenticated;
grant select on public.social_profiles, public.city_entries, public.ai_usage to authenticated;
grant all on public.social_profiles, public.city_entries, public.ai_usage, private.ai_quota to service_role;
grant usage on schema private to service_role;
grant usage, select on sequence public.ai_usage_id_seq to service_role;

create policy profile_self on public.social_profiles for select to authenticated
using (user_id = (select auth.uid()));
create policy city_authenticated on public.city_entries for select to authenticated
using (exists (select 1 from public.social_profiles p
  where p.user_id = (select auth.uid()) and p.deletion_requested_at is null));
create policy usage_self on public.ai_usage for select to authenticated
using (user_id = (select auth.uid()));

create function public.accept_social_profile(p_display_name text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'Unauthorized'; end if;
  if exists(select 1 from public.social_profiles where user_id=auth.uid() and deletion_requested_at is not null)
    then raise exception 'Deletion pending'; end if;
  insert into public.social_profiles(user_id,display_name) values(auth.uid(),trim(p_display_name))
  on conflict(user_id) do update set display_name=excluded.display_name;
end $$;
revoke all on function public.accept_social_profile(text) from public, anon;
grant execute on function public.accept_social_profile(text) to authenticated;

create function public.set_report_resolved(p_id uuid, p_resolved boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not exists(select 1 from public.social_profiles where user_id=auth.uid() and deletion_requested_at is null)
    then raise exception 'Unauthorized'; end if;
  update public.city_entries set resolved=p_resolved
  where id=p_id and owner_id=auth.uid() and kind='REPORT';
  if not found then raise exception 'Not found'; end if;
end $$;
revoke all on function public.set_report_resolved(uuid,boolean) from public, anon;
grant execute on function public.set_report_resolved(uuid,boolean) to authenticated;

-- Service-only atomic reservation: no concurrent bypass, no direct equivalent write grant.
create function public.reserve_ai_call(p_user uuid, p_task text) returns boolean
language plpgsql security definer set search_path = '' as $$
declare v_used integer;
begin
  if not exists(select 1 from public.social_profiles where user_id=p_user and deletion_requested_at is null)
    then return false; end if;
  insert into private.ai_quota(user_id,hour,used) values(p_user,date_trunc('hour',now()),1)
  on conflict(user_id,hour) do update set used=private.ai_quota.used+1
    where private.ai_quota.used<40 returning used into v_used;
  if v_used is null then return false; end if;
  insert into public.ai_usage(user_id,task) values(p_user,p_task);
  return true;
end $$;
revoke all on function public.reserve_ai_call(uuid,text) from public, anon, authenticated;
grant execute on function public.reserve_ai_call(uuid,text) to service_role;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('city-photos','city-photos',false,2097152,array['image/jpeg']);
-- Upload/delete exclusively via JWT-validated Edge functions (server strips JPEG metadata).
create policy city_photo_read on storage.objects for select to authenticated using (
  bucket_id='city-photos' and exists(select 1 from public.city_entries e where e.photo_path=name)
);

-- Explicit retention task; schedule daily with pg_cron when deploying (see SERVER.md).
create function public.purge_ai_usage() returns void
language sql security definer set search_path = '' as $$
  delete from public.ai_usage where created_at < now()-interval '30 days';
  delete from private.ai_quota where hour < now()-interval '30 days';
$$;
revoke all on function public.purge_ai_usage() from public, anon, authenticated;
grant execute on function public.purge_ai_usage() to service_role;
