do $$
declare
  public_relation_kind "char";
begin
  select c.relkind
    into public_relation_kind
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public'
    and c.relname = 'app_releases';

  if public_relation_kind is null then
    return;
  end if;

  if public_relation_kind <> 'v' then
    raise exception 'public.app_releases is not the expected compatibility view';
  end if;

  if to_regclass('today_mwo_meokji.app_releases') is null then
    raise exception 'dedicated app_releases table is missing';
  end if;

  drop view public.app_releases;
end
$$;
