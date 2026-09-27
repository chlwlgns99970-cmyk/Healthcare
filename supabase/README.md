# Supabase schema ownership

## `today_mwo_meokji`

`오늘 뭐먹지` 앱이 소유하는 데이터베이스 객체의 기본 schema입니다.

- `today_mwo_meokji.app_releases`
- 향후 앱 전용 table, view, function

새로운 앱 전용 객체는 특별한 공유 목적이 없는 한 `public`에 만들지 않습니다.

## `public`

여러 서비스가 실제로 공유하는 객체만 둡니다. `오늘 뭐먹지` 전용 table 신규 생성은 금지합니다.

## Supabase 관리 schema

`auth`, `storage`, `realtime`, `extensions`, `graphql`, `vault` 등 Supabase 관리 schema는 이동하거나 수정하지 않습니다. 향후 사용자 기능이 필요하면 `auth.users`는 그대로 사용하고, 앱 소유 profile은 `today_mwo_meokji.profiles`처럼 전용 schema에 둡니다.

## Data API

Supabase Project의 exposed schemas에는 `today_mwo_meokji`가 포함되어야 합니다. 공개 role에는 schema `USAGE`와 published release 조회용 `SELECT`만 부여하며, INSERT/UPDATE/DELETE 권한은 부여하지 않습니다.
