# 오늘의 추천: 테마 선택과 하루 식단 QA

검증일: 2026-10-01. 프로젝트: `C:\Users\young\AndroidStudioProjects\Healthcare`. 이번 보고서는 하루 식단 변경의 관련 검증만 다룬다. 기존 미커밋 작업은 보존했다.

## 1. STATUS

**COMPLETE — 요청한 개발·관련 검증·Samsung QA 업데이트 범위.**

추천 첫 화면에 8개 테마를 표시하고, 선택 후 실제 목표를 사용하는 아침·점심·저녁·간식 조합을 제공한다. 관련 Unit 95개와 targeted instrumentation 20개가 모두 통과했다. 저속노화식 스타일은 요청에서 허용한 데이터 부족 제한 상태다. 공개 제품 배포는 수행하지 않았다.

## 2. 오늘의 추천 UX

수정 전: 추천 음식 최대 3개와 개별 끼니 선택을 중심으로 구성했다.

수정 후: 추천 탭 첫 진입은 **“오늘은 어떻게 먹고 싶나요?”**와 8개 테마 카드다. 선택 후에만 하루 결과를 표시한다. 결과에는 4개 끼니, 실제 목표와 합계, 영양정보, 확인된 알레르기 경고, `다른 메뉴`, `다른 조합 보기`, 기존 양 조절·먹었어요 상세 진입을 제공한다.

첫 화면 음식 노출: **없음**. 저장된 식단이 있어도 추천 탭 첫 진입은 선택 화면이다. 홈은 테마와 하루 합계, `오늘 식단 보기`를 표시하는 작은 요약으로 바꾸었고 세 음식 pager를 제거했다. 홈의 세로 스크롤은 추가하지 않았다.

## 3. 테마

다음 수는 292개 원본의 기본 메타데이터 판정에서, 하나 이상의 끼니에 eligible한 **고유 메뉴 수**다. 실제 사용자의 제외 음식·식사 방식·조리 시간·비용·cycle 필터 후 후보 수와는 다르다.

| 테마 | 실제 selection rule | eligible 메뉴 수 | 하루 plan |
|---|---|---:|---|
| 가볍게 | 기존 끼니별 상대적 저열량 약 하위 1/3을 재사용하고 그 안에서 목표에 가까운 조합 탐색 | 96 | 생성 확인; 목표 오차 안내 필요할 수 있음 |
| 균형 있게 | 실제 탄단지 완비, 4/4/9로 계산한 탄수화물 45~65%, 단백질 10~35%, 지방 20~35%의 기존 앱 기준 | 26 | 생성 확인 |
| 든든하게 | 기존 끼니별 상대적 고열량 약 상위 1/3을 재사용하고 그 안에서 목표에 가까운 조합 탐색 | 110 | 생성 확인; 목표 오차 안내 필요할 수 있음 |
| 다이어트 | 필수 조건을 통과한 후보 안에서 상대적 저열량, 완비된 영양정보와 확인된 단백질 정보에 가점; 현재 목표 유지 | 292 | 생성 확인 |
| 벌크업 | 필수 조건을 통과한 후보 안에서 상대적 고열량과 실제 단백질 절대량에 가점; null 단백질 추정 없음; 현재 목표 유지 | 292 | 생성 확인 |
| 건강하게 | 실제 탄단지 완비, 명시적 근접 균형 범위: 탄수화물 40~70%, 단백질 8~40%, 지방 15~40% | 54 | 생성 확인 |
| 치팅데이 | 필수 조건과 목표 유지, 실제 선호 음식·즐겨찾기 가점 증가, 균형 필수 조건 없음 | 292 | 생성 확인 |
| 저속노화식 스타일 | 검증된 원재료 곡물·콩·채소 분류가 현재 데이터에 없으므로 eligible 처리하지 않음 | 0 | 데이터 부족 제한 상태 |

균형 범위는 앱의 선택 기준이며 의료 기준이나 건강효과 보장이 아니다. 건강하게 테마의 원재료 식품군 다양성은 현재 요리 분류로 추정하지 않았다.

## 4. 292 메뉴 audit

[theme-eligibility-audit.csv](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/recommendation/theme-eligibility-audit.csv)는 runtime 입력이 아닌 개발 검증 자료다. [생성 스크립트](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/recommendation/generate-theme-audit.py)로 재생성할 수 있으며 테스트가 원본과 runtime 판정을 대조한다.

| 검사 | 결과 |
|---|---:|
| 원본 recommendation template | 292 |
| audit row / 고유 stable ID | 292 / 292 |
| 누락 / 중복 ID | 0 / 0 |
| kcal 완비 | 292 / 292 |
| 탄단지 모두 완비 | 261 / 292 |
| 원재료 메타데이터 완비 | 36 / 292 |
| 알레르기 UNKNOWN 표시 메뉴 | 255 / 292 |

| 기본 판정 | 아침 | 점심 | 저녁 | 간식 |
|---|---:|---:|---:|---:|
| meal eligibility | 115 | 230 | 230 | 52 |
| 가볍게 | 43 | 77 | 77 | 19 |
| 균형 있게 | 15 | 19 | 18 | 4 |
| 든든하게 | 39 | 82 | 82 | 18 |
| 다이어트 / 벌크업 / 치팅데이 각각 | 115 | 230 | 230 | 52 |
| 건강하게 | 22 | 45 | 43 | 5 |
| 저속노화식 스타일 | 0 | 0 | 0 | 0 |

테마별 고유 메뉴 총수는 3절에 기재했다. 현재 categories는 완성 요리 분류이며 검증된 원재료 식품군 taxonomy가 아니다. audit에 이를 숨기거나 메뉴 이름으로 대체하지 않았다.

## 5. 하루 kcal 알고리즘

목표 source는 기존 `CalorieGoal`과 해당 날짜의 `EnergyProfileHistory`다. BMR 모드는 저장된 기초대사량, 유지 모드는 기존 유지 에너지 계산기, 수동 모드는 저장된 calorie goal을 사용한다. 목표가 없거나 유효하지 않으면 **“하루 목표 칼로리를 먼저 설정해주세요.”**와 기존 에너지 설정 진입을 제공한다. 2,000 kcal fallback이나 테마에 의한 목표 저장 변경은 없다.

전체 목표 배분 중심은 아침 25%·점심 30%·저녁 35%·간식 10%다. 탐색에서 아침 20~30%·점심 25~35%·저녁 25~35%·간식 5~15% 범위를 우선한다. 이미 기록하거나 고정한 끼니의 실제 kcal를 차감하고, 남은 슬롯의 비율을 다시 정규화한다.

각 끼니는 최대 40개 후보(배분 예산과 가까운 32개 및 저·고열량 각 4개), beam은 최대 256개로 제한한다. 남은 후보의 최소·최대 kcal로 목표 도달 가능성을 평가하고 배분 범위, 실제 취향과 스타일 점수, 고정 tie break로 정렬한다. 완성 조합은 ±5% → ±10% → 탐색에서 찾은 실제 가장 가까운 조합 순서다. 제한 탐색이므로 모든 292⁴ 조합의 전역 최적해라고 주장하지 않는다.

Room 후보 읽기는 IO, 조합 탐색은 Default dispatcher에서 수행한다. 사용 portion은 기존 표준 양 **1.0**이며 숨겨진 serving 조정이 없다. 실제 Samsung의 QA 목표 1,500 kcal에서 읽기·탐색·저장을 포함한 7개 테마 생성 시간은 **283~769 ms**였다.

| 읽기 전용 실제 QA 데이터 검사 | 생성 시간 | 합계 / 목표 |
|---|---:|---:|
| 가볍게 | 729 ms | 1,034 / 1,500 kcal |
| 균형 있게 | 283 ms | 1,498 / 1,500 kcal |
| 든든하게 | 631 ms | 1,821 / 1,500 kcal |
| 다이어트 | 705 ms | 1,473 / 1,500 kcal |
| 벌크업 | 735 ms | 1,544 / 1,500 kcal |
| 건강하게 | 395 ms | 1,498 / 1,500 kcal |
| 치팅데이 | 769 ms | 1,494 / 1,500 kcal |

이 검사는 실제 DB를 읽되 메모리 cycle/store를 사용했다. 뒤의 UI 예시는 실제 QA cycle을 소비하므로 메뉴가 다를 수 있다. 가볍게와 든든하게는 현재 후보 구간·표준 양 제약 때문에 목표 범위를 크게 벗어난 결과가 나왔다. UI는 각각 목표보다 466 kcal 낮음 / 321 kcal 높음을 정확히 안내하며 목표 충족으로 표시하지 않는다. [기기 측정 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/device-generation-times.txt).

## 6. 하루 추천 예시

실제 QA 사용자 목표는 저장된 **BMR 모드 1,500 kcal**다. 다음은 최종 캡처의 다이어트 생성 결과다.

| 끼니 | 실제 메뉴 | kcal | portion |
|---|---|---:|---:|
| 아침 | 개피떡(바람떡) | 309 | 1.0 |
| 점심 | 삼치구이 | 445 | 1.0 |
| 저녁 | 비빔국수 | 508 | 1.0 |
| 간식 | 호박죽 | 196 | 1.0 |
| 합계 | 현재 목표 1,500 kcal | **1,458** | |

목표 차이 −42 kcal(−2.8%). 실제 탄단지 합계는 탄수화물 186.4g·단백질 84.6g·지방 41.4g이다. [식단 캡처](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/diet-day-plan.png), [저장 snapshot](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/diet-capture-example.json).

앞서 검증한 별도 다이어트 조합도 율무죽 308·오리백숙 448·연어롤 510·가래떡 195 = 1,461 / 1,500 kcal로 확인했다. 영양값이나 양을 수정해 합계를 맞추지 않았다.

## 7. 이미 기록된 식사

오늘의 `MealRecord`를 끼니별로 묶어 실제 kcal와 확인 가능한 영양정보를 합산한다. 해당 슬롯은 기록된 식사로 표시하고 잠그며 추천으로 덮어쓰거나 `다른 메뉴`를 제공하지 않는다. 일부 영양정보가 없으면 완전한 하루 macro로 표시하지 않는다.

기기 fixture: 목표 1,850 kcal, 기록된 아침 450 kcal → 남은 예산 **1,400 kcal**. Unit fixture: 목표 2,000 kcal, 아침 450 kcal → **1,550 kcal**. 기록된 섭취가 목표에 도달하거나 초과했고 빈 슬롯이 있으면 남은 추천이 목표에 맞는다고 처리하지 않는다.

계획 생성만으로 MealRecord를 생성하지 않는다. 기존 상세 화면에서 사용자가 먹었어요를 확정할 때만 기록한다. 실제 QA의 기존 기록 3개는 모두 보존했다. 상세 기록 쓰기는 격리 fixture에서 확인했다.

## 8. 개인화

meal eligibility·기존 diet/source 필수 조건·제외 음식/재료·조리 방식/시간/비용 → 테마 조건 → 하루 kcal와 배분 가능성 → 실제 선호 음식/스타일 가점 → cycle의 미사용 후보와 deterministic tie break를 적용한다. cycle에서 이미 본 메뉴는 조합 후보로 재사용하지 않으며 한 cycle을 소진했을 때 직전 메뉴를 피하며 새 cycle을 시작한다.

선호 음식은 기존 저장소와 `FoodPreferencePolicy`를 재사용한다. 치팅데이는 선호와 즐겨찾기 가점을 높인다. 좋아하는 음식이 필수 eligibility, 제외 음식, 테마 조건을 우회하지 않는다. 실제 선호 일치가 없는 경우 취향을 반영했다는 이유를 만들지 않는다.

dislike는 후보 제외, allergy는 확인된 원재료 메타데이터와 일치하는 경우 해당 카드의 경고다. 둘을 합치지 않았다. UNKNOWN은 안전하다고 표시하지 않고 미확인 안내를 유지한다. 실제 QA의 기존 오이 제외 설정도 유지된다.

## 9. 다른 메뉴 / 다른 조합

`다른 메뉴`: 같은 슬롯만 재탐색하며 다른 세 끼와 이미 기록된 끼니는 고정한다. 같은 테마·meal eligibility·dislike 조건과 하루 중복 금지를 지킨다. 후보가 없으면 기존 plan을 유지하고 안내한다.

실제 최종 캡처: **점심 삼치구이 445 → 돼지갈비찜 485 kcal**. 아침 309·저녁 508·간식 196은 stable ID와 portion까지 동일하다. 합계 **1,458 → 1,498 kcal**. 이전 검증에서도 점심 오리백숙 448 → 귀리밥 486만 바뀌고 합계 1,461 → 1,499로 재계산됨을 확인했다.

`다른 조합 보기`: 동일 테마에서 고정 기록을 유지하며 추천 슬롯 전체를 다시 탐색한다. 테마·슬롯 ID·portion 기반 signature의 최근 24개 이력으로 직전 동일 plan을 즉시 반환하지 않는다. 끼니별 cycle도 기존 store의 별도 scope에 저장한다. 하루 내부 동일 stable ID와 정규화 메뉴명 중복은 제외한다.

## 10. same-day persistence

기존 `recommendation_cycle_v2` SharedPreferences에 versioned key `today_four_meal_plan_v1`을 추가했다. 날짜, 테마, 추천 슬롯 stable ID·portion·kcal, 목표 snapshot, 최근 24개 signature를 저장한다. 기록된 슬롯은 실제 DB 기록으로 복원한다. DB 스키마는 추가하지 않았다.

실제 force-stop 후 재실행에서 치팅데이 1,494 kcal의 4개 메뉴와 snapshot 전체가 같았고, 최종 다이어트 점심 교체 후 **1,498 / 1,500 kcal**의 snapshot 전체도 같았다. 상세의 정확한 메뉴·표준 100% 양을 확인하고 뒤로가면 기존 하루 결과로 돌아왔다.

재검증 시 dislike/조리 조건 등으로 invalid한 슬롯만 바꾼다. 선호 변경만으로 유효한 plan을 모두 초기화하지 않는다. 목표 변화가 기존 목표 대비 5% 이내이면 유효 슬롯을 유지하고 표시 목표를 갱신하며, 더 큰 변화에서는 전체를 재계산한다. 홈에는 저장 원문 대신 재검증된 plan을 표시한다. 날짜가 다르면 전날 계획을 재사용하지 않는다.

## 11. 저속노화식 스타일

**데이터 부족으로 제한.** 확인한 데이터는 292개 template의 요리 categories, 실제 연결 원재료, 메뉴명, 실제 macro completeness다. 원재료 완비 36개만으로 검증된 곡물·콩·채소 분류가 생기지는 않으며, 전체 source에 해당 taxonomy가 없다.

selection rule은 검증된 분류 없이 eligible 처리하지 않는 것이다. 메뉴 이름으로 식품군을 추정해 채우지 않는다. 카드와 선택 흐름을 구현했고 결과에는 **“현재 메뉴 데이터로 정확히 분류할 수 있는 식단이 부족해요.”**를 표시한다. 음식 카드나 노화를 늦춘다는 효과 주장은 없다. [제한 상태 캡처](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/slow-aging-style-plan.png).

## 12. DB

Room version **8 유지**. 이번 변경에 schema 변경, migration, destructive migration이 없다. 추천 생성과 저장은 기존 DB 읽기와 기존 SharedPreferences 기반이다. 사용자가 기존 상세에서 섭취를 확정할 때만 실제 기록과 필요한 계획 슬롯을 기존 transaction 경로로 처리한다.

실제 QA 전후 DB의 모든 테이블을 행 단위로 비교했다. **변경 테이블 0**, version 8 → 8. MealRecord 3개, 에너지 profile 1개, 식사 설정 1개, 제외 음식 1개, 음식 31,582개, 추천 template/재료 연결 각 292개를 포함한 전체 행이 같다. 목표·빠른 음식·기록 인식 데이터 등 다른 테이블도 동일하다.

의도된 QA 변경은 추천 plan/cycle SharedPreferences다. 설치 과정의 `profileInstalled` 파일도 바뀌었고 격리 테스트 namespace 파일이 생성됐으나 기존 다른 사용자 저장 파일은 보존됐다. 테스트는 별도 Room/SharedPreferences 또는 실제 DB 읽기 전용 방식으로 실행했다. [보존 비교 결과](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preservation-final.json).

공개 제품 앱에는 설치·제거·clear·run-as·DB 접근·UI 조작을 수행하지 않았다. 제품 versionCode/versionName/lastUpdateTime을 읽기 전용으로 비교해 동일함을 확인했다. 제품 DB의 바이트 비교를 수행했다는 의미는 아니다.

## 13. 실제 수정 파일

다음은 이번 하루 식단 작업의 파일이다. git status에 보이는 이전 작업의 다른 변경은 이번 작업의 수정으로 포함하지 않았다.

| 파일 | 변경 내용 |
|---|---|
| [DailyMealPlanEngine.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/domain/DailyMealPlanEngine.kt) | 신규. 8개 테마 규칙, 실제 목표 해석, 4끼 제한 탐색, 기록 잠금, 합계와 이유 |
| [TodayMealPlanStore.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/data/TodayMealPlanStore.kt) | 신규. versioned snapshot codec와 기존 SharedPreferences 저장 |
| [TodayMealPlanRepository.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/data/repository/TodayMealPlanRepository.kt) | 신규. 기존 source 연결, IO/Default 실행, cycle, same-day 재검증, 교체 |
| [MealCoachRepository.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/data/repository/MealCoachRepository.kt) | 실제 292개 seed·원재료·알레르기 읽기와 기존 cycle/detail/섭취 슬롯 연결 |
| [TodayMealPlanViewModel.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/viewmodel/TodayMealPlanViewModel.kt) | 신규. 선택·로딩·재시도·교체·설정 변화·saved plan 상태 |
| [TodayRecommendationScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/TodayRecommendationScreen.kt) | 신규. 테마 먼저 선택, 4끼 결과·이미지·합계·경고·actions·큰 글자 |
| [HealthcareApplication.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/HealthcareApplication.kt) | 하루 plan store/repository 인스턴스 연결 |
| [ViewModelFactory.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/viewmodel/ViewModelFactory.kt) | 새 ViewModel과 홈 repo 연결 |
| [DashboardViewModel.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/viewmodel/DashboardViewModel.kt) | 음식 3개 생성 대신 검증된 테마/하루 합계 요약 |
| [DashboardScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/DashboardScreen.kt) | 기존 pager를 선택 초대/오늘 식단 요약 카드로 대체, 홈 non-scroll 유지 |
| [HealthcareApp.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/HealthcareApp.kt) | 추천 탭 최초 선택과 홈 저장 식단 진입, 기존 설정/detail 경로 연결 |
| [Navigation.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/Navigation.kt) | 상세 route에 optional daily stable ID 추가 |
| [MealPlanViewModel.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/viewmodel/MealPlanViewModel.kt) | 정확한 하루 메뉴 detail, 기존 portion/섭취 처리와 간식 슬롯 연결 |
| [MealPlanScreen.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/main/java/com/example/healthcare/ui/screens/MealPlanScreen.kt) | 하루 메뉴 이미지와 단일 detail, 양 조절·먹었어요 및 결과 복귀 |
| [DailyMealPlanEngineTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/test/java/com/example/healthcare/DailyMealPlanEngineTest.kt) | 신규. 관련 규칙/계산/필터/codec/cycle 52개 Unit |
| [DailyPlanAuditTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/test/java/com/example/healthcare/DailyPlanAuditTest.kt) | 신규. 실제 292개 audit, runtime 8개 판정, 4끼 및 배분 회귀 4개 Unit |
| [TodayMealPlanUiTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/androidTest/java/com/example/healthcare/TodayMealPlanUiTest.kt) | 신규. 격리 fixture와 실제 source 읽기 전용 기기 테스트 18개 |
| [RecommendationPersonalizationUiTest.kt](C:/Users/young/AndroidStudioProjects/Healthcare/app/src/androidTest/java/com/example/healthcare/RecommendationPersonalizationUiTest.kt) | 기존 홈 pager 기대값 2개를 초대/하루 요약 UI 검증으로 갱신 |
| [generate-theme-audit.py](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/recommendation/generate-theme-audit.py) | 신규. audit 재생성 |
| [theme-eligibility-audit.csv](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/recommendation/theme-eligibility-audit.csv) | 신규. 정확히 292개 원본 영양/메타데이터/테마 판정 |
| [daily-theme-plan-qa.md](C:/Users/young/AndroidStudioProjects/Healthcare/docs/daily-theme-plan-qa.md) | 신규. 이번 20항목 완료 보고 |

개발용 로그·보존 snapshot·캡처 helper는 `app/build/daily-plan-qa/`에 두었다. 기존 사용자 소유 `artifacts/`는 수정하지 않았다.

## 14. 관련 Unit Test

실행 **95**, 성공 **95**, 실패 **0**, 오류 **0**, skip **0**. 전체 Unit suite를 실행한 수치가 아니다.

| 실행 class | 개수 |
|---|---:|
| DailyMealPlanEngineTest | 52 |
| DailyPlanAuditTest | 4 |
| MealRecommendationEngineTest | 14 |
| MealPlanRecommendationCycleTest | 6 |
| RecommendationPersonalizationTest | 13 |
| RecommendationDataAuditTest | 2 |
| RecommendationImageResolverTest | 4 |

실행은 `testQaUnitTest`에 위 7개 class의 `--tests` filter를 지정했다. 새 규칙 56개와 기존 추천·개인화·cycle·이미지 관련 39개다. 목표 없음·실제 1,850/2,000 예제·±5/10%/차이 안내·4끼 eligibility·중복·기록·취향/제외/알레르기·snapshot·교체·alternate를 포함한다. 실제 메뉴 조합에서 12 kcal 아침을 선택하던 배분 문제는 범위 우선 탐색으로 수정하고 실제 1,500 kcal 원본 회귀를 추가했다.

[최종 Unit 집계](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/unit-results.json), [최종 코드 빌드/Unit 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/meal-budget-build.txt). `git diff --check`도 통과했다.

## 15. Targeted Instrumentation

Samsung **SM-S948N**, Android **16 / API 36**, serial `R5KL20HFPAK`. 실행 **20**, 성공 **20**, 실패 **0**, 최종 소요 **24.613초**.

`TodayMealPlanUiTest` 18개와 `RecommendationPersonalizationUiTest`의 홈 관련 2개만 direct adb runner의 class/method filter로 실행했다. 전체 `connectedQaAndroidTest` 실행이 아니다.

검증 내용: 8테마/선택 전 음식 미표시, 지원 7테마, slow 제한, 현재 목표 유지, 기록된 아침 잠금/잔여 예산, dislike 및 실제 preference, 끼니 교체, 다른 조합, same-day/설정 재검증, 목표 없음, 정확한 stable ID 상세와 portion·알레르기·한 번 섭취, 기존 간식 비활성 설정에서 새 하루 간식 확정, 홈 요약, 실제 292개 읽기 전용 생성.

360dp에서 앱 글자 1.30 × system fontScale 1.30에 해당하는 **합성 1.69**를 Compose `LocalDensity`로 주입해 읽기·카드 경계·48dp 선택 영역·버튼 접근·홈 non-scroll을 검증했다. 기기 OS의 전역 글자 설정을 바꾼 검증은 아니다. 초기 선택 카드의 intrinsic Text 폭 문제를 `fillMaxWidth`와 자연 줄바꿈으로 수정했다. 홈 CTA 통합 semantics에 맞추어 실제 클릭 Row tag를 검증하도록 갱신했고 최종 20개가 통과했다.

[최종 targeted 원본 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/targeted-complete.txt), [집계](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/targeted-results.json).

## 16. QA

관련 검사 이후 최종 `assembleQa` **BUILD SUCCESSFUL**, exit 0. 최신 APK를 `adb install -r -t`로 **Success** 업데이트 설치했다. QA applicationId는 **`com.example.healthcare.qa`**, QA versionName은 기존 suffix를 포함한 **1.0.5-qa**다. 제품 package를 설치/제거/clear하지 않았다.

Samsung foreground에 **오늘의 추천 테마 선택 화면**을 준비했다. 현재 저장된 다이어트는 점심 교체 후 **1,498 / 1,500 kcal**다. 오늘 기록은 실제 기존 상태 그대로다.

사용자 직접 확인 순서:

1. 준비된 오늘의 추천 테마 선택 화면에 진입한다.
2. 음식이 바로 나오지 않는지 확인한다.
3. 아래로 스크롤해 저속노화식 스타일까지 8개 테마를 확인한다.
4. 다이어트를 선택한다.
5. 아침·점심·저녁·간식을 확인한다.
6. 실제 목표와 총 kcal를 확인한다.
7. 점심 카드의 `다른 메뉴`로 점심만 바뀌는지 확인한다.
8. `다른 조합 보기`로 같은 테마의 다른 하루 식단을 확인한다.
9. 뒤로가서 벌크업을 선택하고 목표가 유지되는지 확인한다.
10. 건강하게를 확인한다.
11. 치팅데이를 확인한다.
12. 저속노화식 스타일의 데이터 부족 안내를 확인한다.
13. 앱을 다시 실행하고 기존에 저장한 테마를 선택해 같은 날 식단이 유지되는지 확인한다.

처음 추천 탭에 들어오면 재실행 후에도 선택 화면을 표시한다. 홈의 `오늘 식단 보기`로는 검증된 저장 식단에 바로 들어갈 수 있다. 다른 테마를 실제 생성하면 마지막으로 생성한 테마가 저장된다.

캡처는 Git ignore 대상 build output에만 저장했다. 전환/로딩 중 캡처를 결과 화면이 안정된 캡처로 다시 저장했다.

| 캡처 | 경로 |
|---|---|
| 테마 선택 상단 | [theme-selector.png](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/theme-selector.png) |
| 테마 선택 하단·8번째 테마 | [theme-selector-bottom.png](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/theme-selector-bottom.png) |
| 다이어트 | [diet-day-plan.png](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/diet-day-plan.png) |
| 벌크업 | [bulk-day-plan.png](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/bulk-day-plan.png) |
| 건강하게 | [healthy-day-plan.png](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/healthy-day-plan.png) |
| 치팅데이 | [cheat-day-plan.png](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/cheat-day-plan.png) |
| 저속노화식 스타일 | [slow-aging-style-plan.png](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/slow-aging-style-plan.png) |
| 점심 교체 | [meal-replaced.png](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/meal-replaced.png) |
| 재실행 후 1,498 kcal 결과 | [diet-relaunch-plan.png](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/diet-relaunch-plan.png) |
| 최종 준비 화면 | [theme-selector-final.png](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/preview/theme-selector-final.png) |

[최종 assemble 로그](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/assemble-final.txt), [설치 결과](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/install-final.txt), [최종 QA 상태](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/final-qa-state.json), [실제 UI snapshot 비교](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/manual-ui-results.json).

## 17. 기존 기능 보호

| 기능 | 이번 작업의 영향과 확인 범위 |
|---|---|
| onboarding·검색·두부/달걀·과일·grouping | 이번 하루 식단 작업에서 관련 구현을 바꾸지 않음; 전체 UI 재검증은 수행하지 않음 |
| 빠른 기록·즐겨찾기 | 기존 데이터 보존; 추천에서 기존 즐겨찾기 읽기 연결; 전체 빠른 기록 회귀 미실행 |
| 기록 수정/삭제·홈 기록 navigation | 이번 작업의 저장/수정 navigation을 바꾸지 않음; 실제 QA 기존 기록 행 동일 |
| Home | 추천 영역만 하루 요약으로 변경; 360dp·합성 큰 글자·non-scroll 관련 테스트 통과 |
| 리포트·탄단지 합계 | 기존 리포트 구현 유지; 새 plan 합계는 확인 가능한 실제 값만 사용 |
| 추천 이미지/stable ID/영양 데이터 | 기존 292개 template·재료 CSV·image mapping 파일 SHA-256 동일; 이미지 resolver 및 원본 audit 관련 테스트 통과 |
| portion/먹었어요 | 기존 상세 계산과 기록 transaction 재사용; 정확한 메뉴 상세·알레르기·한 번 기록·간식 처리 격리 테스트 통과 |
| 운동·걸음 | 이번 작업에서 구현/저장 경로를 바꾸지 않음; 전체 권한/운동 UI 회귀 미실행 |
| 체중·에너지 목표 | 기존 설정은 읽기만 사용; 테마에 의한 목표 자동 변경 없음; QA 전후 목표/profile 행 동일 |
| updater | 이번 작업에서 구현·배포 정보를 바꾸지 않음; 전체 updater 회귀 미실행 |
| API29 | 기존 호환 구조를 사용하고 minSdk 유지; 이번 범위에서 API29 emulator/실기기 실행은 하지 않음 |

위의 “구현을 바꾸지 않음”은 전체 회귀 성공의 주장과 구분한다. [보호 파일 hash](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/daily-plan-qa/protected-source-hashes.json).

## 18. 전체 회귀검증

사용자 지시에 따라 이번 작업에서는 **전체 connectedQaAndroidTest, 전체 Lint, Release build를 실행하지 않았다**. 관련 Unit 95개와 Samsung targeted 20개만 실행했다. API29 managed 전체 실행도 이번 범위에 포함하지 않았다. 이전 작업의 전체 회귀 결과를 이번 새 코드의 전체 회귀 결과로 사용하지 않는다.

## 19. 버전/배포

versionCode **6**, versionName **1.0.5** 유지. QA suffix만 기존대로 `-qa`다. GitHub Release 변경 **0**, Supabase latest 변경 **0**, Vercel production 변경 **0**, production 설치/배포 **0**.

공개 제품의 읽기 전용 identity는 `versionCode=6`, `versionName=1.0.5`, `lastUpdateTime=2026-09-29 23:17:20`으로 작업 전후 동일하다. 공개 제품 DB를 초기화하거나 읽고 쓰는 작업을 하지 않았다. Git commit/PR/외부 게시도 수행하지 않았다.

## 20. 남은 문제

이번 관련 검증의 미해결 테스트 실패는 **없음**. 다음은 확인된 데이터/검증 범위의 한계다.

- 저속노화식 스타일은 원재료 식품군 데이터 부족으로 제한된다. 활성 식단을 만들려면 출처가 확인된 taxonomy와 원재료 메타데이터가 필요하다.
- 가볍게/든든하게의 현재 상대적 후보 구간과 표준 portion에서는 일부 실제 목표를 ±10% 안으로 맞추지 못한다. 실제 차이를 표시하며 목표나 영양값을 바꾸지 않는다.
- 전체 회귀·Lint·Release·API29/Note9 실기기 검증은 이번 요청에서 수행하지 않았다.
- 앞선 공개 제품 1.0.5의 “저장 후 뒤로가기로 기록창 재등장” 제보는 발생 버전만 확인됐으며 원인 확정은 별도 재현이 남아 있다. 이번 하루 식단 QA로 공개 제품의 해당 원인이 해결됐다고 보고하지 않는다.
