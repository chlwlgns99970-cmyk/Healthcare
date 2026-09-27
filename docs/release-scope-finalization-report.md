# Healthcare 출시 범위 확정 및 보완 QA 보고서

작성일: 2026-09-17 (Asia/Seoul)

## 1. 최종 상태

`STATUS: COMPLETE`

출시 요구사항으로 확인된 기록 편집을 구현·검증했고, 접근성 semantics, 실기기 TalkBack, 정확히 360 dp로 제한한 실기기 Compose 렌더링, CameraX 백그라운드 복귀까지 완료했다. 실제 LED 발광은 승인 기준에 포함되지 않아 수행하지 않았고, 출처·서명·해시가 검증된 v2 QA APK가 없어 실제 APK 업데이트 검수는 조건에 따라 수행하지 않았다.

## 2. 기록 편집 요구사항 포함 여부

포함된다. `.agent/plan.md`는 Food History Management에 날짜별 기록 조회·편집·삭제를 명시하고, 완료 기준에도 Meal의 추가·편집·삭제를 요구한다.

## 3. 기록 편집 구현 및 결과

- 기존 History adaptive detail에 편집 진입점과 편집 상태를 추가했다. 별도 top-level route는 만들지 않았다.
- 음식 이름, 칼로리, 식사 유형, 날짜, 시간, 섭취량, 단위, 메모를 수정할 수 있다.
- 기존 MealRecord ID, createdAt, source, 사진 메타데이터를 유지하고 updatedAt만 갱신한다.
- 날짜 변경 시 기존 날짜와 새 날짜 합계가 Room Flow로 재계산된다.
- 저장 중 재입력을 막고, 유효성 검사 또는 저장 실패 시 기존 레코드를 유지한다.
- 실기기 QA에서 `EDITQA` 300 kcal를 `EDITQA2` 450 kcal로 수정했다. 목록은 1건을 유지했고, force-stop 후에도 수정값과 1건 상태가 보존됐다.

## 4. 실제 수정한 파일

- `app/src/main/java/com/example/healthcare/ui/viewmodel/HistoryViewModel.kt`
- `app/src/main/java/com/example/healthcare/ui/screens/HistoryScreen.kt`
- `app/src/main/java/com/example/healthcare/ui/screens/WellnessManualRecordScreen.kt`
- `app/src/main/java/com/example/healthcare/ui/components/WellnessComponents.kt`
- `app/src/main/java/com/example/healthcare/ui/screens/DashboardScreen.kt`
- `app/src/main/java/com/example/healthcare/ui/screens/SettingsScreen.kt`
- `app/src/test/java/com/example/healthcare/HistoryEditViewModelTest.kt` (신규)
- `app/src/androidTest/java/com/example/healthcare/HistoryEditUiTest.kt` (신규)
- `app/src/androidTest/java/com/example/healthcare/CompactWidthUiTest.kt` (신규)
- `app/src/androidTest/java/com/example/healthcare/EnergyUiTest.kt`
- `app/src/androidTest/java/com/example/healthcare/QaRuntimeUiTest.kt`
- `docs/ui-screen-inventory.md`
- `docs/ui-runtime-qa-report.md`
- `docs/release-scope-finalization-report.md` (신규)

이 범위에서 Room schema/version, migration, dependency, applicationId, 계산식, CameraX 구현, Repository, 백엔드는 변경하지 않았다. 이전 QA 테스트 호스트 보완인 `app/build.gradle.kts`는 그대로 유지한 채 최종 계측 테스트로 검증했다.

## 5. 접근성 semantics 변경

- 공통 section/card 제목과 Dashboard의 에너지·체중 환산 제목에 heading semantics를 적용했다.
- BMR, CUSTOM PAL, 활동 수준, 저장 오류를 해당 입력에 연결되는 error semantics로 노출했다.
- 활동 수준과 목표 모드의 선택 상태는 Material selectable semantics로 유지하고 계측 테스트로 확인했다.
- 장식 아이콘에는 불필요한 contentDescription을 추가하지 않았다.

## 6. 실제 TalkBack 결과

- Samsung TalkBack 서비스가 `FEEDBACK_SPOKEN`으로 실제 bound된 상태를 확인했다.
- CUSTOM PAL 1.39 오류가 표시된 Settings에서 14단계 순차 탐색을 수행했다.
- BMR 1,500 kcal, 예상 유지 2,325 kcal, 오늘 섭취 2,625 kcal, 오늘 초과 300 kcal가 표시된 Dashboard에서 30단계 순차 탐색을 수행했다.
- 모든 제스처 직전에 전면 앱을 확인했고 접근성 포커스는 QA 창에 유지됐다. heading/error/selected와 7일·30일 읽기 구조는 같은 빌드의 계측 semantics 테스트와 병행 확인했다.
- 다른 앱 알림이 섞일 수 있는 전역 이벤트 수집 방식은 즉시 폐기했으며 결과에 사용하지 않았다. 음성은 개인정보 보호상 녹음·전사하지 않았다.
- 종료 후 TalkBack은 원래 상태(`enabled_accessibility_services=null`, `accessibility_enabled=0`, bound service 없음)로 복구됐다.

## 7. 360 dp 결과

별도 360 dp AVD/system image는 없었다. Samsung SM-S948N 실기기에서 Compose 콘텐츠를 정확히 `360.dp × 800.dp`로 제한해 Dashboard 기본·초과 상태, Settings/CUSTOM PAL, 기록 목록·상세·편집, 사진 참고 수동 입력, top-level navigation을 실제 렌더링했다. 카드·긴 문구·수치/단위·Chip·저장 버튼·Bottom Navigation 검사가 3/3 통과했다. 이는 360 dp 물리 기기 전체 창이 아니라 실제 기기에서의 정확한 폭 제약 렌더링이라는 점을 구분한다.

## 8. CameraX 백그라운드 복귀 결과

PASS. QA CameraX 화면을 준비한 뒤 사용자가 직접 Home으로 이동하고 Healthcare QA로 복귀했다. 사용자가 미리보기 재개와 크래시·중복 촬영 없음에 대해 `복귀 정상`을 확인했다. 개인정보 보호 규칙에 따라 Home/최근 앱은 자동 조작하지 않았다.

## 9. 실제 플래시 LED 발광 결과

미실행. 플래시 켜기·끄기 상태 전환은 기존 검수에서 통과했으나, 실제 LED 발광은 최종 프로젝트 요구사항 또는 출시 승인 필수 항목으로 확인되지 않았다. 따라서 사용자 육안 검수를 임의로 추가하지 않았다.

## 10. 실제 v2 QA APK 업데이트 결과

미실행. workspace에서 출처, SHA-256, 동일 applicationId, 서명 호환성을 모두 입증할 수 있는 이전 v2 QA APK를 찾지 못했다. `MigrationTestHelper`의 v2→v3 및 MealRecord/CalorieGoal 보존 통과는 유효하지만 실제 APK `install -r` 업데이트 결과와 구분한다.

## 11. 실행한 테스트

- `testQaUnitTest`: 51/51 PASS, 실패·오류·건너뜀 0
- `connectedQaAndroidTest`: 15/15 PASS, 실패·오류·건너뜀 0
- 계측 구성: migration 3, 360 dp 3, energy semantics/UI 3, photo processor 3, history edit UI 1, runtime flow 1, context 1
- `assembleQa`, `assembleQaAndroidTest`: PASS
- 실제 기록 편집 및 force-stop 유지: PASS
- 실제 TalkBack 포커스 탐색: Settings 14단계, Dashboard 30단계 PASS
- CameraX 사용자 감독 백그라운드 복귀: PASS

## 12. Lint 결과

`lintQa`: 오류 0, 기존 warning 48. 이번 변경으로 신규 lint 오류 또는 치명 warning은 확인되지 않았다.

## 13. 빌드 결과

QA APK와 QA AndroidTest APK 빌드 성공. 최종 계측 테스트는 Samsung SM-S948N / Android 16(API 36)에서 QA 패키지만 대상으로 실행했다. 제품 패키지용 `connectedDebugAndroidTest`는 실행하지 않았다.

## 14. QA APK 경로

`C:/Users/young/AndroidStudioProjects/Healthcare/app/build/outputs/apk/qa/app-qa.apk`

## 15. QA APK SHA-256

`279CF183C592004D07755DE818BB8D8F7BE21391C3FBD7D5F42EA2F8B6E14936`

## 16. 제품 앱 보호 결과

- 제품 package: `com.example.healthcare`, versionCode 1, versionName 1.0
- firstInstallTime 전·후: `2026-09-15 21:34:39`로 동일
- lastUpdateTime: `2026-09-15 21:34:39`
- 제품 앱에 instrumentation, 실행, uninstall, `pm clear`, run-as, DB/캐시 조회·복사, 테스트 데이터 삽입을 수행하지 않았다.
- 최종 자동 조작과 조회의 전면 앱은 `com.example.healthcare.qa`로 확인했다.

## 17. 테스트하지 못한 항목과 이유

- 실제 LED 발광: 승인 필수 범위가 아니므로 조건에 따라 미실행
- 실제 v2→v3 APK 업데이트: 신뢰 가능한 v2 QA APK 부재
- 독립 360 dp 물리 기기/AVD 전체 창: 설치된 system image/AVD 부재. 대신 실기기 정확 폭 제약 렌더링을 수행함
- TalkBack 음성 녹음·자동 전사: 개인정보 보호를 위해 미수행. 실제 서비스와 접근성 포커스, 동일 빌드 semantics 테스트로 검수함

## 18. 남아 있는 위험 요소

- 360 dp 검수는 독립 기기 창이 아니라 실기기 내 정확 폭 제약이므로 제조사별 system UI/inset 차이는 별도다.
- 실제 LED 하드웨어 발광은 검수하지 않았으므로 플래시가 향후 출시 필수 조건으로 바뀌면 사용자 육안 확인이 필요하다.
- 신뢰 가능한 과거 v2 QA APK가 확보되면 실제 서명 호환 `install -r` 경로를 별도로 검수할 수 있다.
- 체중 변화 값은 7,700 kcal/kg 기반 단순 환산이며 개인별 실제 체중 변화·수분 변동·의료 판단과 다를 수 있다는 기존 안내를 유지한다.
