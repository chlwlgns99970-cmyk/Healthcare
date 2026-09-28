begin;

update today_mwo_meokji.app_releases
set is_latest = false
where platform = 'android'
  and is_latest = true;

insert into today_mwo_meokji.app_releases (
  version_name,
  version_code,
  platform,
  apk_url,
  file_name,
  file_size_bytes,
  sha256,
  release_notes,
  released_at,
  is_latest,
  minimum_android_version,
  status
)
values (
  '1.0.4',
  5,
  'android',
  'https://github.com/chlwlgns99970-cmyk/Healthcare/releases/download/v1.0.4/today-mwo-meokji-v1.0.4.apk',
  'today-mwo-meokji-v1.0.4.apk',
  127916948,
  'E186A88E56F5F5F958AC13665EE9BCFACF9A17A46A102A357C4948AFEFD969BE',
  array[
    '체중목표 기반 에너지 추천과 목표 설정 화면 개선',
    '홈 식사·칼로리 흐름과 달력 목표 상태 개선',
    '과일·채소·간식 검색 및 음식 카테고리 확대',
    '자주 먹는 음식 저장과 검색 중복 개선'
  ],
  '2026-09-28T12:44:14Z'::timestamptz,
  true,
  null,
  'published'
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
begin
  if not exists (
    select 1
    from today_mwo_meokji.app_releases
    where platform = 'android'
      and version_code = 4
      and version_name = '1.0.3'
      and status = 'published'
      and is_latest = false
  ) then
    raise exception 'v1.0.3 release metadata must be preserved as non-latest';
  end if;

  if not exists (
    select 1
    from today_mwo_meokji.app_releases
    where platform = 'android'
      and version_code = 5
      and version_name = '1.0.4'
      and status = 'published'
      and is_latest = true
  ) then
    raise exception 'v1.0.4 release metadata was not published as latest';
  end if;
end
$$;

commit;
