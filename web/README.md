# 오늘 뭐먹지 다운로드 사이트

GitHub Release에 보관된 최신 Android APK 정보를 Supabase `app_releases`에서 읽어 표시하는 Next.js 사이트입니다.

## 환경 변수

- `SUPABASE_URL`: Supabase 프로젝트 URL
- `SUPABASE_ANON_KEY`: 공개 읽기 정책만 허용된 anon key

`service_role` 키는 사용하지 않습니다. 실제 값이 들어간 `.env*` 파일은 Git에 포함하지 않습니다.

## 검증

```powershell
npm run typecheck
npm run lint
npm run build
```
