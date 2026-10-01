-- Public v1.0.6 APK SHA and size were independently verified before preparing this SQL.
-- Preserve every existing release column except its is_latest flag; no permission/RLS/public-schema changes.
begin;

lock table today_mwo_meokji.app_releases in share row exclusive mode;

create temporary table release_106_before_snapshot on commit drop as
select count(*) as row_count,
       array_agg(r.id order by r.id) as old_ids,
       coalesce(jsonb_agg(to_jsonb(r) - 'is_latest' order by r.id), '[]'::jsonb) as old_rows
from today_mwo_meokji.app_releases r;

do $$
begin
  if not exists (select 1 from today_mwo_meokji.app_releases
                 where platform = 'android' and version_code = 5
                   and version_name = '1.0.4' and status = 'published') then
    raise exception 'Published v1.0.4 release must already exist';
  end if;
  if not exists (select 1 from today_mwo_meokji.app_releases
                 where platform = 'android' and version_code = 6
                   and version_name = '1.0.5' and status = 'published') then
    raise exception 'Published v1.0.5 release must already exist';
  end if;
  if exists (select 1 from today_mwo_meokji.app_releases
             where platform = 'android' and version_code > 7) then
    raise exception 'A newer release exists; refuse to replace it with v1.0.6';
  end if;
  if not (select relrowsecurity from pg_class
          where oid = 'today_mwo_meokji.app_releases'::regclass) then
    raise exception 'Release table RLS must remain enabled';
  end if;
  if to_regclass('public.app_releases') is not null then
    raise exception 'Unexpected public.app_releases relation';
  end if;
end
$$;

update today_mwo_meokji.app_releases
set is_latest = false
where platform = 'android' and is_latest = true;

insert into today_mwo_meokji.app_releases (
  version_name, version_code, platform, apk_url, file_name, file_size_bytes,
  sha256, release_notes, released_at, is_latest, minimum_android_version, status
)
values (
  '1.0.6', 7, 'android',
  'https://github.com/chlwlgns99970-cmyk/Healthcare/releases/download/v1.0.6/today-mwo-meokji-v1.0.6.apk',
  'today-mwo-meokji-v1.0.6.apk',
  128080912,
  '0104AF47FA6DCD3B4F5376B6593C5D19910F9094B5A538B5581E78C82265F980',
  array[
    '오늘의 추천을 8개 식사 스타일의 하루 식단 추천으로 개선',
    '아침·점심·저녁·간식을 하루 목표 kcal에 맞춰 조합',
    '다른 메뉴·다른 조합·같은 날 식단 유지 및 피하고 싶은 음식·취향 반영',
    '음식 빠른 기록·즐겨찾기·최근 기록량 활용 개선',
    '두부·달걀·과일 등 기본 음식·대표 검색 결과 개선',
    '기록 목록에서 바로 수정하고 홈 식사명·kcal·리포트 확인',
    '탄수화물·단백질·지방 누적 및 부분 정보 표시 개선',
    'Android 10 업데이트 호환성과 검증 보강',
    '저속노화식 스타일은 데이터 부족 시 제한 안내 표시'
  ],
  '2026-10-01T15:09:08Z'::timestamptz,
  true, null, 'published'
)
on conflict (platform, version_code)
do update set
  version_name = excluded.version_name,
  apk_url = excluded.apk_url,
  file_name = excluded.file_name,
  file_size_bytes = excluded.file_size_bytes,
  sha256 = excluded.sha256,
  release_notes = excluded.release_notes,
  released_at = excluded.released_at,
  is_latest = excluded.is_latest,
  minimum_android_version = excluded.minimum_android_version,
  status = excluded.status;

do $$
declare
  expected_old_rows jsonb;
  actual_old_rows jsonb;
  expected_old_count bigint;
  actual_old_count bigint;
begin
  if (select count(*) from today_mwo_meokji.app_releases
       where platform = 'android' and is_latest = true) <> 1 then
    raise exception 'Exactly one Android latest release is required';
  end if;
  if not exists (
    select 1 from today_mwo_meokji.app_releases
    where platform = 'android' and version_code = 7 and version_name = '1.0.6'
      and apk_url = 'https://github.com/chlwlgns99970-cmyk/Healthcare/releases/download/v1.0.6/today-mwo-meokji-v1.0.6.apk'
      and file_name = 'today-mwo-meokji-v1.0.6.apk'
      and file_size_bytes = 128080912
      and sha256 = '0104AF47FA6DCD3B4F5376B6593C5D19910F9094B5A538B5581E78C82265F980'
      and release_notes = array[
        '오늘의 추천을 8개 식사 스타일의 하루 식단 추천으로 개선',
    '아침·점심·저녁·간식을 하루 목표 kcal에 맞춰 조합',
    '다른 메뉴·다른 조합·같은 날 식단 유지 및 피하고 싶은 음식·취향 반영',
    '음식 빠른 기록·즐겨찾기·최근 기록량 활용 개선',
    '두부·달걀·과일 등 기본 음식·대표 검색 결과 개선',
    '기록 목록에서 바로 수정하고 홈 식사명·kcal·리포트 확인',
    '탄수화물·단백질·지방 누적 및 부분 정보 표시 개선',
    'Android 10 업데이트 호환성과 검증 보강',
    '저속노화식 스타일은 데이터 부족 시 제한 안내 표시'
      ]::text[]
      and released_at = '2026-10-01T15:09:08Z'::timestamptz
      and minimum_android_version is null and status = 'published' and is_latest = true
  ) then
    raise exception 'v1.0.6 metadata does not match the verified public APK';
  end if;
  if not exists (select 1 from today_mwo_meokji.app_releases
                 where platform = 'android' and version_code = 5
                   and version_name = '1.0.4' and status = 'published' and is_latest = false)
     or not exists (select 1 from today_mwo_meokji.app_releases
                    where platform = 'android' and version_code = 6
                      and version_name = '1.0.5' and status = 'published' and is_latest = false) then
    raise exception 'v1.0.4 and v1.0.5 must be preserved as non-latest';
  end if;
  select row_count, old_rows into expected_old_count, expected_old_rows
  from pg_temp.release_106_before_snapshot;
  select count(*), coalesce(jsonb_agg(to_jsonb(r) - 'is_latest' order by r.id), '[]'::jsonb)
    into actual_old_count, actual_old_rows
  from today_mwo_meokji.app_releases r
  where r.id = any ((select old_ids from pg_temp.release_106_before_snapshot)::bigint[]);
  if expected_old_count <> actual_old_count or expected_old_rows is distinct from actual_old_rows then
    raise exception 'Existing release metadata changed beyond is_latest';
  end if;
  if exists (
    select 1 from today_mwo_meokji.app_releases r
    where not (r.id = any ((select old_ids from pg_temp.release_106_before_snapshot)::bigint[]))
      and not (r.platform = 'android' and r.version_code = 7)
  ) then
    raise exception 'Unexpected release row was added';
  end if;
end
$$;

commit;
