# 과일 검색 / 기록 완료 navigation 검증

검증일: 2026-09-30 (Asia/Seoul). 기존 미커밋 작업을 보존하고 아래 수정만 추가했다.

## 1. STATUS

BLOCKED — 코드 보강, 관련 테스트, QA 빌드 및 Samsung 업데이트 설치는 완료했다. 공개 1.0.5에서 발생했다는 원래 navigation 사례의 정확한 진입 경로와 원인은 아직 확인되지 않았다. 이를 확인했다고 단정하지 않는다.

## 2. 과일 검색 실제 원인

공개 1.0.5 소스(HEAD)의 `FoodSearchPolicy`에서는 원본 이름 `사과_껍질 포함_생것`과 사과 가공제품 모두 부분일치 rank 20이 된다. 같은 rank에서는 `sourceTieBreakRank`가 제품 0, 기본 음식 2로 제품을 앞세운다. 화면은 이 결과를 다시 제품/프랜차이즈/기본 종류별로 묶어 출력했다. `과일` 카테고리는 별도로 USDA 원본을 우선 정렬하므로 전체와 카테고리 결과가 다르게 보일 수 있었다.

이번 작업 시작 시 개발 작업본에는 이미 basic alias rank 5와 canonical grouping이 추가되어 있었다. 이 기존 개선을 보존하면서 기본 과일의 첫 source alias를 표시 이름 및 canonical key로 연결했다. `사과`와 같은 이름의 제품도 일반 사과가 대표인 그룹에 보존한다. 주스/젤리/과자처럼 다른 음식은 독립 그룹으로 유지한다.

DAO의 generic 조회는 LIMIT 80 전에 basic exact alias를 우선하도록 보강했다. 브랜드가 별도 열에 있는 제품도 `OO 사과주스`처럼 검색할 수 있도록 제품 조회와 rank에 브랜드+이름 exact 조건을 추가했다.

검색은 job cancel 및 `collectLatest`를 사용하며, debounce나 검색마다 실행하는 async asset lookup은 없다. 현재 번들에는 기본 과일 8종이 존재하고 작업 전 DAO 조회에서도 6종이 조회되었다. 이번 검증에서 비동기 갱신 지연을 원인으로 확인하지 못했다.

## 3. 과일 검색 결과

Samsung의 실제 QA Room 데이터와 repository 및 canonical 그룹을 검증했다.

| 검색어 | 전체 대표 기본 음식 순위 | 과일 카테고리 순위 | 대표 kcal / 기준량 |
|---|---:|---:|---|
| 사과 | 1 | 1 | 52 kcal / 100g |
| 바나나 | 1 | 1 | 89 kcal / 100g |
| 딸기 | 1 | 1 | 32 kcal / 100g |
| 포도 | 1 | 1 | 69 kcal / 100g |
| 배 | 1 | 1 | 42 kcal / 100g |
| 복숭아 | 1 | 1 | 39 kcal / 100g |

모두 USDA-SR-LEGACY 기본 음식이다. repository 결과를 grouping 후 펼친 ID 집합 및 개수는 grouping 전과 같았다. 제품 데이터 CSV 및 원본 FoodItem은 변경하지 않았다.

## 4. `과일` 검색어

기존 `|과일|과일류|` alias로 지원한다. 전체에서 `과일` 입력 시 상위 8개 대표 그룹은 사과, 바나나, 딸기, 포도, 배, 복숭아, 오렌지, 수박의 기본 음식이다. 새 category intent 특수 규칙은 추가하지 않았다.

## 5. Navigation 실제 원인

사용자는 피드백이 공개 제품 앱 1.0.5에서 발생했다고 확인했다. 그러나 공개 소스와 작업 전 개발 소스 모두 `onRecordSaved`에서 `backStack.clear()` 후 `DashboardRoute`를 추가하고 있었다. 단순히 Home을 push하고 Record를 남기는 구현은 확인되지 않았다.

로컬 배포 APK `dist/v1.0.5/today-mwo-meokji-v1.0.5.apk`의 SHA-256은 배포 SQL에 기록된 `6337EE0EB7C69AB9C5E9A6C84EC7CB4EEC6D665D6E264F61E5529FA5B0C62DCE`와 일치한다. 해당 APK의 DEX를 읽기 전용으로 확인했으며, `HealthcareApp`의 식사 저장 callback에도 `NavBackStack.clear()` → `DashboardRoute` → `NavBackStack.add()`가 들어 있다.

따라서 제보의 정확한 원인을 확인했다고 보고할 근거는 없다. 원래 기록 방식(검색/직접입력/사진/바코드), 홈 복귀 방법 및 이후 경로를 추가로 확인 중이다. 제품 앱 데이터를 변경하여 재현하는 작업은 수행하지 않았다.

## 6. 수정 후 흐름

실제 Navigation 3의 `MutableList<NavKey>`를 사용하며 NavController/popUpTo API는 사용하지 않는다.

- 작업 전 확인된 소스: `[DashboardRoute, AddRecordRoute]` → 성공 callback → `[DashboardRoute]` (전체 clear 후 root 추가).
- 수정 후: `[DashboardRoute, AddRecordRoute]` → 성공 callback → 기존 `DashboardRoute`를 남기고 마지막 `AddRecordRoute`만 제거 → `[DashboardRoute]`.
- 이후 Android 시스템 back: Activity 종료. 실기기에서 `Lifecycle.State.DESTROYED`를 확인했으며 기록 화면이 재등장하지 않았다.
- `[DashboardRoute, RecommendationsRoute, AddRecordRoute]`는 저장 후 `[DashboardRoute, RecommendationsRoute]`로 복귀한다.
- 기록 탭 root `[AddRecordRoute]`는 완료 후 Home root 하나만 만든다.
- active 마지막 route가 AddRecord가 아니면 완료 callback을 무시한다. 반복 호출로 부모 화면을 지우거나 Home을 중복 생성하지 않는다.

## 7. 다른 navigation 보호

- History detail → 상단 back → 기록 목록: 실제 화면 테스트 통과.
- History edit → 저장 → 기록 목록: 실제 repository 변경 값과 화면 복귀 확인.
- Home 검색 취소 → 상단 back 및 시스템 back → Home: 통과. 이어서 시스템 back으로 Activity가 종료되어 중복 Home이 없음을 확인.
- Quick record → 상단/system back → 음식 검색 → Home: 실제 화면 테스트 통과. 기존 음식 선택 변경 동작을 유지한다.
- 저장 실패 fake repository: `[DashboardRoute, AddRecordRoute]`, 음식 ID/이름/먹은 양/kcal가 유지되고 재시도 가능. 재시도 후 1회 저장/1회 성공 처리 확인.
- 기존 success callback 방식과 `isSaving` 중복 클릭 방지를 유지한다. 재수집되는 Flow success event는 이 기록 flow에 없다.

## 8. 수정 파일

이번 작업에서 추가 또는 수정한 파일만 나열한다.

- `app/src/main/java/com/example/healthcare/domain/FoodSearchPolicy.kt`: 기본 과일의 단순 표시 이름/canonical 그룹, canonical 및 구체적 브랜드 exact 우선순위.
- `app/src/main/java/com/example/healthcare/data/dao/FoodItemDao.kt`: basic alias를 LIMIT 전에 조회, 브랜드+제품명 조회.
- `app/src/main/java/com/example/healthcare/ui/RecordNavigation.kt`: 활성 기록 flow만 제거하는 idempotent 완료 처리.
- `app/src/main/java/com/example/healthcare/ui/HealthcareApp.kt`: 완료/취소 callback을 위 helper에 연결, 부모 유지, 성공 시 pending 문맥 정리.
- `app/src/main/java/com/example/healthcare/ui/screens/AddRecordScreen.kt`: 부모에서 진입한 검색의 상단/system 취소를 같은 처리로 연결.
- `app/src/test/java/com/example/healthcare/FruitSearchPolicyTest.kt`: 6종, 가공제품, 브랜드 검색, 과일 alias, grouping/데이터 보존.
- `app/src/test/java/com/example/healthcare/RecordNavigationTest.kt`: Home/기록 탭/추천/리포트/History 부모, 중복 callback 보호.
- `app/src/test/java/com/example/healthcare/PhotoManualRecordViewModelTest.kt`: 실패 입력 유지, 재시도 및 중복 저장 방지 검증 보강.
- `app/src/androidTest/java/com/example/healthcare/FruitRecordRegressionUiTest.kt`: 실기기 실제 앱/Room/History/navigation 및 crowded 조회 테스트.
- `app/src/androidTest/java/com/example/healthcare/FruitRecordLayoutFailureUiTest.kt`: 360dp/큰 글자, 다른 제품 펼침, fake 저장 실패/재시도.
- 이 보고서.

기존 사용자 미커밋 변경을 별도로 되돌리거나 commit하지 않았다.

## 9. 관련 Unit Test

`testQaUnitTest`에 아래 5개 class만 필터링하여 실행했다. **60개 통과, 최종 실패 0개.**

| Class | 테스트 수 | 실패 |
|---|---:|---:|
| FoodSearchPolicyTest | 14 | 0 |
| FruitSearchPolicyTest | 8 | 0 |
| RecordNavigationTest | 5 | 0 |
| PhotoManualRecordViewModelTest | 28 | 0 |
| HistoryEditViewModelTest | 5 | 0 |

명령: `gradlew.bat :app:testQaUnitTest --tests com.example.healthcare.FoodSearchPolicyTest --tests com.example.healthcare.FruitSearchPolicyTest --tests com.example.healthcare.RecordNavigationTest --tests com.example.healthcare.PhotoManualRecordViewModelTest --tests com.example.healthcare.HistoryEditViewModelTest`.

## 10. Targeted Instrumentation

Samsung **SM-S948N**, Android **16 / API 36**, serial `R5KL20HFPAK`에서 새 테스트 2개 class의 8개 항목만 실행했다. **최종 8개 통과, 미해결 실패 0개.**

초기 실행에서 테스트 fixture의 unique source code, 실제 버튼 description, 입력 field와 음식명의 동명 선택자를 수정했다. 큰 글자 검증에서는 Text intrinsic 폭 반올림 flag 대신 실제 glyph bounds, ellipsis 및 요소 간 간격을 검증했다. 캡처에서도 잘림/겹침이 없음을 확인했다. 7개 최종 성공 후 수정한 layout 테스트 1개만 재실행하여 통과했다.

기기는 3버튼 navigation(`navigation_mode=0`) 상태여서 edge gesture back은 검증하지 않았다. 시스템 back과 상단 back은 검증했다. 기기 navigation 설정은 변경하지 않았다.

로그: `app/build/qa-preview/instrumentation-log-2.txt`, `app/build/qa-preview/layout-final-log.txt`, `app/build/qa-preview/cancel-final-log.txt`.

## 11. QA

- `assembleQa`: 성공.
- `assembleQaAndroidTest`: 성공.
- Samsung QA APK `adb install -r -t`: Success. 기존 앱 데이터 제거/초기화 없이 업데이트.
- QA applicationId: `com.example.healthcare.qa`.
- 테스트 APK applicationId: `com.example.healthcare.qa.test`.
- 실기기는 QA 음식 검색 / 전체 / 사과 결과 상태로 준비했다. 자동 테스트로 바나나, 홈 빠른 기록, 저장 후 홈 복귀도 확인했다.
- 직접 확인 순서: 현재 사과 기본 결과 → 검색어 바나나로 변경 → 홈 → 아침 → 사과 검색/선택 → 먹은 양 100g 입력 → 아침에 기록 → 홈 확인 → 시스템 back.
- 테스트가 추가한 식사 기록은 각 테스트의 finally에서 삭제했다. 기존 기록은 삭제하지 않았다.

캡처는 모두 build output 아래에 있으며 Git commit 대상이 아니다.

- `app/build/qa-preview/fruit-record-qa/all-apple.png`
- `app/build/qa-preview/fruit-record-qa/all-banana.png`
- `app/build/qa-preview/fruit-record-qa/home-after-record.png`
- `app/build/qa-preview/fruit-record-qa/search-360dp-font130.png`
- `app/build/qa-preview/ready-all-apple.png`

기존 사용자 소유 `artifacts/` 폴더는 수정하지 않았다.

## 12. DB

개발 DB **version 8 유지**, migration 추가 없음. 작업 전후 `AppDatabase.kt` 및 두 음식 CSV의 SHA-256이 같음을 확인했다.

- AppDatabase: `C8344DDB30BCFD3A9EE73DD8BCDFAD17E5C4A13D82CBD8E4713617DEAA182219`
- food_items.csv: `CCF6A9B2322AD5E75FE29F13FFB7C4BF719C2CCA5CD3B459F104D659231261B4`
- product_items.csv: `8984DC1C4CF881728D383EB4EAEA273B658E0886BD75A8DACB8CCAF8F8AD08B8`

## 13. 전체 검증

요청대로 전체 connectedQaAndroidTest, 전체 Lint, Release build는 실행하지 않았다. `git diff --check`는 통과했다.

## 14. 버전/배포

- versionCode **6** 유지.
- 제품 versionName **1.0.5** 유지, QA **1.0.5-qa**.
- GitHub Release / Supabase / Vercel 변경 **0**.
- 제품 package `com.example.healthcare`에 install/uninstall/clear/기록 입력 등의 쓰기 명령 **0**.
- 제품의 versionCode/versionName/lastUpdateTime은 작업 전후 동일 (`2026-09-29 23:17:20`).
- production 배포 변경 **0**.

## 15. 남은 문제

공개 1.0.5에서 제보된 원래 navigation 사례의 정확한 재현 경로 및 원인 확정이 남아 있다. QA에서는 요청된 기본 음식 검색, Home 빠른 기록 저장 후 back, 실패/취소/History 보호 검증이 모두 통과했다. 원래 원인을 확인하기 전에는 전체 요청을 COMPLETE로 단정하지 않는다.
