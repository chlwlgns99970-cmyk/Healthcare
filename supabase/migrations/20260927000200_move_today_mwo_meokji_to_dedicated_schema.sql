create schema if not exists today_mwo_meokji authorization postgres;

revoke all on schema today_mwo_meokji from public;
grant usage on schema today_mwo_meokji to anon, authenticated, service_role;

do $$
declare
  before_count bigint;
  after_count bigint;
  before_rows jsonb;
  after_rows jsonb;
begin
  if to_regclass('public.app_releases') is null then
    raise exception 'public.app_releases does not exist';
  end if;

  if to_regclass('today_mwo_meokji.app_releases') is not null then
    raise exception 'today_mwo_meokji.app_releases already exists';
  end if;

  select count(*), coalesce(jsonb_agg(to_jsonb(r) order by r.id), '[]'::jsonb)
    into before_count, before_rows
  from public.app_releases r;

  alter table public.app_releases set schema today_mwo_meokji;

  select count(*), coalesce(jsonb_agg(to_jsonb(r) order by r.id), '[]'::jsonb)
    into after_count, after_rows
  from today_mwo_meokji.app_releases r;

  if before_count <> after_count or before_rows is distinct from after_rows then
    raise exception 'app_releases data changed during schema move';
  end if;
end
$$;

alter table today_mwo_meokji.app_releases enable row level security;

revoke all on table today_mwo_meokji.app_releases from anon, authenticated;
grant select on table today_mwo_meokji.app_releases to anon, authenticated;

do $$
begin
  if not exists (
    select 1
    from pg_policies
    where schemaname = 'today_mwo_meokji'
      and tablename = 'app_releases'
      and policyname = 'Public can read published app releases'
      and cmd = 'SELECT'
  ) then
    create policy "Public can read published app releases"
      on today_mwo_meokji.app_releases
      for select
      to anon, authenticated
      using (status = 'published');
  end if;
end
$$;

-- Temporary compatibility view: keeps the existing production query working
-- until the web deployment explicitly selects today_mwo_meokji.
create view public.app_releases
with (security_invoker = true)
as
select *
from today_mwo_meokji.app_releases;

revoke all on table public.app_releases from public, anon, authenticated;
grant select on table public.app_releases to anon, authenticated;
