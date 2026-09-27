STATUS: PRIVACY_GUARD_TRIGGERED

# Healthcare QA 격리 및 실기기 검수 보고서

작성일: 2026-09-15 (Asia/Seoul)

## 완료 보고

| 번호 | 항목 | 결과 |
| ---: | --- | --- |
| 1 | 최종 상태 | `PRIVACY_GUARD_TRIGGERED`. QA 격리, 자동 테스트, 기록 UI·Room 검수는 완료했으나 CameraX 단계 시작 직전에 전면 패키지가 QA가 아닌 상태로 바뀌어 자동 조작을 중단했다. |
| 2 | 기존 앱 applicationId | `com.example.healthcare` |
| 3 | QA 앱 applicationId | `com.example.healthcare.qa` |
| 4 | QA 빌드 구현 방식 | `qa` build type이 `debug`를 상속하고 `applicationIdSuffix = ".qa"`, `versionNameSuffix = "-qa"`, `isDebuggable = true`, `matchingFallbacks = debug`를 사용한다. 최종 `testBuildType`은 `qa`라 제품용 `connectedDebugAndroidTest` 태스크가 생성되지 않는다. |
| 5 | 실제 수정한 파일 | `app/build.gradle.kts`, `app/src/qa/res/values/strings.xml`, `app/src/androidTest/java/com/example/healthcare/ExampleInstrumentedTest.kt`, `app/src/androidTest/java/com/example/healthcare/QaRuntimeUiTest.kt`, `app/src/main/java/com/example/healthcare/ui/viewmodel/AddRecordViewModel.kt`, `app/src/test/java/com/example/healthcare/PhotoManualRecordViewModelTest.kt`, 이 보고서와 QA 스크린샷 3개 |
| 6 | FileProvider authorities 검토 | Manifest에 Provider/FileProvider/authorities가 없다. CameraX는 앱 캐시의 파일에 직접 쓰므로 하드코딩 authority 충돌 대상이 없다. QA와 제품은 applicationId별 샌드박스가 분리된다. |
| 7 | 기존 앱과 QA 앱 동시 설치 | PASS. 제품과 QA APK가 동시에 설치된 경로를 확인했다. QA 테스트 APK는 검수 데이터 생성 후 제거했고 QA 앱은 유지했다. |
| 8 | 기존 앱 firstInstallTime 전후 | 전 `2026-09-15 21:34:39`, `connectedQaAndroidTest` 후 `2026-09-15 21:34:39`로 동일하다. |
| 9 | 기존 앱 제거·초기화 미실행 | 이번 작업에서는 제품 패키지에 `uninstall`, `pm clear`, `clearPackageData`, instrumentation을 실행하지 않았다. 제품 내부 데이터·DB·캐시도 읽거나 복사하지 않았다. |
| 10 | QA 자동 테스트 | QA 단위 테스트, QA Lint, QA APK/AndroidTest APK 빌드, `connectedQaAndroidTest`를 실행했다. 최종 계측 결과는 7/7 PASS다. |
| 11 | QA 실기기 | Samsung SM-S948N, Android 16 / API 36, 1440x3120, 600 dpi, 약 384 dp |
| 12 | 키보드 검수 | PASS(계측). 긴 한국어 메모 입력 뒤 스크롤해 저장 버튼에 접근했고, Back으로 IME를 닫은 뒤 음식명·메모 값 유지가 확인됐다. Compose 루트 캡처는 개인정보 보호를 위해 IME/상태바를 제외하므로 키보드 자체가 보이는 사진은 저장하지 않았다. |
| 13 | 수동 기록 저장 | PASS. QA UI에서 가상 기록 5건을 저장했고 대시보드와 내역에 반영됐다. |
| 14 | 저장 전후 오늘 총섭취량 | 500 kcal 기록 저장 직전 `1,250 kcal`, 저장 후 `1,750 kcal`; 목표 초과 기록까지 최종 `2,150 kcal` |
| 15 | 중복 저장 | PASS(단위 테스트). `saveRecord`를 연속 두 번 호출해도 `isSaving` 가드로 한 건만 저장됨을 확인했다. 실제 사람의 초고속 다중 탭은 별도 수동 검수하지 않았다. |
| 16 | 긴 음식명 | PASS. `닭가슴살과 현미밥을 곁들인 매콤한 구운 채소 샐러드`가 카드에서 2줄로 정리되고 상세 화면에서 전체 표시됐다. |
| 17 | 긴 메모 | PASS. 가상 긴 메모가 입력값으로 유지되고 상세 화면에서 잘림 없이 표시됐다. |
| 18 | 기록 목록·상세·수정·삭제 | 목록·상세·삭제 PASS. 400 kcal 기록 삭제 후 합계가 `1,750 kcal`로 갱신됐고 재추가 후 `2,150 kcal`가 됐다. 현재 제품에 기록 수정 UI가 없어 수정은 해당 없음이다. |
| 19 | CameraX | 미완료. QA 카메라 권한만 부여했으며 CameraX 화면을 덤프하거나 탭하기 직전 개인정보 보호 가드가 발동해 프리뷰·셔터·플래시·재촬영을 실행하지 않았다. |
| 20 | 사진 참고 기록 | 실제 CameraX 기반 흐름은 미완료. 기존 단위 테스트에서는 촬영 경로를 수동 작성 상태로 전환할 때 자동 분석 저장소를 호출하지 않고 MANUAL 한 건을 저장하는 흐름이 통과했다. |
| 21 | 임시 사진 삭제 | 코드/테스트 PASS, 실제 CameraX 파일은 미검증. 관리 임시 파일 삭제, 사진 수동 기록 저장 후 삭제, 사진 흐름 취소 후 삭제 테스트가 통과했다. |
| 22 | force-stop 후 Room 유지 | PASS. QA 앱만 force-stop한 뒤 제거·초기화 없이 콜드 스타트했고 `2,150 kcal`, `기록 5건`이 유지됐다. |
| 23 | 화면 회전 | Activity 재생성 후 `2,150 kcal` 유지 PASS. 실기기 물리 회전 중 입력 상태와 사진 URI 복원은 개인정보 가드 이후라 미검증이다. |
| 24 | 큰 글자 | 실기기 미검증. 기기 전역 설정을 변경하지 않았고 사용 가능한 에뮬레이터/system image가 없었다. |
| 25 | 360 dp | 런타임 미검증. 360 dp Compose Preview 코드는 컴파일됐지만 설치된 에뮬레이터/system image가 없어 실제 렌더링은 수행하지 않았다. |
| 26 | TalkBack 또는 semantics | Compose semantics 코드 검토 완료: 주요 동작 아이콘에 설명이 있고 FilterChip 선택 상태가 semantics에 노출된다. 실제 TalkBack 음성·읽기 순서는 미검증이다. |
| 27 | Light/Dark | 이전 검수에서 Dashboard Light/Dark가 확인됐다. 이번 QA 캡처에서는 Dark의 기록 추가, 대시보드, 상세 화면이 정상 표시됐다. QA의 전 화면 Light 재검수는 수행하지 않았다. |
| 28 | 개인정보 보호 가드 | 작동함. 전면 package가 QA와 정확히 일치하지 않아 `PRIVACY_GUARD_TRIGGERED`를 기록했고 이후 UI 덤프·탭·키 입력·스크린샷·카메라 조작을 실행하지 않았다. |
| 29 | 잘못된 캡처 삭제 | 이번 작업에서는 잘못된 캡처가 생성되지 않았다. 가드가 캡처 전에 차단했다. |
| 30 | 단위 테스트 | Debug 26/26 PASS, QA 26/26 PASS. 백엔드 38 PASS, 1 integration SKIP, 기존 경고 3개다. |
| 31 | instrumentation | `connectedQaAndroidTest` 7/7 PASS. 제품 패키지 대상 instrumentation은 실행하지 않았다. 구성 중간의 실패는 QA 테스트 선택자 보정과 실제 저장 폼 상태 결함 발견 과정이었고 최종 회귀는 0 failure다. |
| 32 | Lint | Debug 0 error / 기존 warning 48, QA 0 error / 기존 warning 48 |
| 33 | 빌드 | `assembleDebug`, `assembleDebugAndroidTest`, `assembleQa`, `assembleQaAndroidTest` PASS. 최종 계측 대상은 QA로 고정돼 있다. |
| 34 | QA APK 경로 | `C:/Users/young/AndroidStudioProjects/Healthcare/app/build/outputs/apk/qa/app-qa.apk` |
| 35 | QA APK SHA-256 | `C342C7E57BAB76C429812125B6CF9CBD1ABBBB3B41D6F36D7D221129E20CC7DE` |
| 36 | 스크린샷 경로 | `docs/ui-qa/screenshots/qa-runtime/` 아래 3개. 모두 QA Compose 루트와 가상 데이터만 포함하며 상태바·알림·다른 앱은 포함하지 않는다. |
| 37 | QA 보고서 경로 | `docs/ui-runtime-qa-report.md` |
| 38 | 제품 코드 변경 | 있음. QA에서 두 번 재현된 저장 성공 후 입력 상태 잔존 결함을 고치기 위해 `AddRecordViewModel`에서 성공 직후 `AddRecordUiState()`로 폼만 초기화했다. Room schema, Repository, 저장 계산, UI 레이아웃, 백엔드, dependency는 변경하지 않았다. |
| 39 | 테스트하지 못한 항목 | CameraX 실제 프리뷰/촬영/플래시/재촬영/취소, 실제 사진 참고 기록, 물리 회전 중 입력·사진 상태, 큰 글자, 360 dp 런타임, 실제 TalkBack. 카메라 항목은 가드 발동, 나머지는 안전한 실행 환경 부재 때문이다. |
| 40 | 남은 위험 요소 | 과거 제품 패키지 재설치 이전 데이터 보존은 계속 확인 불가이며 삭제 가능성이 있다. 이번 작업은 그 데이터를 복구·검증하지 않았다. CameraX 실물 흐름과 실제 TalkBack은 사용자가 QA 앱을 직접 전면에 둔 감독 환경에서 추가 검수가 필요하다. |

## 발견 및 수정한 실제 결함

QA 앱을 신선하게 설치한 동일 조건에서 두 차례, 첫 기록 저장 뒤 추가 화면을 다시 열면 이전 `AddRecordUiState`가 남아 두 번째 입력이 이어 붙는 현상이 재현됐다. Activity 범위 ViewModel은 유지되지만 저장 성공 시 폼을 초기화하지 않았기 때문이다.

수정은 Room 저장 성공과 임시 사진 정리가 끝난 뒤 `_uiState`를 기본 상태로 되돌리는 한 줄이며, 다음 기록 입력만 새 폼으로 시작한다. 저장 데이터·계산·DAO·Repository에는 손대지 않았다. 전용 회귀 단위 테스트와 QA UI 계측 흐름으로 재검증했다.

## 자동 검증 요약

| 명령/검증 | 결과 |
| --- | --- |
| `testDebugUnitTest` | 26 passed |
| `testQaUnitTest` | 26 passed |
| `lintDebug` | 0 errors, 48 existing warnings |
| `lintQa` | 0 errors, 48 existing warnings |
| `assembleDebug` | PASS |
| `assembleDebugAndroidTest` | PASS(빌드만 수행, 제품 기기 테스트 미실행) |
| `assembleQa` | PASS |
| `assembleQaAndroidTest` | PASS |
| `connectedQaAndroidTest` | 7 passed on QA package only |
| 백엔드 `pytest` | 38 passed, 1 skipped, 3 existing warnings |

## QA 스크린샷

- `docs/ui-qa/screenshots/qa-runtime/keyboard-save-visible-384dp.png`
- `docs/ui-qa/screenshots/qa-runtime/dashboard-qa-data-384dp.png`
- `docs/ui-qa/screenshots/qa-runtime/history-detail-long-content-384dp.png`

세 파일은 테스트 내부에서 `com.example.healthcare.qa`와 Activity window focus를 확인한 뒤 Compose root만 캡처했다. 알림 영역, 다른 앱, 개인 사진, 계정 정보, 실제 식사 기록은 포함하지 않는다.

## 2026-09-17 출시 범위 후속 검수

이 절은 2026-09-15 당시의 결과를 지우지 않고, 이후 확정된 출시 기준과 재검증 결과를 기록한다. `.agent/plan.md`의 MVP 기준에 날짜별 기록 조회·수정·삭제가 명시돼 있어, 누락돼 있던 기록 수정 흐름을 기존 `HistoryRoute`의 adaptive detail 상태에 최소 범위로 추가했다. 별도 top-level route, Room schema, migration 또는 dependency는 추가하지 않았다.

- 기록 상세에서 편집 진입, 음식명·칼로리·식사 유형·날짜/시간·섭취량/단위·메모 수정 및 저장을 지원한다.
- 저장 시 기존 `MealRecord` ID, 생성 시각, source와 사진 메타데이터를 보존하고 수정 시각만 갱신한다. AI 결과를 사용자가 바꾼 경우 기존 편집 표시 규칙을 따른다.
- 같은 날짜와 다른 날짜로의 이동, 유효성 검사, 저장 실패 시 원본 보존, 중복 저장 방지를 단위 테스트로 검증했다.
- QA 앱에서 `EDITQA` 300 kcal 기록을 `EDITQA2` 450 kcal로 수정했다. 목록은 한 건을 유지했고 합계가 갱신됐으며, QA 앱 force-stop 후에도 수정 내용과 한 건 상태가 유지됐다.
- `connectedQaAndroidTest` 최종 결과는 Samsung SM-S948N / Android 16(API 36)에서 15/15 PASS, 실패·건너뜀 0이다. 제품 패키지 대상 instrumentation은 실행하지 않았다.
- 360 dp는 별도 기기/에뮬레이터가 아니라 실기기에서 Compose 콘텐츠 폭을 정확히 360 dp로 제한해 Dashboard, Settings/CUSTOM PAL, History 목록·상세·편집, 사진 참고 수동 입력, top-level navigation을 렌더링했다. 해당 계측 테스트가 통과했다.
- 주요 section heading, 선택 상태와 입력 오류를 Compose semantics로 보강하고 계측 테스트로 확인했다. 실제 TalkBack 서비스 기반 읽기 순서 검수는 후속 사용자 감독 단계로 남아 있다.
- 후속 빌드의 QA APK SHA-256은 `279CF183C592004D07755DE818BB8D8F7BE21391C3FBD7D5F42EA2F8B6E14936`이다.
- CameraX 실제 촬영의 기존 검수 결과는 유지한다. 앱을 배경으로 보냈다가 복귀하는 lifecycle 검수는 사용자가 직접 Home/복귀를 수행해야 하므로 자동화하지 않았다.

### 2026-09-17 감독 검수 추가 결과

- Samsung TalkBack 서비스를 실제로 활성화하고 `FEEDBACK_SPOKEN` 서비스가 bound된 상태에서 QA 앱만 전면에 둔 채 검수했다. CUSTOM PAL 1.39 오류가 보이는 Settings에서 14단계, BMR 1,500·유지 2,325·섭취 2,625·초과 300 kcal 상태의 Dashboard에서 30단계 순차 탐색을 수행했다. 모든 단계에서 접근성 포커스가 QA 창에 유지됐다. 항목의 heading/error/selected semantics는 `EnergyUiTest`와 함께 확인했다. 개인정보 보호를 위해 음성을 녹음하거나 자동 전사하지 않았다.
- 검수 종료 후 `enabled_accessibility_services=null`, `accessibility_enabled=0`, bound/enabled service 없음으로 TalkBack을 원래 꺼진 상태로 복구했다.
- CameraX 촬영 화면을 준비한 뒤 사용자가 직접 Home으로 이동하고 Healthcare QA로 복귀했다. 사용자가 미리보기 재개와 오류·종료 없음에 대해 `복귀 정상`을 확인했다. Home/최근 앱 조작은 자동화하지 않았다.

## 데이터 안전 판정

- QA Room 파일명은 제품과 같은 `healthcare_database`지만 applicationId별 앱 샌드박스가 달라 물리적으로 분리된다.
- 이번 작업에서 제품 패키지는 설치 확인과 `firstInstallTime` 확인만 수행했다.
- 제품 패키지에는 instrumentation, uninstall, `pm clear`, DB/캐시 열람을 실행하지 않았다.
- 과거 작업에서 이미 변경된 제품 설치 시각과 그 이전 데이터의 상태는 복구됐다고 단정하지 않는다.
- 가드 발동 뒤 기기 자동 조작을 중단했으며, 보고서에는 비-QA 전면 화면의 패키지명이나 내용이 없다.
