# 오늘 뭐먹지 1.0.6 최종 검증·배포 보고서

검증 기간: 2026-10-01~2026-10-02 (Asia/Seoul). 프로젝트: `C:\Users\young\AndroidStudioProjects\Healthcare`.

전체 회귀와 정식 공개 배포를 완료했다. 증거는 Git에서 제외되는 [release-1.0.6 build 폴더](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6)에 보관한다.

## 1. STATUS

`RELEASE COMPLETE`

필수 검증과 versionCode 7 / 1.0.6 APK·서명 확인, source main push, GitHub 공개 Release/asset, 인증 없는 재다운로드 SHA·크기 확인, Supabase latest 전환과 이전 기록·권한 보존, Vercel API·사이트 반영까지 완료했다. Samsung 실제 인앱 업데이트 결과는 11절에 구분한다.

## 2. 전체 회귀검증

| 항목 | 실행 / 성공 / 실패 / skip | 현재 확인 결과 |
|---|---:|---|
| 전체 QA Unit | 356 / 356 / 0 / 0 | 최종 전체 실행 PASS. errors 0 |
| 전체 connectedQaAndroidTest | 168 / 168 / 0 / 0 | Samsung SM-S948N, Android 16/API36. 수정 후 전체 재실행 PASS |
| API29 updater | 3 / 3 / 0 / 0 | 기존 관리형 `api29Updater` 재사용, 실제 GET_SIGNATURES fallback 포함 PASS |
| Room migration 관련 테스트 | 9 / 9 / 0 / 0 | DB8 대상 2개 클래스. 최종 connected 결과로 재확인 PASS |
| Recommendation audit | 292 templates / 292 unique stable IDs | 중복·누락 0, PASS |
| Image mapping | 292 / 292 | 중복·누락·빈 drawable 0, PASS |
| Theme audit | 292 / 292 | 중복·누락·영양 불일치·8개 테마 판정 불일치 0, PASS |

Unit은 44개 테스트 클래스의 실행 결과다. Android는 34개 클래스의 168개 테스트를 실행했다. 최초 전체 기기 실행은 167개 중 7개가 실패했다. 변경된 추천 UX에 맞춰 오래된 CTA 기대를 갱신하고 목표 fixture를 격리했다. 실제로 확인된 초기 저장 식단 검증 중 테마 선택 유실은 선택 즉시 반영·진행 중 생성 예약·이전 테마 결과 게시 방지·Back 즉시 반영으로 수정했다. 기존 실패 7개와 추가 회귀 1개의 관련 재검증 8/8 PASS 후 전체 168/168 PASS를 확인했다.

Migration 9개는 `AppDatabaseMigrationTest` 7개와 `RecommendationPreferenceMigrationTest` 2개다. v1→v8 전체 연결, v6→v7→v8, v7→v8, MealRecord/FrequentFood/즐겨찾기/음식 설정·제외/목표/에너지 이력/추천 데이터 보존을 검사한다. 신체정보의 SharedPreferences 호환성은 별도 `BodyProfileLegacyMigrationTest` 6개 Unit이 PASS했다. DB version은 8이며 제품 코드에 destructive fallback을 추가하지 않았다.

Audit의 기본 데이터 판정은 가볍게 96, 균형 있게 26, 든든하게 110, 다이어트 292, 벌크업 292, 건강하게 54, 치팅데이 292, 저속노화식 스타일 0이다. 이 수는 사용자 제외·취향·cycle·하루 목표 적용 전의 원본 메타데이터 판정이다. kcal 정보는 292개, 완전한 탄단지는 261개, 확인 가능한 전체 원재료는 36개이며, 알레르기 미확인 표시는 255개다. 미확인 값을 0이나 안전 판정으로 바꾸지 않는다.

근거: [최종 전체 Unit](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/full-unit-final-results.json), [최종 Samsung 전체 결과](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/connected-final-results.json), [API29 최종 결과](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/api29-final-results.json), [독립 audit](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/audit-results.json), [필수 조건 gate](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/gate-final.json), [전체 테스트 메서드 목록](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/test-method-inventory.json).

## 3. Lint

| Variant | errors | warnings | 기존 warnings | 신규 warnings |
|---|---:|---:|---:|---:|
| QA | 0 | 63 | 62 | 1 |
| Debug | 0 | 59 | 58 | 1 |

두 variant의 신규 경고는 동일한 `TodayMealPlanStore.kt`의 `UseKtx` 1건이다. `SharedPreferences.edit` 편의 확장 사용을 권하는 경고이며, 현재 구현은 `commit()` 성공 여부를 확인하여 저장 실패를 처리한다. 신규 Lint error 또는 치명적 warning은 확인되지 않았다. 최종 제품 수정 후 전체 QA/Debug Lint를 다시 실행해 같은 결과를 확인했다.

근거: [최초 Lint 비교](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/lint-results.json), [최종 Lint 결과](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/lint-final-results.json).

## 4. Release build

- 버전 변경 전·후 `assembleRelease`: 모두 exit code 0, `BUILD SUCCESSFUL`. 최종 1.0.6 빌드는 46초였다.
- 확인된 packageName: `com.example.healthcare`.
- 최종 Release signer SHA-256: `385693830FF4C9F9122A9DC5D992646A871AC82764439AEC1C1078BF55496CA8`. 기존 제품 signer와 일치한다.
- 최종 APK 버전: versionCode 7 / versionName 1.0.6.
- 최종 APK package/version/signer/실제 SHA/size 검증: PASS.

제품 keystore와 alias를 유지한다. 새 keystore 생성, 기존 파일 삭제, alias 변경, 비밀번호 출력 또는 commit은 수행하지 않는다.

근거: [최종 전체 회귀·Lint·Release 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/full-regression-final.txt), [최종 버전 Release build](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/final-release-build.txt), [최종 APK 검증](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/final-apk.json), [최종 signer](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/final-apk.json.signer.txt).

## 5. 최종 버전

| 항목 | 최종 Release 확인 | 결과 |
|---|---|---|
| versionCode | 7 | PASS |
| versionName | 1.0.6 | PASS |

회귀·audit·Lint·Release·signer 필수 조건 통과 후 버전을 변경했다. 변경 후 Gradle configuration과 Release build, package/version을 재확인했다. 버전 숫자 변경만을 이유로 전체 테스트를 반복하지 않았다.

## 6. APK

| 항목 | 최종 1.0.6 결과 |
|---|---|
| 경로 | [최종 APK](C:/Users/young/AndroidStudioProjects/Healthcare/dist/v1.0.6/today-mwo-meokji-v1.0.6.apk) |
| 파일명 | `today-mwo-meokji-v1.0.6.apk` |
| 파일 크기 | 128080912 bytes |
| SHA-256 | `0104AF47FA6DCD3B4F5376B6593C5D19910F9094B5A538B5581E78C82265F980` |
| packageName | `com.example.healthcare` |
| versionCode / versionName | 7 / 1.0.6 |
| signer | `385693830FF4C9F9122A9DC5D992646A871AC82764439AEC1C1078BF55496CA8` — 일치 PASS |

최종 APK의 실제 SHA와 크기를 GitHub asset, Supabase, Vercel metadata의 단일 기준으로 사용한다. 기존 1.0.5 APK의 값으로 대체하지 않는다.

## 7. Git

- 기존 branch: `main`.
- 작업 시작 HEAD: `f268a16bdb05addbb2b94721cd94b15042d820ed` (`Publish 1.0.5 release metadata`).
- Release source commit: `cef45dfe8cbaa37d899fbc4fc2012117526619b2`.
- Release commit message: `Release 1.0.6`.
- 정상 main push: PASS, remote와 commit 일치.
- source commit 전 `git diff --check`: PASS.
- 배포 metadata source/최종 보고서: `Publish 1.0.6 release metadata` 별도 commit으로 정상 main push했다. 정확한 후속 commit hash는 최종 응답과 로컬 Git 이력에 기록한다.

현재 프로젝트가 source of truth다. 기존 미커밋 기능·테스트 변경을 보호하며 clone/reset/clean/force push를 하지 않는다. 사용자 `artifacts/`, build output, 임시 캡처·로그, APK, keystore, secret은 source commit에서 제외한다. 기존 프로젝트는 Release source commit 다음에 Supabase metadata source를 별도 commit한 관례가 있다.

GitHub 기존 GCM 인증과 저장소 push 권한은 읽기 전용으로 확인했다. credential은 메모리에서만 사용하며 출력·파일 저장하지 않았다.

## 8. GitHub Release

| 항목 | 결과 |
|---|---|
| tag | `v1.0.6` |
| release URL | [오늘 뭐먹지 1.0.6](https://github.com/chlwlgns99970-cmyk/Healthcare/releases/tag/v1.0.6) |
| asset name | `today-mwo-meokji-v1.0.6.apk` |
| public asset size | 128080912 bytes |
| 공개 asset 재다운로드 SHA | `0104AF47FA6DCD3B4F5376B6593C5D19910F9094B5A538B5581E78C82265F980` — 로컬 일치 PASS |
| 공개 asset 재다운로드 size | 로컬 일치 PASS |

GitHub Release id 401084153, asset id 603509658. 기존 1.0.5 Release와 asset은 보존했다. draft 업로드 시 GitHub 임시 `untagged-...` URL을 확인했고, 공개 후 정식 v1.0.6 URL을 다시 검증했다. 서버 digest 확인과 별도로 인증 없는 공개 URL에서 APK 전체를 다시 다운로드했다. 재다운로드 gate가 통과한 뒤에만 Supabase latest를 전환했다.

근거: [공개 Release](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/github-release-published.json), [공개 파일 재검증](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/public-asset-verified.json).

## 9. Supabase

- 프로젝트: `chlwlgns99970-main` (`fjpbsooiojcrdgjgpltc`).
- 전용 schema/table: `today_mwo_meokji.app_releases`.
- 1.0.6 record: id 6, versionCode 7, `published`, `is_latest=true`. URL·SHA·크기·게시 시각은 공개 asset 기준과 일치한다.
- 기존 1.0.5는 `is_latest=false`로 보존했다. 1.0.4 및 1.0.2~1.0.5 전체 이전 행은 `is_latest` 외 모든 column이 동일하다. 총 release history 4→5행, Android latest는 v7 한 행이다.
- RLS=true 유지. anon/authenticated의 schema USAGE와 published SELECT만 유지했다. INSERT/UPDATE/DELETE/ALL 권한 추가 0건.
- columns/constraints/indexes/policies/privileges/RLS/schema usage 전후 동일. `public.app_releases`는 계속 없으며 public schema 변경 0건.

transaction 안에서 table lock, 기존 JSON snapshot, 새 release 등록, 단일 latest와 이전 모든 행 보존 assertion을 수행했다. 별도 읽기 전용 조회로 실제 반영과 권한 보존을 다시 확인했다.

근거: [등록 SQL](C:/Users/young/AndroidStudioProjects/Healthcare/supabase/migrations/20261002000100_publish_v1_0_6.sql), [배포 후 검증](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/supabase-publish-verified.json).

## 10. Vercel

| 항목 | 결과 |
|---|---|
| metadata versionCode / versionName | 7 / 1.0.6 |
| `apkUrl` | 공개 v1.0.6 APK URL 일치 |
| SHA-256 | `0104AF47FA6DCD3B4F5376B6593C5D19910F9094B5A538B5581E78C82265F980` — 일치 |
| fileSizeBytes | 128080912 — 일치 |
| 다운로드 사이트 | HTTP200, 최신 1.0.6 표시, 실제 v1.0.6 APK 버튼 링크 |
| production 재배포 | 0건 — 동적 조회로 자동 반영 |

[metadata API](https://today-mwo-meokji.vercel.app/api/releases/latest)와 [다운로드 사이트](https://today-mwo-meokji.vercel.app)는 Supabase published latest를 `cache:no-store`/`force-dynamic`으로 조회한다. API와 사이트 모두 최종 APK의 URL·SHA·크기와 일치하며 추가 deployment가 필요하지 않았다.

근거: [최종 metadata](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/vercel-metadata-after.json), [최종 사이트](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/vercel-site-after.json).

## 11. 실제 updater

- Samsung SM-S948N / Android16 제품 package: `com.example.healthcare`.
- 실제 시작 versionCode 6 / 1.0.5 → 최종 versionCode 7 / 1.0.6: PASS.
- 기존 앱의 설정→앱 정보→앱 업데이트→업데이트에서 1.0.6 안내와 다운로드 진행을 확인했다. 제품 APK의 `adb install`은 수행하지 않았다.
- Android 시스템 installer: package install-source에서 `installerPackageName`와 `initiatingPackageName`이 `com.google.android.packageinstaller`, `originatingPackageName`이 `com.example.healthcare`인 것을 확인했다. 설치 확인 버튼 화면은 캡처하지 않았으며, 설치 완료와 출처 정보로 시스템 installer 경로를 검증했다.
- firstInstallTime: `2026-09-21 00:10:35` 그대로 유지.
- lastUpdateTime: `2026-09-29 23:17:20` → `2026-10-02 00:19:03` 변경.
- 새 버전 정상 실행: PASS. 제품 앱 정보 화면의 1.0.6과 업데이트 재확인 결과 `현재 최신 버전을 사용 중이에요.`를 확인했다.
- 제품 uninstall·pm clear·데이터 초기화·DB 직접 접근·테스트 record 생성: 각각 0건.

실제 식사 기록과 제품 DB는 열거나 비교하지 않았다. 데이터 삭제 작업 없이 기존 package를 업데이트했으며, 사용자 DB의 내용 보존을 직접 조회했다는 의미로 보고하지 않는다.

근거: [실제 제품 업데이트 결과](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/product-update-verified.json), [앱 정보·최신 안내](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/product-update-final-labels.json).

## 12. Note9 / Android 10

- API29 관리형 환경: 기존 `api29Updater` 재사용. 최종 실행 3 / 성공 3 / 실패 0 / errors 0 / skip 0, PASS.
- 검증 메서드: `AndroidApkIdentityReaderTest#installedAndArchivePackageIdentityExposeMatchingSignerOnDeviceApi`, `#api29CompatibilityBranchUsesIntFlagsAndStillReadsBothSignerIdentities`, `#legacyGetSignaturesArchiveAndInstalledMatchModernIdentityOnApi29AndLater`.
- archive/installed identity, 현대 signer 조회, API29 int flags, legacy GET_SIGNATURES fallback과 정상 signer 일치를 확인하는 범위다.
- 실제 Galaxy Note9 실기기: 개발 환경에 없으므로 **미검증**. API29 emulator 결과로 실기기 완료를 주장하지 않는다.

구버전 Note9 updater가 계속 실패하는 경우 [다운로드 사이트](https://today-mwo-meokji.vercel.app)에서 최신 APK를 받아 기존 앱 삭제 없이 덮어쓰기 설치가 필요할 수 있다. 현재 문서의 이 안내는 실기기에서 그 실패를 재현했다는 뜻이 아니다.

## 13. 핵심 기능 회귀

아래 표는 사용자 요구사항과 최종 실제 실행된 테스트 메서드를 연결한다. 최종 전체 Unit 356/356, Samsung 기기 168/168, API29 updater 3/3에서 해당 검증이 PASS했다. 실제 제품 앱의 1.0.5→1.0.6 시스템 installer 업데이트도 별도로 PASS했으며 11절에 증거를 기록했다.

| 요구 기능 | 대표 Unit / 기기 테스트 메서드 | 최종 결과 |
|---|---|---|
| 기본 두부·달걀·계란·삶은 계란 | `FoodSearchPolicyTest#basicAliasRanksAheadOfBrandProductsForTofuAndEggQueries`; `CurrentUxImprovementUiTest#officialBasicFoodsAndCanonicalTunaGimbapGroupAreVisible` | Unit PASS / 기기 PASS |
| 사과·바나나·딸기·포도·배·복숭아 | `FruitSearchPolicyTest#apple`~`#peach`; `FruitRecordRegressionUiTest#allSixFruitQueriesAndCategoryIntentUseBasicRepresentatives` | Unit PASS / 기기 PASS |
| 전체·과일 category | `FruitSearchPolicyTest#categoryIntentUsesExistingExactAliasesForAllEightBasicFruits`; `FruitRecordRegressionUiTest#allAppleBananaAndFruitCategoryAreVisibleInRealSearch` | Unit PASS / 기기 PASS |
| 참치김밥 grouping·다른 제품 펼치기 | `FoodSearchPolicyTest#canonicalDishOrderAndBrandPrefixesCreateOneFoodKindGroup`; `SmartCoachUiTest#sameNameSearchShowsOneRepresentativeThenExpandsEveryOriginalProduct` | Unit PASS / 기기 PASS |
| 브랜드 직접 검색 | `FoodSearchPolicyTest#brandQueryChoosesMatchingBrandAsRepresentative`; `SmartFoodSearchUiTest#brandBrowseShowsActualCompanyProductsAndReusesPortionFlow` | Unit PASS / 기기 PASS |
| 홈 빠른 기록·meal context·1건 저장 | `PhotoManualRecordViewModelTest#검색 결과 선택은 네 식사 context를 유지한 빠른 기록으로 열리고 중복 저장되지 않는다`; `QuickRecordUiTest#quickRecordSaveButtonPersistsExactlyOneBreakfastRecord` | Unit PASS / 기기 PASS |
| 저장 실패 시 입력 유지·재시도 | `PhotoManualRecordViewModelTest#빠른 기록 저장 실패는 성공 callback 없이 재시도 가능한 오류를 남긴다`; `FruitRecordLayoutFailureUiTest#failedQuickSaveKeepsInputAndStackThenRetryCompletesOnlyOnce` | Unit PASS / 기기 PASS |
| 즐겨찾기·빠른 기록 | `FrequentFoodRepositoryTest#favorite persists independently from frequent food and survives repository recreation`; `SmartCoachUiTest#favoriteSectionOpensSavedItemWithoutSearchingAgain` | Unit PASS / 기기 PASS |
| 자주 먹는 음식·최근 기록량 | `FrequentFoodRepositoryTest#saving a favorite as frequent merges flags and removing favorite preserves frequent row`; `PhotoManualRecordViewModelTest#최근 기록 shortcut은 공식 제공량으로 위장하지 않고 저장한 양을 적용한다` | Unit PASS / 기기 PASS |
| 공식 생활 단위·portion 비례 | `PortionGuideTest#riceHouseholdChoicesUseDocumented210GramBowl`; `#hamburgerNeverBecomesOnePieceWithoutOfficialPieceEvidence`; `MacronutrientsTest#portionScalesAllMacrosByTheSameRatio` | Unit PASS |
| 기록 원클릭 수정·같은 ID·저장 1회 | `HistoryEditViewModelTest#편집은 기존 값을 불러오고 같은 기록의 모든 사용자 필드를 갱신한다`; `HistoryEditUiTest#listCardHasVisibleOneClickEditActionSeparateFromDetail`; `#existingValuesCanBeEditedAndSaveBecomesSingleShot` | Unit PASS / 기기 PASS |
| 기록 삭제·합계 갱신 | `DailyIntakeTimelineTest#multiple meals aggregate and edits or deletions refresh summaries`; `QaRuntimeUiTest#manualRecordKeyboardPersistenceHistoryAndDeleteFlow` | Unit PASS / 기기 PASS |
| 탄단지 snapshot·부분 합계·null 유지 | `MacronutrientsTest#knownTotalKeepsAvailableValuesAndOnlyUnknownStaysNull`; `#scalingPreservesMissingValuesInsteadOfTurningThemIntoZero`; `PhotoManualRecordViewModelTest#검색 음식 150그램은 탄단지 모두 같은 비율로 MealRecord에 저장한다` | Unit PASS |
| Home→Save→Home→Android Back | `RecordNavigationTest#homeSaveRemovesRecordWithoutPushingDuplicateHome`; `FruitRecordRegressionUiTest#homeQuickSaveThenSystemBackClosesActivityWithoutReopeningRecord` | Unit PASS / 기기 PASS |
| 기록 취소·상단/system Back·History 편집 복귀 | `RecordNavigationTest#reportAndHistoryParentsArePreservedOnCancelOrCompletion`; `FruitRecordRegressionUiTest#upperAndSystemBackCancelHomeSearchWithoutDuplicateHome`; `#historyDetailBackAndEditSaveReturnToHistoryList` | Unit PASS / 기기 PASS |
| 추천→기록·정확한 상세·1회 기록 | `RecordNavigationTest#searchFromRecommendationReturnsToItsParent`; `TodayMealPlanUiTest#dayPlanDetailUsesExactIdPortionAndAllergyThenConsumptionRecordsOnce` | Unit PASS / 기기 PASS |
| 홈 식사 순서·음식명·kcal·추천 summary·non-scroll | `DashboardSummaryTest#mealOrderIsBreakfastLunchDinnerSnack`; `#singleAndMultipleMealsShowNamesAndKnownCalories`; `CurrentUxImprovementUiTest#dashboardAt360AndLargeTextShowsOrderedMealsReportAndDailyPlanSummary`; `TodayMealPlanUiTest#homeIsCompactSummaryWithoutFoodCardsOrVerticalScroll` | Unit PASS / 기기 PASS |
| 360dp·앱1.30·system1.30/합성 약1.69 | `RecommendationPersonalizationUiTest#homeDaySummaryFitsAt360DpWithSystemAndAppFontScale130`; `OnboardingMenuUiTest#combinedSystemAndAppLargeTextKeepsWalkingEstimateReachable`; `TodayMealPlanUiTest#selectorHasAllEightReadableThemesAndNoFoodAt360AndCombined169Font` | 기기 PASS |
| 오늘 리포트·홈 에너지 진입 | `DashboardSummaryTest#reportTotalsMealsFoodsAndPartialNutritionWithoutInventingValues`; `TopLevelNavigationUiTest#homeCalorieTargetOpensTodayReport`; `CurrentUxImprovementUiTest#todayReportRemainsUsableAt360AndLargeText` | Unit PASS / 기기 PASS |
| 테마 전 음식 없음·8개 테마·4끼·실제 목표 | `DailyMealPlanEngineTest#targetMissingHasNoImplicit2000`; `#actual1850IsNotReplacedWith2000`; `TodayMealPlanUiTest#selectorHasAllEightReadableThemesAndNoFoodAt360AndCombined169Font`; `#dietGeneratesFourMealsWithRealTarget` | Unit PASS / 기기 PASS |
| 다이어트·벌크업·치팅 목표 유지·중복 방지 | `DailyMealPlanEngineTest#dietPreservesTargetAndKnownProtein`; `#bulkPreservesTargetAndActualProtein`; `#cheatDoesNotChangeOrBypassTarget`; `#stableIdsAreDistinct`; `#canonicalNamesAreDistinctEvenDifferentIds` | Unit PASS |
| 다른 메뉴·나머지3개 유지·합계 재계산 | `DailyMealPlanEngineTest#lunchReplacementKeepsOtherThreeAndRecalculates`; `TodayMealPlanUiTest#onlyLunchChangesAndTotalIsRecomputed` | Unit PASS / 기기 PASS |
| 다른 조합 보기 | `DailyMealPlanEngineTest#alternateCannotImmediatelyRepeatSignature`; `TodayMealPlanUiTest#alternateKeepsThemeAndAvoidsPreviousPlan` | Unit PASS / 기기 PASS |
| same-day persistence·설정 변경 최소 보정 | `DailyMealPlanEngineTest#savedDateThemeIdsPortionsAndTargetRoundTrip`; `TodayMealPlanUiTest#persistedPlanSurvivesNewStoreAndSettingsRepairOnlyInvalidSlot`; `#smallGoalChangeKeepsSamePlanAndUpdatesDisplayedTarget` | Unit PASS / 기기 PASS |
| 이미 기록된 meal 고정·남은 예산 | `DailyMealPlanEngineTest#existingBreakfastRemainsActual450AndRemaining1550`; `#severalBreakfastRecordsAreSummedWithoutOverwrite`; `TodayMealPlanUiTest#alreadyRecordedBreakfastIsLockedAndSubtracted` | Unit PASS / 기기 PASS |
| dislike·실제 preference·allergy 경고 | `DailyMealPlanEngineTest#dislikeAppliesToEveryThemeAndOutranksPreference`; `#preferenceIsRealAndDoesNotInventReasonsWhenAbsent`; `#allergyWarnsWithoutBlocking`; `TodayMealPlanUiTest#cheatKeepsTargetAndDislikesWhileApplyingPreference` | Unit PASS / 기기 PASS |
| 저속노화식 스타일 제한·근거 | `DailyMealPlanEngineTest#slowStyleHasExplicitUnsupportedState`; `TodayMealPlanUiTest#slowStyleShowsAccurateLimitationAndNoFood`; `DailyPlanAuditTest#allEightThemeAuditColumnsMatchRuntimeForAll292Templates` | Unit PASS / 기기 PASS |
| 초기 검증 중 테마 선택·Back 경합 | `TodayMealPlanUiTest#selectionAndBackDuringGenerationKeepLatestThemeWithoutPublishingPreviousPlan` | 신규 기기 회귀 PASS |
| 운동·초과분·체중 검증 | `ExerciseCoachCalculatorTest`; `ExerciseCoachUiTest#detailsValidateWeightAndShowSixChoices`; `#detailsAlwaysShowHundredKcalButHideExcessSectionAtTarget`; `ZFinalVisualQaTest#capturesRecommendationAndCompleteExerciseFlowOnQaDevice` | Unit PASS / 기기 PASS |
| 걸음·권한·센서·목표 분리 | `StepCounterTest#permissionGapBaselinePreservesAlreadyMeasuredTodaySteps`; `#sensorResetKeepsTodaysStepsAndStartsFromNewTotal`; `StepCounterUiTest#stepsRemainSeparateFromCalorieTarget` | Unit PASS / 기기 PASS |
| onboarding·신체정보·BMR·활동량·체중목표 | `BodyProfileLegacyMigrationTest`; `EnergyViewModelTest#신체정보 저장은 운동 체중과 예상 BMR 입력을 같은 source of truth로 갱신한다`; `WeightGoalCalculatorTest#resultBelowBmrCannotBeApplied`; `EnergyUiTest#settingsAcceptsBmrActivityAndShowsMaintenancePreview` | Unit PASS / 기기 PASS |
| updater metadata·download·size·SHA·package·version·signer | `AppUpdateTest` 29개; `AppUpdateDownloadTest` 6개; `AndroidApkIdentityReaderTest` 3개 | Unit PASS / API29 PASS / 실제 제품 업데이트 PASS |

360dp/합성 font scale 검증은 Samsung에서 Compose 콘텐츠 폭과 density를 제한한 테스트다. 독립 360dp 실기기 또는 실제 OS font setting을 바꾼 검증으로 확대하여 보고하지 않는다. 전체 소스 클래스와 메서드는 [inventory JSON](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/test-method-inventory.json)에 보관했다.

## 14. 사용자 데이터 보호

| 제품 `com.example.healthcare` 대상 작업 | 현재까지 횟수 |
|---|---:|
| uninstall | 0 |
| pm clear | 0 |
| 앱 데이터 초기화 | 0 |
| destructive DB 작업 | 0 |
| 사용자 DB 직접 수정·테스트 record 생성 | 0 |

전체 instrumentation은 QA package에서 실행했다. 최종 QA 복원 검증은 PASS다. 원래 private 파일 15개가 byte 단위로 모두 동일하고 추가·누락 파일 0개다. healthcare DB version 8→8, schema와 전체 테이블 행이 동일하다. 기존 식사 기록 3건, 음식 31582개, 추천292개, 에너지 이력·설정·제외·인식 데이터가 원본과 일치한다. Gradle connected 종료 후 QA package가 없어져 최신 QA APK를 다시 설치하고 백업을 복원했다. 제품 package는 전체 회귀 중 version/install time이 유지됐고, 공개 후 승인된 인앱 업데이트에서만 1.0.6으로 변경됐다. firstInstallTime 유지와 제품 DB 접근 0건을 확인했다. 정상 QA UI 실행 후에도 원본 백업을 다시 복원하여 전체 private 파일·DB 행 동일성을 최종 확인했다.

근거: [QA 복원·전체 데이터 보존 결과](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/release-1.0.6/preservation-restore.json). 제품의 실제 업데이트 후 확인은 package/version/install time과 정상 앱 실행 및 시스템 installer 흐름만 사용한다.

keystore/secret 보호와 사용자 `artifacts/` 보존은 유지한다. 사용자 파일 또는 build output을 source commit에 넣지 않는다. 최종 source stage에는 build/APK/artifacts/keystore/secret이 포함되지 않았고 보호 정책을 유지했다.

## 15. 남은 문제

- 회귀 관련 미해결 테스트 실패: 없음. 전체 Unit356/356·Samsung168/168·API29 3/3·migration9/9·audit292·Lint errors0·Release/signature 검증 PASS.
- 초기 저장 식단 검증 중 선택 유실 경합은 수정했고 추가 회귀와 전체 suite에서 PASS했다. 이는 공개 1.0.5 Home 저장 후 Back 원인을 찾았다는 뜻이 아니다.
- GitHub 공개 asset 재다운로드, Supabase latest 및 이전 기록·권한 보존, Vercel 반영: 모두 PASS. 실제 제품 updater 결과는 11절에 별도 기록한다.
- 공개 1.0.5에서 제보된 원래 Home 저장 후 Back 문제의 정확한 발생 원인은 미확정이다. 현재 개발 navigation의 테스트 결과와 구분한다.
- 실제 Galaxy Note9 실기기는 미검증이다.
- 저속노화식 스타일은 현재 검증 가능한 원재료 식품군 데이터가 부족하여 0개 eligible이며 제한 안내를 표시한다. 건강효과를 보장하지 않는다.
- 가볍게/든든하게의 엄격한 상대 kcal 범위를 지키면 실제 하루 목표의 ±5%/±10%에 항상 들어가지는 않는다. 실제 합계와 차이를 표시하며 목표를 자동 변경하거나 숨은 portion으로 맞추지 않는다.

필수 검증 실패 또는 배포 metadata 불일치는 남아 있지 않다.
