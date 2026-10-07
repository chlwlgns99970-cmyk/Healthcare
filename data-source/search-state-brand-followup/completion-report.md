# 검색 상태·브랜드 필터·공식 레시피 참고 열량 완료 보고

요청: dbb0915f-c711-4072-889e-c412bb1f5ed1. 검증: 2026-10-04, Samsung SM-S948N / API 36.

1. **STATUS: COMPLETE.** 세 요청 범위 구현 및 targeted 검증 완료.

2. **검색 상태.** 음식 선택이 `searchQuery`, 결과 목록, 브랜드, 필터를 비우던 원인이었다. 상세 진입은 입력 모드만 바꾸며 검색 조건을 보존한다. 브랜드 진입 직전 상태를 ViewModel에 보관하고 목록 복귀 시 복원한다. 메뉴 검색은 브랜드 검색과 별도 조건이다. 같이 먹은 음식 검색 종료도 원래 검색 조건을 복원한다. 음식 목록과 브랜드 메뉴 목록은 화면 상위의 별도 `rememberLazyListState`를 재사용한다. 김밥 검색→상세→뒤로의 검색어·필터·결과·스크롤 위치, 피자스쿨 검색+피자→브랜드→뒤로의 상태 복원 PASS. 프로세스 종료 후 영구 저장은 추가하지 않았다.

3. **브랜드 필터.** 메뉴를 하나라도 판매하면 해당 브랜드가 그 메뉴 업종의 브랜드 필터에 들어오던 원인이었다. 대표 업종으로 브랜드를 필터링하고 브랜드 내부 메뉴는 기존 개별 메뉴 taxonomy를 유지한다. 전체 89개 registry와 실제 repository의 모든 표시 필터를 검사했다. 73개 브랜드에서 교차 판매 필터 노출을 발견했고, 불필요한 브랜드·필터 연결 271개를 제거했다. 사용자 결정에 따라 KFC=치킨·햄버거, 본죽&비빔밥=죽·한식의 다중 대표 업종을 추가했다. 남은 정책 mismatch 0, 대표 업종 unresolved 0. 89개 모두 전체 검색에 포함된다. 놀부부대찌개는 메뉴 원본이 없어 메뉴 개수 대신 자료 확인 필요로 표시한다. 대표 브랜드가 없는 사이드 필터는 브랜드 목록에서 숨기고 내부 메뉴 필터에서는 유지한다.

4. **대표 검증.** 실제 packaged DB+repository에서 굽네치킨+피자 제외/치킨 포함, 피자스쿨+피자 포함/치킨 제외, 지코바+치킨 포함/피자 제외 PASS. 실제 UI에서도 피자 목록의 굽네치킨 부재와 치킨 목록의 굽네치킨 노출 PASS. 브랜드 상세의 다른 업종 메뉴는 삭제하지 않았다.

5. **재료별 kcal.** 기존 식약처 식품안전나라 공개 조리법 및 농촌진흥청 공개 조리자료 516개 원본 레시피 identity를 조사했다. 기존 일반 음식명과 정확히 일치하는 레시피 후보는 306개, 해당 식품 identity는 512개였다. 여러 레시피가 동일 음식명에 대응하면 자동 선택하지 않는다. 원본 HTML의 SHA-256 및 조리법을 확인한 12개 참고 레시피를 36개 기존 식품 상세에 연결했다. 정확한 g 사용량과 기존 USDA SR Legacy 원재료 identity를 연결한 부분 예상 열량이 36개 상세에서 표시된다. 연결된 원재료는 오이(FDC 168409, 100g 15kcal), 양배추(FDC 169975, 100g 25kcal)다. 예: 농촌진흥청 김밥 참고 레시피의 오이 13g은 1.95kcal, UI는 약 2kcal다. 식약처 양상추샐러드 원문 4인분의 오이 120g은 18kcal다.

   기존 확정/부분 재료명이 있는 식품은 599개다. 참고 레시피의 재료량 연결 식품 36개, 부분 영양 연결/예상 열량 표시 식품 36개, 전체 레시피 재료의 영양 연결 완료 0개다. 참고 레시피는 선택한 식품의 확정 배합으로 인증되지 않았으며, UI에 이를 명시하고 선택량·기록 kcal에 반영하지 않는다. 원문 전체 재료량과 인분 수를 구분하고 미확인 인분 수는 추정하지 않는다. 재료량과 개별 영양 데이터로 계산한 예상값이라는 안내를 표시한다. 미연결 재료를 0kcal로 취급하거나 합계를 완성 음식 kcal에 맞추지 않는다. 주요 미연결 이유: 여러 recipe identity, 단위 없는 숫자/약간, 가공 상태·품종 불명확, 정확한 개별 영양 identity 부재.

6. **이번 수정 파일.**

   - app/src/main/java/com/example/healthcare/ui/viewmodel/AddRecordViewModel.kt
   - app/src/main/java/com/example/healthcare/ui/screens/AddRecordScreen.kt
   - app/src/main/java/com/example/healthcare/ui/screens/SmartFoodInputScreen.kt
   - app/src/main/java/com/example/healthcare/domain/FranchiseCatalog.kt
   - app/src/main/java/com/example/healthcare/data/repository/NutritionRepository.kt
   - app/src/main/java/com/example/healthcare/data/FoodMetadataStore.kt
   - app/src/main/java/com/example/healthcare/domain/RecipeCaloriePolicy.kt (추가)
   - app/src/main/assets/fooddata/recipe_ingredient_estimates.csv (추가)
   - app/src/test/java/com/example/healthcare/PhotoManualRecordViewModelTest.kt
   - app/src/test/java/com/example/healthcare/SearchRecipeFollowupTest.kt (추가)
   - app/src/androidTest/java/com/example/healthcare/CatalogExpansionSamsungTest.kt
   - tools/build_recipe_calorie_references.py (추가)
   - tools/audit_brand_search_filters.py (추가)
   - tools/qa_search_followup_preservation.py (추가)
   - data-source/recipe-calories/audit.json (추가)
   - data-source/search-state-brand-followup/all-89-brand-filter-audit.csv (추가)
   - data-source/search-state-brand-followup/summary.json (추가)
   - 이 완료 보고서 (추가)

   작업 시작 전 이미 존재했던 다른 변경은 이번 작업 목록에 포함하지 않는다.

7. **Targeted 테스트.** `SearchRecipeFollowupTest` 2개, `PhotoManualRecordViewModelTest.foodSearchConditionsSurviveDetailAndManualBack`, `franchiseSearchConditionsSurviveBrandAndFoodDetailBack` 총 4개 PASS. 비기본 음식 필터와 companion 검색 복귀도 확인했다. 레시피 식품별 quantity/nutrient identity 누락 거부, names-only 식품 미표시, 120g×15/100=18kcal 계산, 전체 89개 대표 업종 및 0브랜드 필터 숨김을 검사했다. `assembleQa`, `assembleQaAndroidTest` PASS. 전체 회귀/Lint/Release/전체 connected 테스트는 실행하지 않았다.

8. **Samsung QA.** 다음 3개 method PASS (`app/build/search-recipe-followup/samsung-final.log`, OK (3 tests)):

   - searchDetailBackKeepsQueryFilterAndScrollAndShowsRecipeReference
   - brandSearchBackKeepsPizzaSchoolQueryAndPrimaryFilter
   - all89RepresentativeBrandFiltersAndQueryCombinationsMatchActualRepository

   실제 packaged asset을 fixture 전용 in-memory DB에 로드하고 실 구현 `AddRecordScreen`, ViewModel, repository를 사용했다. 360dp, 앱 글자 1.30 배율이다. 사용자 DB에 테스트 식사를 저장하지 않았다. 최종 QA APK를 `adb install -r -t`로 덮어 설치했다. 테스트 selector 오류는 수정 후 통과했으며, 휴대폰 사용으로 테스트 Activity가 종료된 실행은 통과로 간주하지 않고 사용자 준비 후 재검증했다.

9. **DB 및 보존.** DB v8, migration 없음. 기존 식품 67,354개 ID/영양/기준량 변경 없음. 불변 QA 스냅샷을 테스트 전에 캡처했다. 테스트 후 동일 스냅샷을 QA 전용 경로에 복원했고 전용 파일 16개 바이트·모든 DB 행·schema·sequence 동일 PASS. QA 최초 설치 시각 유지 PASS. `app/build/search-recipe-followup/preservation-restore.json`의 changedTables=[], allAllowlistedPrivateFileBytesIdentical=true, productIdentityUnchanged=true, qaFirstInstallPreserved=true. 제품 앱은 package 정보 조회만 했으며 제품 데이터에 접근하지 않았다. QA/제품 uninstall 및 pm clear 0회.

10. **남은 데이터 한계.** 36개 표시도 전체 레시피가 아닌 검증된 일부 원재료의 참고 예상값이다. 선택 식품의 확정 배합으로 사용할 수 있는 전체 레시피 kcal 분해는 아직 0개다. 놀부부대찌개의 공식 메뉴 자료는 미확인 상태로 표시한다. 이 상태를 값 0이나 안전한 재료 구성으로 추정하지 않는다.

11. **QUESTION_REQUIRED.** 없음. 다중 대표 업종 정책은 사용자 답변을 반영했다.

12. **production.** versionCode=7 / versionName=1.0.6, production 변경·배포 0건. updater·홈·추천 저장 정책 변경 없음.

## 감사 산출물

- [89개 브랜드 audit](all-89-brand-filter-audit.csv)
- [브랜드 필터 요약](summary.json)
- [공식 레시피 연결 audit](../recipe-calories/audit.json)

공식 공개 레시피 HTML은 기존 `app/build/food-quality-qa/recipe-source` 스냅샷을 사용한다. 레시피 연결 CSV는 레시피 URL/원본 digest/checkedAt과 영양 식품 ID/URL을 함께 보관하므로 출처가 서로 섞이지 않는다.
