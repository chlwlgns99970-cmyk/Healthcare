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
  '1.0.3',
  4,
  'android',
  'https://github.com/chlwlgns99970-cmyk/Healthcare/releases/download/v1.0.3/today-mwo-meokji-v1.0.3.apk',
  'today-mwo-meokji-v1.0.3.apk',
  127899968,
  'DB88D6DCBCE7500CA98B688F53A29C890C45C14D7031BAD1A33059DDB0157BAD',
  array[
    '앱 자체 업데이트 확인 기능 추가',
    '설정에서 업데이트 확인 가능',
    'APK 다운로드 후 무결성·패키지·버전·서명 검증',
    '기존 기록을 유지하는 업데이트 설치 흐름'
  ],
  '2026-09-27T14:26:54Z'::timestamptz,
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
      and version_code = 3
      and version_name = '1.0.2'
  ) then
    raise exception 'v1.0.2 release metadata must be preserved';
  end if;

  if not exists (
    select 1
    from today_mwo_meokji.app_releases
    where platform = 'android'
      and version_code = 4
      and version_name = '1.0.3'
      and status = 'published'
      and is_latest = true
  ) then
    raise exception 'v1.0.3 release metadata was not published as latest';
  end if;
end
$$;

commit;
