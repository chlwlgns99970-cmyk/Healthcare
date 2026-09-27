# K-FIND 자동 업데이트 설계 및 제한

확인일: 2026-09-27

- 공식 다운로드: https://various.foodsafetykorea.go.kr/nutrient/general/down/list.do
- 공식 OpenAPI 안내: https://various.foodsafetykorea.go.kr/nutrient/industry/openApi/info.do
- 공공데이터포털 API: https://www.data.go.kr/data/15127578/openapi.do
- 번들 기준일: 2026-08-28

앱은 WorkManager unique periodic work로 약 7일마다 네트워크 연결 시 공식 K-FIND 버전을 확인한다.
앱 시작 시에도 unique one-time work를 예약하므로 같은 작업이 중복 실행되지 않는다.

공식 다운로드는 현재 소속·부서·기관 유형·활용 목적 입력을 요구하고, OpenAPI는 활용 신청 후
서비스 키가 필요하다. 앱은 개인정보를 꾸며 제출하거나 서비스 키를 APK에 넣거나 HTML을
스크래핑하지 않는다. 따라서 공개 JSON 메타데이터 확인은 자동화하지만 실제 파일은 현재
검증 번들 스냅샷을 유지한다. 새 버전이 보이면 설정에 인증 필요 상태를 사실대로 표시한다.

`FoodDataUpdateSource`가 추후 인증 없는 안정적인 공식 machine-readable payload를 제공하면,
후보 행은 버전·필수값·중복 code/id·행 급감·malformed 비율·선택적 SHA-256을 먼저 검사한다.
모든 검증을 통과한 경우에만 Room transaction에서 upsert한다. 실패·프로세스 종료 시 기존 DB가
그대로 남고, `MealRecord`는 저장 당시 kcal/탄단지 snapshot을 가지므로 과거 기록은 바뀌지 않는다.
