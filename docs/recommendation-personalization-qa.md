# 추천 개인화·테마 QA 보고서

검증 기준일: 2026-10-01. 프로젝트: `Healthcare`, 앱: 오늘 뭐먹지. 구현·전체 회귀·QA 복원·제품 보존 확인을 마친 최종 보고서다. 테스트 수는 중복 실행을 합산하지 않고 [최종 XML 집계](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/final-results.json)를 사용한다.

## 1. STATUS

**COMPLETE.** Unit **300/300**, Samsung 전체 Instrumentation **148/148**, API29 updater **2/2**가 모두 성공했다. 실패·오류·건너뜀은 각각 0이다. Migration 3개 chain과 DB suite 9개, QA/Debug Lint error 0·신규 warning 0, QA/Release 빌드 및 기존 서명 검증을 통과했다. QA 원본 14개 파일을 복원하고, 실제 홈·테마 확인 후에도 전체 DB 행·값이 동일함을 확인했다. 제품 앱과 production 배포는 변경하지 않았다. 기존 공개판 원인 미확정 및 지원 범위는 17항에 별도로 남긴다.

## 2. 추천 UX

- 홈은 서로 다른 추천을 **최대 3개** 가로 pager로 제공한다. stable ID와 정규화한 메뉴명이 중복되는 후보를 제거하며, 적격 후보가 1~2개면 그 수만 표시한다.
- 각 카드에 기존 추천 이미지, 메뉴명, 약 kcal, 짧은 추천 이유, 현재 페이지를 표시한다. 홈에 테마 목록이나 영양 상세를 추가하지 않는다.
- 실제 흐름은 **홈 카드 선택 → 해당 메뉴 상세 → 양·재료 확인 → 먹었어요 → 섭취량 확인·기록**이다. **추천 더 보기 → 별도 추천 화면 → 테마 선택 → 메뉴 상세**로도 탐색한다.
- 홈 이유는 한 줄과 ellipsis를 사용한다. 상세에서는 `추천 이유`를 눌러 실제 적용 조건 목록을 펼친다. 엔진이 판단한 취향 일치·칼로리 참고 범위·테마 분류·제외 음식 등의 조건만 표시한다. 선호 설정이 없으면 취향을 반영했다고 표시하지 않는다.
- 같은 날짜·식사·추천 범위의 ID 목록을 기존 `RecommendationCycleStore`에 저장한다. 유효한 선택과 순서는 유지하고, 제외 음식·목표 등 변경으로 부적격해진 자리만 교체한다. 취향 변경 후 현재 3개에 새 취향이 없고 아직 보지 않은 선호 후보가 있을 때는 최대 한 자리만 교체한다. 빈자리가 있으면 기존 유효 메뉴를 먼저 유지한다.
- 로딩 중 설정 변경도 재로딩 예약으로 반영한다. 이전 조건에서 선택한 상세는 새 조건을 불러올 때 정리한다.
- 홈과 테마가 공유하는 ViewModel에 테마 목록이 남아 있더라도, 홈 카드의 stable ID를 요청하면 오늘 일반 추천을 다시 불러와 해당 메뉴 상세를 연다. 테마 로딩 중 진입한 홈 ID도 최신 요청으로 보관해 로딩 후 처리하고, 그동안 바뀐 제외 조건을 함께 반영한다.

최종 실제 QA 홈에서 **1/3 · 잡채밥 · 약 450 kcal**와 추천 이유·`추천 더 보기`를 확인했다. [홈 캡처](C:/Users/young/AndroidStudioProjects/Healthcare/artifacts/qa-screenshots/recommendation-home-final.png), [테마 캡처](C:/Users/young/AndroidStudioProjects/Healthcare/artifacts/qa-screenshots/recommendation-themes-final.png).

## 3. 음식 취향

최초 신체 프로필을 저장한 신규 사용자에게 **선택 단계 1개**를 제공한다. `한식`, `밥류`, `면·만두`를 여러 개 선택하거나 `특별히 없음`, `건너뛰기`를 사용할 수 있다. 기존 프로필 사용자는 기본적으로 이 단계를 강제로 거치지 않는다. 진행 여부만 기존 `BodyProfileStore`에 기록하고, 실제 취향은 기존 Room `UserMealPreference.preferredFoods`에 저장한다.

스타일 토큰과 기존 자유 입력 음식 키워드는 동일한 필드에 함께 저장한다. 최초 설정 저장은 기존 키워드·식사 배분·조리 방식·기타 설정을 보존한다. 건너뛰기는 저장된 취향을 바꾸지 않고 미저장 선택을 버린다. 저장 중 중복 입력은 한 번만 처리하며, 실패하면 선택을 유지하고 재시도할 수 있다. 저장 후에는 같은 ViewModel에서도 이후 저장소 변경을 다시 받아 표시한다.

**설정 → 식사 추천 설정**에서 음식 스타일, 좋아하는 음식 키워드, 피하고 싶은 음식·재료를 관리한다. 식사 배분·추천 방식과 기존 알레르기 항목은 펼쳐 보는 별도 영역으로 유지한다. 종교 질문이나 종교 저장 항목은 추가하지 않았다.

| 선택 | 실제 추천 매칭 |
|---|---|
| 한식 | 기존 템플릿 `cuisineType = KOREAN` |
| 밥류 | 연결된 `FoodItem.category = 밥류` |
| 면·만두 | 연결된 `FoodItem.category = 면 및 만두류` |
| 자유 입력 음식 | 기존 메뉴명·연결 재료명과 정규화한 키워드 매칭 |

스타일은 메뉴 이름으로 추측하지 않는다. 실제 식사 적격성·검증 데이터·피하고 싶은 음식·식단 제한·조리 방식·예산·칼로리 조건을 먼저 적용한 후보에서 선호도 점수를 사용한다. 선호가 없으면 기존 기본 점수로 추천한다. 선호가 필수 조건을 우회하지 않는다. 알레르기는 확인 가능한 allergen에 대한 **경고**, 피하고 싶은 음식은 **후보 제외**로 구분한다.

취향 chip은 FlowRow로 줄바꿈하고, 선택 상태에 체크 아이콘과 selected semantics를 함께 제공한다. 최소 높이 48dp, 저장 버튼 52dp, 건너뛰기 48dp를 적용했다.

## 4. 테마 추천

홈의 `추천 더 보기`로 진입한 추천 화면에 세 테마를 표시한다. 테마별 기본 선택은 **최대 3개**이며, 충분한 후보가 없으면 적게 표시한다. 각 테마의 현재 후보 개수 표시도 최대 3개 기준이다. 테마별 `다른 메뉴 보기` 확장은 이번 구현에 제공하지 않는다. 일반 추천의 기존 식단 교체 흐름은 유지한다.

| 테마 | 코드에 정의한 selection rule | 제외 음식 | 제공 개수 |
|---|---|---|---|
| 가볍게 먹고 싶은 날 | 같은 meal type의 필수 조건 통과 후보 중 kcal가 낮은 쪽 약 1/3. 정렬 경계 `(n-1)/3` 이하이며 최댓값 제외. 후보가 2개 미만이거나 모두 같은 kcal이면 빈 결과. | 동일하게 제외 | 0~3 |
| 균형 있게 먹기 | 탄·단·지 값이 모두 양수·유한값인 메뉴만 사용. 탄수화물·단백질은 4kcal/g, 지방은 9kcal/g로 계산한 열량비가 각각 **45~65% / 10~35% / 20~35%**에 해당. 앱의 명시적인 분류 기준을 화면에 설명. | 동일하게 제외 | 0~3 |
| 든든하게 먹기 | 같은 meal type의 필수 조건 통과 후보 중 kcal가 높은 쪽 약 1/3. 정렬 경계 `(2*(n-1)+2)/3` 이상이며 최솟값 제외. 후보가 2개 미만이거나 모두 같은 kcal이면 빈 결과. | 동일하게 제외 | 0~3 |

상대 kcal 분류는 같은 식사의 필수 조건 통과 후보를 기준으로 계산하고, 이후 현재 칼로리 범위와 추천 순환 조건을 적용한다. 경계 kcal가 같은 메뉴는 함께 포함될 수 있다. 조건을 풀거나 중복 메뉴로 3개를 채우지 않는다. 후보가 없으면 `현재 조건에 맞는 메뉴가 없어요.`와 `음식 취향 설정` 이동 경로를 제공한다.

**계절 메뉴는 미지원**이다. 기존 recommendation metadata에 실제 season 정보가 없어 이름으로 계절을 추정하지 않았다. 치팅데이·질병별·치료 목적·호르몬·저속노화 등의 테마는 추가하지 않았다. 가벼움·든든함을 건강 효과로 표현하지 않는다.

실제 최종 조건의 테마 화면에서는 가볍게 **0개**, 균형 있게 **3개**, 든든하게 **3개**를 확인했다. 후보가 없는 테마에 숫자를 맞추려고 다른 조건의 메뉴를 넣지 않는다.

## 5. 추천 데이터

- 기존 **292개 recommendation template**와 연결 재료 데이터를 사용한다. 새 메뉴나 영양 수치를 생성하지 않았다.
- 기존 stable ID, 메뉴 이미지 mapping, kcal·macro·portion·meal eligibility를 유지한다. `RecommendationImageResolver`와 기존 추천 사진 컴포넌트를 그대로 사용한다.
- 취향·테마 분류는 기존 cuisine metadata와 연결 음식 category·영양값을 읽는다. 이름이 비슷한 메뉴를 여러 슬롯에 중복 배치하지 않는다.
- 기존 full-cycle no-repeat 저장소를 재사용한다. 홈은 식사별 cycle, 테마는 식사·테마별 cycle을 사용한다. 실제 표시한 신규 ID만 소비하고, 취향·칼로리 변경만으로 이미 본 이력을 지우지 않는다. 한 순환을 모두 소진한 뒤에는 기존 순환 정책에 따라 다시 시작한다.
- 같은 날의 재진입은 저장된 유효 메뉴를 재사용한다. 선호 메뉴가 이미 표시되었거나 아직 보지 않은 선호 후보가 없으면 취향 변경만으로 유효 메뉴를 강제 교체하지 않는다.

다음 세 파일의 SHA-256은 기존 값과 동일하다.

| 보호 파일 | SHA-256 |
|---|---|
| [meal_templates.csv](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/assets/fooddata/meal_templates.csv) | `F6D2855BA77372DDE311E2065CBAEAD3D4348A25CBE52EBF390789707456F5A9` |
| [meal_template_ingredients.csv](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/assets/fooddata/meal_template_ingredients.csv) | `0A3B795901831AB7634227168E74E6E8C08950C1B4C3D8668944FF84620780DD` |
| [GeneratedRecommendationImages.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/GeneratedRecommendationImages.kt) | `D1917E4A2856870C304FAFAAC328CA4C301839E15132C60B1FE3A9A345681B38` |

## 6. DB

최종 개발 Room DB는 **version 8**이다. 이번 추천 개인화 작업에 새 테이블·컬럼·migration을 추가하지 않았다. `preferredFoods`, `UserExcludedFood`, 기존 추천 순환 저장소를 재사용한다. `MealPreferenceRepository`는 기존 6개 저장·관찰 API의 테스트 가능한 인터페이스이며 새 저장소가 아니다. destructive migration을 도입하지 않았다.

별도 이름의 임시 migration DB에서 기록·목표·에너지 프로필·취향·제외 음식·추천 데이터 보존을 검증했다. 실제 QA 저장소를 migration fixture로 사용하지 않는다.

전체 회귀 전 QA의 **DB·SharedPreferences·파일 백업**을 확보했다. AGP의 connected QA 테스트 종료 정리 과정에서 **QA 패키지가 제거되는 동작**을 확인했다. 최종 QA APK를 재설치한 뒤 사전 백업을 복원했고, **원본 14개 파일의 SHA·내용이 전부 일치**했다. 실제 홈·테마 확인 및 홈 재실행 뒤에도 **전체 QA healthcare DB의 모든 table 행·값 변화 0**, DB version 8을 확인했다. 전체 식사 기록 3행, 음식 31,582행, 템플릿 292행, 취향 1행, 제외 음식 1행 등 원본 상태를 유지했다. 단순 행 개수뿐 아니라 저장된 값도 비교했다.

UI 확인 이후 원본 파일 중 실행 중 갱신되는 `android.app.ActivityThread.IDS`, `food_data_update`, `recommendation_cycle_v2`, `app_update_preferences`, `profileInstalled`의 5개만 변경되었다. 실행 캐시·추천 순환·업데이트 확인 시간에 해당하며, 사용자 취향·프로필 등의 canonical 설정과 DB 행은 바뀌지 않았다. `ZFinalVisualQaTest`의 snapshot/복원 fixture도 최종 전체에서 통과했다.

근거: [복원 검증 JSON](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/preservation-restore.json), [실제 UI 확인 후 보존 JSON](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/preservation-after-ui.json), [회귀 전 QA 백업](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/qa-before-regression.tar).

## 7. 관련 구현 테스트

| 관련 Unit class | 최종 XML 테스트 수 | 성공 | 실패/오류 |
|---|---:|---:|---:|
| RecommendationPersonalizationTest | 13 | 13 | 0/0 |
| StableRecommendationPolicyTest | 7 | 7 | 0/0 |
| MealPreferenceViewModelTest | 9 | 9 | 0/0 |
| MealRecommendationEngineTest | 14 | 14 | 0/0 |
| MealPlanRecommendationCycleTest | 6 | 6 | 0/0 |

선호 없음·실제 매칭·필수 조건 우선·dislike/allergy 구분·실제 추천 이유·3개 중복 제거·같은 날 유지·취향 최소 교체·조건 변경·세 테마 분류·빈 후보·순환 이력·최초/설정 동일 source·중복 저장·실패 재시도·skip draft 정리·저장 후 외부 변경 반영을 검증한다. 관련 Unit은 시더·공유 ViewModel 최종 수정까지 포함한 아래 전체 **300개 성공** 실행에 포함된다.

Samsung targeted 추천 Instrumentation의 저장·skip·Settings·홈 3개·이유·테마·제외 음식·360dp/큰 글자 **초기 9개는 수정 후 9개 성공, 실패 0**이다. 추가 큰 글자 검증에서는 취향 화면이 통과했고, 홈 이유가 숨겨지는 실제 레이아웃 문제를 확인해 추천 카드 높이를 148dp로 조정한 뒤 해당 홈 테스트가 통과했다. 같은 ViewModel에서 skip 후 미저장 취향을 버리는 흐름도 관련 재실행에서 통과했다. [초기 targeted 성공 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/targeted-instrumentation-2.txt), [큰 글자 홈 재검증](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/combined-font-home-final.txt).

공유 ViewModel의 테마 로딩 중 홈 카드 진입 및 제외 조건 변경을 함께 검증하는 `homeRecommendationOpenedDuringThemeLoadUsesTodayMenuAndPortionDetail`을 추가했다. 초기 마지막 UI 단계에서 중복 추천 이유를 고르는 selector가 실패해 상세 영역 고유 tag로 보강했으며, 메뉴 ID·조건·portion·정확한 이유 문구 assertion을 유지했다. **targeted 1/1 성공**, 이후 전체 Samsung 실행에 포함된 **RecommendationPersonalizationUiTest 12/12 성공**을 확인했다. [신규 경로 최종 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/theme-to-home-complete.txt). 중복 실행을 합쳐 전체 테스트 수로 표시하지 않는다.

큰 글자 검증은 **실기기의 실제 density·insets를 사용한 360dp Compose fixture**에서 수행했다. OS 글자 크기 설정은 그대로 두고, `LocalDensity`로 system fontScale **1.3**을 재현해 앱 글자 크기 **1.3**과 결합한 **1.69** 배율에서 텍스트·chip·pager·CTA의 표시, 겹침, 잘림을 검사했다. 실제 OS font 설정을 변경해 검사했다는 의미는 아니다.

관련 코드: `FoodPreferencePolicy`, `MealRecommendationThemePolicy`, `StableRecommendationPolicy`, `MealCoachRepository`, `RecommendationCycleStore`, `MealPreferenceViewModel`, `MealPlanViewModel`, `TasteSetupScreen`, `MealPreferenceScreen`, `DashboardScreen`, `MealPlanScreen`, `HealthcareApp`, `BodyProfileStore`. 신규 UI/migration 테스트는 `RecommendationPersonalizationUiTest`, `RecommendationPreferenceMigrationTest`에 있다.

## 8. QA 설치

- 최종 `:app:assembleQa`, `:app:assembleQaAndroidTest`: **성공, exit code 0**. [최종 UI 빌드 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/final-ui-build.txt).
- 최종 QA APK `adb install -r -t`: **Success**. AGP의 전체 테스트 종료 정리로 QA 패키지가 제거된 뒤 재설치하고, 회귀 전 QA DB·설정·파일 백업을 복원했다. 보존 근거는 원본 14개 파일 일치와 실제 UI 확인 후 모든 DB 행·값 일치이다(6항).
- 기기: **Samsung SM-S948N**, Android **16 / API 36**.
- QA applicationId: **`com.example.healthcare.qa`**. 테스트 package: `com.example.healthcare.qa.test`.
- 사용자 확인용 최종 준비 화면: **실제 QA 홈**. `1/3 · 잡채밥 · 약 450 kcal`와 `추천 더 보기`를 확인했고, 더 보기로 세 테마에 진입한 후 홈을 재실행해 준비했다. [홈 캡처](C:/Users/young/AndroidStudioProjects/Healthcare/artifacts/qa-screenshots/recommendation-home-final.png), [테마 캡처](C:/Users/young/AndroidStudioProjects/Healthcare/artifacts/qa-screenshots/recommendation-themes-final.png), [최종 준비 화면 XML](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/final-ready-ui.xml).

직접 확인 순서는 **홈 추천 최대 3개 → 추천 이유 → 추천 더 보기 → 세 테마 → 설정 → 식사 추천 설정 → 취향 변경·저장 → 홈 복귀 후 반영 → 피하고 싶은 음식이 일반/테마 후보에서 제외되는지 확인**이다. 실제 후보가 부족하면 3개 미만 또는 빈 상태가 정상이다.

## 9. 전체 회귀 — Unit

**`:app:testQaUnitTest` 성공. 42개 class, 실행 300개, 성공 300개, 실패 0개, 오류 0개, 건너뜀 0개.**

시더 streaming·공유 ViewModel의 홈 ID 로딩 수정 이후 실행했다. 이후 상세 영역의 testTag만 추가했고 로직 변경이 없어 Unit을 불필요하게 반복하지 않았다. 최종 tag 포함 QA/Release/Lint와 전체 Instrumentation은 다시 검증했다. 근거: [Unit 포함 완료 빌드 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/completion-build.txt), [최종 XML 집계](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/final-results.json).

## 10. 전체 회귀 — Instrumentation

| Samsung 전체 실행 | 실행 | 성공 | 실패 | 건너뜀 | 결과 |
|---|---:|---:|---:|---:|---|
| 최초 전체 connectedQaAndroidTest | 147 | 128 | 19 | 0 | 실패 |
| 조사·수정 후 전체 재검증 | 148 | 148 | 0 | 0 | BUILD SUCCESSFUL, exit code 0 |

최종 Samsung SM-S948N / Android 16 API36의 `:app:connectedQaAndroidTest`는 **33개 class, 148개 전부 성공, 오류 0, 2분 52초**다. [최종 전체 실행 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/full-connected-complete.txt), [최종 XML 집계](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/final-results.json).

[최초 실패 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/full-connected.txt)의 19개를 모두 flaky로 처리하지 않았다. 다음처럼 실제 구현 문제와 테스트 기대·fixture 문제를 구분하여 수정했다. **최초 실패 19개는 모두 해당 관련 재검증에서 통과한 뒤 최종 전체도 통과했다.** [관련 재검증 1](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/regression-related.txt), [관련 재검증 2](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/regression-remaining.txt), [시더·검색 관련 최종 재검증](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/streaming-related-device.txt). 중간 관련 실행에서 남았던 franchise·미역국 두 실패도 마지막 관련 실행에서 통과했다. 중복 실행 수는 합산하지 않는다.

| 구분 | 확인한 문제와 대응 |
|---|---|
| 실제 검색 후보 문제 | BroadFoodSearch의 치킨 검색에서 일반 후보 80개와 제품 후보 60개를 최종 60개로 정렬·절단할 때 실제 제품 후보가 0개로 밀려나는 문제를 확인했다. 기본/일반 음식과 실제 제품 후보를 각각 유지하는 정책으로 수정하고 회귀를 보강했다. 원본 음식 행을 삭제하지 않는다. |
| 검색/분류 테스트의 오래된 기대 | Bundled tuna 테스트는 원시 `김밥_참치`의 단순 정규화와 사용자용 대표 `참치김밥`을 혼동했다. canonical group/display 이름을 검증하도록 수정했으며 원본 행·g 우선 검증은 유지했다. SmartFoodSearch는 실제 음식/대표 그룹 tag와 원본·portion을 검증하도록 수정했다. 미역국 ml 변형은 g 대표 그룹의 대안을 펼쳐 선택하도록 했으며 350ml=39kcal 기록, 신라면 240g=1001kcal 검증을 유지했다. |
| pager를 세로 스크롤로 간주한 기대 | ExerciseCoach·Onboarding·StepCounter 테스트가 모든 ScrollAction을 금지해 가로 pager에도 실패했다. 세로 ScrollAxis만 금지하도록 수정했으며, 실제 카드·CTA의 잘림과 viewport 내 위치 검증을 유지했다. |
| 화면 문구·의미 트리 순서 기대 | CompactWidth의 노드 반환 순서를 화면 배치 순서로 간주한 기대, 이전 아침 인사·다음 식사 문구·kcal 단일 문구 기대를 현재 화면 기준으로 정리했다. 표시 요소와 실질적 동작 검증은 유지했다. |
| 기록·목표 fixture | QaRuntime의 현재 수동 입력 경로를 확인해 테스트를 수정했다. ZFinalVisual의 수동 목표 2000이 기존 EnergyProfile의 AUTO/BMR/MAINTENANCE 모드에 가려지는 fixture를 수정했다. 오늘 유효 목표가 MANUAL 2000인지 resolver로 확인하고 원본 상태를 복원했다. |

추가 소스 검토에서 공유 추천 ViewModel에 테마 선택이 남아 있는 상태로 홈 카드를 누르면 같은 식사·예산 cache 경로가 테마 목록을 유지하는 실제 문제와, 테마 로딩 중 요청된 홈 ID를 잃는 문제를 수정했다. 홈 ID를 오늘 일반 추천에서 선택하도록 cache 조건을 보강하고 최신 로딩 요청을 보관한다. 새로운 회귀 테스트를 추가했다(7항). 이 문제와 targeted 큰 글자 이유 가독성 실패는 최초 전체 19개에 더하지 않는다.

## 11. API29 updater

기존 Android 10 / API29 관리형 `api29Updater` 환경을 재사용했다. archive APK identity, installed signer, API29 signer fallback 관련 테스트를 실행했다.

| 실행 | 테스트 수 | 성공 | 실패 | 상태 |
|---|---:|---:|---:|---|
| 최초 API29 실행 | 2 | 1 | 1 | installed/archive identity 테스트에서 OutOfMemoryError |
| streaming 시더 적용 후 실행 | 2 | 1 | 1 | OOM 없음. 첫 테스트의 DB count Flow 대기가 180초 timeout, 둘째 서명 테스트 통과 |
| stamp 기반 fixture 적용 후 최종 재검증 | 2 | 2 | 0 | BUILD SUCCESSFUL, 25초, exit code 0 |

근거: [초기 API29 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/api29-updater.txt), [시더 수정 후 중간 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/api29-updater-final.txt). 기존 cached AVD의 앱 heap growth limit 16MB에서 APK parser뿐 아니라 `Application.onCreate`의 CSV 시더에서도 OOM을 확인했다. 전체 CSV lines·row map·약 3만 개 FoodItem을 동시에 보유하는 구조를 줄여 **250행 단위 streaming insert를 단일 Room transaction으로 처리**하도록 수정했다. 기존 데이터 validation·rollback·템플릿 영양 계산·dataset stamp를 유지하며, API29의 seed 반환은 5.205초 이내로 확인했다.

두 번째 실행에서 OOM은 없었으나 첫 DB count Flow 대기가 180초 timeout했다. seed 반환 시간과 둘째 서명 테스트 통과는 Room 2.7 transaction/invalidation 관찰 경합과 일치한다. fixture는 **transaction 반환 후 기록하는 현재 manifest bundle stamp를 listener로 먼저 기다린 다음, 실제 food/template/ingredient 세 count를 직접 조회·assert**하도록 교정했다. listener 등록 뒤 초기 값을 보내고 종료 시 해제한다. timeout 180초와 원래 archive/installed package·양쪽 signer assertion은 유지했다. 새 환경 생성·sleep·GC 호출은 하지 않았다. 최종 **1개 class, 2개 성공, 실패·오류·건너뜀 0**이며 stamp·세 count 검증도 포함한다. [API29 최종 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/api29-updater-complete.txt), [최종 집계](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/final-results.json). **실제 Note9: 실기기 미검증.**

## 12. Migration

| 경로 | 검증 내용 | 최종 전체 결과 |
|---|---|---|
| v1→v8 전체 chain | 기존 migration 1→2→3→4→5→6→7→8 및 최종 Room schema·기존 데이터 보존 | 성공 |
| v6→v7→v8 | 기존 취향 토큰/키워드, dislike/allergy, 기록·목표·에너지·추천·인식 데이터 보존 | 성공 |
| v7→v8 | 기존 FrequentFood·macro 보존, 기존 saved-food 정책 전환, 신규 제품별 identity·명시적 즐겨찾기 reopen 보존 | 성공 |

**Migration 3개 chain 성공. DB suite 9/9 성공, 실패 0.** `AppDatabaseMigrationTest` 7개와 `RecommendationPreferenceMigrationTest` 2개이며, migration 3개 외 기존 DB 동작 6개도 포함한다. [DB 테스트 목록·최종 집계](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/final-results.json).

신규 두 테스트는 별도 임시 이름 DB에서 실제 migration chain으로 fixture schema를 만들고 v8 Room open으로 검증했다. 12개 테이블의 기존 행 값을 비교한다. v6에서 새로 추가되는 FrequentFood macro는 null, v7의 기존 macro 35/12/6은 보존된다. 기존 7→8 정책은 옛 자동 saved-food flag를 새 명시적 즐겨찾기로 간주하지 않고 `isFrequent=true`, `isFavorite=false`로 전환한다. 이는 이번 작업에서 추가한 동작이 아니다. v8에서 사용자가 명시적으로 지정한 즐겨찾기와 같은 이름의 서로 다른 제품 identity는 별도로 보존됨을 확인한다.

## 13. Lint

| 최종 Variant | Errors | Warnings | 기존 baseline warnings | 신규 warnings |
|---|---:|---:|---:|---:|
| QA | 0 | 62 | 62 | 0 |
| Debug | 0 | 58 | 58 | 0 |

최종 상세 tag까지 포함한 `:app:lintQa`, `:app:lintDebug` **성공, exit code 0**. 초기 추가 UseKtx 2건은 수정했다. 경고의 id·message·file 및 발생 수를 baseline과 Counter로 비교해 **신규 warning instance 0**을 확인했다. [최종 빌드·Lint 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/final-ui-build.txt), [QA Lint XML](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/reports/lint-results-qa.xml), [Debug Lint XML](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/reports/lint-results-debug.xml). 기존 경고는 위 수치대로 남아 있다.

## 14. Release build

- 최종 상세 tag 포함 `:app:assembleRelease`: **성공, exit code 0**.
- 최종 Release APK `apksigner verify`: **성공, exit code 0**.
- signer SHA-256: **`385693830FF4C9F9122A9DC5D992646A871AC82764439AEC1C1078BF55496CA8`** — 기존 알려진 signer와 일치.
- packageName: **`com.example.healthcare`**, versionCode **6**, versionName **1.0.5**.
- 기존 `today-what-to-eat-release.jks`와 alias `today-what-to-eat`를 유지했다. 새 keystore 생성·삭제·alias 변경·비밀번호 출력은 하지 않았다.
- **빌드와 서명 검증만 수행했으며 production 배포하지 않았다.** [최종 빌드 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/final-ui-build.txt), [Release APK](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/outputs/apk/release/app-release.apk).

## 15. 핵심 기능 회귀

| 기능 | 확인 범위 | 최종 상태 |
|---|---|---|
| Onboarding·취향 | 1단계, skip, 체크/48dp, 저장·실패 재시도, Settings 동일 source | PASS — 관련 Unit 및 추천UI12개 |
| 음식 검색 | 두부·달걀/계란, 기본 과일, 참치김밥 대표 그룹, 일반 종류·실제 브랜드/제품, kcal·양 계산 | PASS — Bundled11·SmartFoodSearch8·과일 회귀 |
| 빠른 기록 | 홈 기록 진입·검색·양 입력·저장, 실패/취소 입력 보호 | PASS — QuickRecord3·과일 기록 회귀 |
| 즐겨찾기 | 명시적 favorite, 빈번 음식 구분, 같은 이름 제품 identity 보호 | PASS — DB·빠른 기록 회귀 |
| 기록 수정 | 목록에서 진입·원본 ID 수정·macro/portion 반영 | PASS — HistoryEdit4 및 관련 Unit |
| 기록 삭제 | 삭제 후 목록·홈 반영 | PASS — HistoryEdit·QaRuntime |
| Home navigation | Home→기록→저장→Home→Back, 다른 부모·취소·실패 보호 | PASS — RecordNavigation Unit5·TopLevelNavigation4·과일 기록 회귀 |
| Home non-scroll | 세로 스크롤 없음, 가로 pager, 실제 density/insets의 360dp·LocalDensity 1.69 fixture | PASS — 추천UI·Exercise·Onboarding·StepCounter·CompactWidth |
| 탄단지 | 기록·부분 섭취·수정·삭제 후 누적 | PASS — NutritionAccumulation2·DailyIntake4·관련 Unit |
| 오늘 리포트 | 기록별 음식명/kcal, 오늘 요약·누적 | PASS — CurrentUxImprovement5·DashboardSummary Unit |
| 추천 | 0~3 distinct ID/이름, 이미지, 실제 이유, same-day·최소 교체·cycle·dislike·allergy·테마·portion/먹었어요·테마 로딩→홈 ID | PASS — 추천UI12·Bundled·SmartCoach·MealPlanConfirmation·관련 Unit |
| 운동 | 초과 kcal 기준·캐릭터·운동 CTA·걷기 예상 | PASS — ExerciseCoach4·ZFinalVisual |
| 걸음 | 걸음 상태·거리·소모량, 섭취 목표와 분리 | PASS — StepCounter3 및 관련 Unit |
| 목표 | 체중·에너지·오늘 유효 target mode 및 수동 목표 | PASS — EnergyUI8·DB·관련 Unit |
| DB migration | v1→8, v6→7→8, v7→8·보존 | PASS — 3chains·DB suite9/9 |
| updater | Android16 identity와 API29 archive/installed signer·fallback | PASS — Samsung identity2·UpdaterUI3·API29 2/2. Note9 실기기 미검증 |

각 class의 최종 실행 수와 성공 여부는 [최종 XML 집계](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/final-results.json)에서 확인할 수 있다.

## 16. 버전/배포

- 제품 versionCode **6**, versionName **1.0.5** 유지. QA versionName은 **1.0.5-qa**이다.
- GitHub Release 생성·변경 **없음**.
- Supabase latest 변경 **없음**.
- Vercel production metadata 변경 **없음**.
- 이번 작업에서 production 배포 **없음**. 1.0.6으로 버전을 올리지 않았다.
- 제품 package `com.example.healthcare`에 대한 **install·uninstall·pm clear·DB 쓰기 0**. 제품 앱은 읽기 전용 `dumpsys`로 identity를 확인했으며 versionCode/versionName 및 lastUpdateTime이 전후 정확히 동일했다: **v6 / 1.0.5 / `2026-09-29 23:17:20`**. 제품 private DB의 byte 비교는 수행하지 않았다. 6항의 파일·DB 행 비교 대상은 QA 앱이다. [제품 identity·QA 보존 확인](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/recommendation-qa/preservation-after-ui.json).

## 17. 남은 문제

이번 구현·회귀검증의 미해결 실패는 **없음**이다. 다음 기존 조사 항목과 지원 범위는 유지한다.

1. **기존 공개판 조사 항목:** 공개 **1.0.5**의 `Home → 기록 → 저장 → Home → Back` 제보는 정확한 재현 경로와 원인이 여전히 **미확정**이다. 현재 QA의 저장 후 back·다른 부모·취소·실패 보호는 이번 전체 회귀에서도 통과했다. 공개판 원인을 찾았다고 단정하지 않는다.
2. **기기 검증 범위:** Android 10/API29 관리형 환경의 핵심 updater 테스트는 통과했으나 **실제 Note9 실기기 미검증**이다.
3. **데이터 지원 범위:** 계절 메뉴는 실제 season metadata가 없어 **미지원**이다. 메뉴 이름으로 계절을 추정하지 않았다.
4. **탐색 지원 범위:** 테마별 추가 페이지·`다른 메뉴 보기` 확장은 **미제공**이다. 지원되는 세 테마에서 기본 최대 3개를 제공하고 일반 추천의 기존 식단 교체는 유지한다.

요청한 QA 설치·전체 회귀·데이터 보존 확인은 완료했다. 이번 작업에서 정식 production 배포는 수행하지 않았다.
