-- Execute only after local/public signed APK verification; preserve existing schema and history.
begin;
lock table today_mwo_meokji.app_releases in share row exclusive mode;
create temporary table release_107_before_snapshot on commit drop as
select count(*) as row_count, array_agg(r.id order by r.id) as old_ids,
       coalesce(jsonb_agg(to_jsonb(r)-'is_latest' order by r.id),'[]'::jsonb) as old_rows
from today_mwo_meokji.app_releases r;
do $$ begin
 if exists(select 1 from today_mwo_meokji.app_releases where platform='android' and (version_code>=8 or version_name='1.0.7')) then
  raise exception 'Version conflict: refuse duplicate/newer release'; end if;
 if (select count(*) from today_mwo_meokji.app_releases where platform='android' and is_latest and version_code=7 and version_name='1.0.6' and status='published')<>1 then
  raise exception 'Expected current latest 1.0.6/code7'; end if;
 if not(select relrowsecurity from pg_class where oid='today_mwo_meokji.app_releases'::regclass) then raise exception 'RLS must remain enabled'; end if;
 if to_regclass('public.app_releases') is not null then raise exception 'Unexpected public relation'; end if;
end $$;
update today_mwo_meokji.app_releases set is_latest=false where platform='android' and is_latest;
insert into today_mwo_meokji.app_releases (version_name,version_code,platform,apk_url,file_name,file_size_bytes,sha256,release_notes,released_at,is_latest,minimum_android_version,status) values ('1.0.7',8,'android','https://github.com/chlwlgns99970-cmyk/Healthcare/releases/download/v1.0.7/today-mwo-meokji-v1.0.7.apk','today-mwo-meokji-v1.0.7.apk',134168293,'C6B2919EE5B01DEBCE21B18AEE65EE8612D25BC8A9CA5805C0857E80384F5DA7',array['음식 상세정보와 확인 가능한 원재료·영양 정보 연결 개선','기본 음식 검색 정확도와 검색 상태 유지 개선','저장 성공 후 저장 완료 안내와 확인 동작 추가','홈 칼로리 문구 잘림 방지 및 큰 글자 표시 개선','앱 업데이트 확인 및 Android 호환성 개선','새 앱 아이콘 적용과 전반적인 안정성 개선'],'2026-10-07T13:05:00.845101+00:00'::timestamptz,true,null,'published');
do $$ declare previous jsonb; actual jsonb; old_count bigint; begin
 select old_rows,row_count into previous,old_count from pg_temp.release_107_before_snapshot;
 select coalesce(jsonb_agg(to_jsonb(r)-'is_latest' order by r.id),'[]'::jsonb) into actual
 from today_mwo_meokji.app_releases r where r.id=any((select old_ids from pg_temp.release_107_before_snapshot)::bigint[]);
 if previous is distinct from actual then raise exception 'Old metadata changed beyond latest flag'; end if;
 if (select count(*) from today_mwo_meokji.app_releases)<>old_count+1 then raise exception 'Unexpected history change'; end if;
 if (select count(*) from today_mwo_meokji.app_releases where platform='android' and is_latest)<>1
 or not exists(select 1 from today_mwo_meokji.app_releases where platform='android' and is_latest and version_code=8 and version_name='1.0.7') then raise exception 'Latest invariant failed'; end if;
end $$;
commit;
