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
  '1.0.5',
  6,
  'android',
  'https://github.com/chlwlgns99970-cmyk/Healthcare/releases/download/v1.0.5/today-mwo-meokji-v1.0.5.apk',
  'today-mwo-meokji-v1.0.5.apk',
  127933332,
  '6337EE0EB7C69AB9C5E9A6C84EC7CB4EEC6D665D6E264F61E5529FA5B0C62DCE',
  array[
    'Android 10 자동 업데이트 호환성 및 검증 강화',
    '음식 검색·기록 사용성 개선',
    '홈 탄수화물·단백질·지방 누적 정확도 개선',
    'Room 6→7 마이그레이션으로 자주 먹는 음식 영양 스냅샷 지원'
  ],
  '2026-09-29T14:09:50Z'::timestamptz,
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
      and version_code = 5
      and version_name = '1.0.4'
      and status = 'published'
      and is_latest = false
  ) then
    raise exception 'v1.0.4 release metadata must be preserved as non-latest';
  end if;

  if not exists (
    select 1
    from today_mwo_meokji.app_releases
    where platform = 'android'
      and version_code = 6
      and version_name = '1.0.5'
      and status = 'published'
      and is_latest = true
      and file_size_bytes = 127933332
      and sha256 = '6337EE0EB7C69AB9C5E9A6C84EC7CB4EEC6D665D6E264F61E5529FA5B0C62DCE'
  ) then
    raise exception 'v1.0.5 release metadata was not published as latest';
  end if;
end
$$;

commit;
