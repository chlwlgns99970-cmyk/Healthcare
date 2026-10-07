# 레시피 영양 연결 / 놀부 메뉴 / 미분류 메뉴 완료 보고

검증 완료: 2026-10-05 KST. 공식 원문 수집·기존 캐시 확인일은 각 원문에 기록된 2026-10-04를 유지한다.

1. **STATUS = COMPLETE**
   요청의 A 조건 충족: 식약처 MFDS-247 알감자구이의 원문 주요 재료 두 가지를 모두 공식 영양정보에 연결하고 Samsung 실제 화면에서 검증했다. 부분 연결 59개를 완전 연결로 계산하지 않았다.

2. **재료별 kcal**
   - 기존 공식 레시피 516개 / 재감사 516개 / 원본 SHA256 검증 실패 0개.
   - 연결 레시피 12 → **60개**, 연결 음식 36 → **201개**.
   - 완전 연결 **1개** / 부분 연결 **59개** / 영양 연결 행 **474개**.
   - 실제 상세 또는 기준량 검토가 필요한 수동 입력 화면에 표시 가능한 음식 **201개**.
   - 완전 연결 레시피가 표시되는 감자구이 음식 ID **4개**. 기존 주재료 정보는 확정 배합으로 바꾸지 않았다.
   - 기존 캐시 USDA SR Legacy 공식 CSV archive의 해시를 manifest와 비교 후 Energy/KCAL 열을 추출했다. 재료 영양값은 별도 참고 sidecar에만 저장한다. FoodItem 추가·수정 0개.
   - 공백 normalization과 다진 마늘·다진 양파·통마늘 등 명시적 alias **18개**, 알감자구이 전용 생감자 identity **1개**. 조리 상태·껍질·품종을 무조건 제거하지 않는다. 큰술/컵/ml 중량 환산 **0개**.
   - 기존 제한 원인: 이전 구현은 오이·양배추 2종 및 레시피 allowlist 12개만 허용했다. 이제 모든 기존 레시피와 추가 양념 재료 필드를 감사한다.

3. **연결 실패 원인**
   개별 레시피에 여러 원인이 있으므로 아래는 중복 가능한 수량이다.
   - 검토된 nutrition identity에 연결하지 못한 재료 **2,276항목**, 해당 레시피 **460개**. 이 수치는 공식 영양 DB에 존재하지 않는다는 뜻이 아니다. 미검토 재료·품종·가공상태·조리상태·복합재료를 포함한다.
   - 단위 미확정·무중량·약간·가정 단위·원문 잔여 표기 **2,134항목**, 해당 레시피 **289개**. 안전하게 연결된 g 항목은 독립적으로 유지한다.
   - 동일 음식명에 여러 레시피 identity **216개** / 정확한 generic 음식 identity 없음 **209개** / 정확한 generic reference **90개** / 명시적으로 검토한 알감자구이→감자구이 참고 관계 **1개**.
   - 재료명 mismatch는 nutrition 미검토 항목에 포함하며 별도 중복 숫자를 만들지 않았다. 이름을 지우거나 유사 재료 값으로 강제 연결하지 않았다.
   - 레시피별 모든 재료·중량·연결 또는 실패 이유: [audit.json](../recipe-calories/audit.json).

4. **실제 예시**
   - 감자구이 상세에 이름을 명시한 **알감자구이 공식 참고 레시피**, 원문 **4인분 전체**: 감자 **520g × 77kcal/100g = 400.4kcal**(화면 약 400), 소금 **2g × 0 = 0kcal**. 소금 0은 USDA의 실제 공식 값이며 미확인 값을 0으로 만든 것이 아니다.
   - 선택 음식 `kfind-d408-356000000-0001` 원문 영양값 **100ml 기준 123kcal** 유지. 기존 기준량 검토 정책에 따라 자동 기록 계산을 막고, 원문 기준값과 레시피를 수동 입력 화면에서 확인하게 한다. 감자 520g을 100ml에 맞추거나 1인분으로 임의 환산하지 않는다.
   - [식약처 원문](https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=247), [USDA 생감자](https://fdc.nal.usda.gov/food-details/170026/nutrients), [USDA 소금](https://fdc.nal.usda.gov/food-details/173468/nutrients).
   - 김밥 참고 레시피: 당근 **15g → 6.15kcal**, 오이 **13g → 1.95kcal**, 설탕 **1g → 3.87kcal**, 다진 마늘 **0.5g → 0.745kcal**. 밥·햄 등 미연결 재료가 있으므로 부분 연결 표시. 기존 김밥 공식값 **100g 140kcal** 유지.

5. **놀부부대찌개**
   - 본사 `https://www.nolboo.co.kr/`, `/pages/brand/boodae.asp`, `/brand/budae/menu.aspx`, `/brand/boodae/menu.aspx`, `/robots.txt`: 시간초과. `http://www.nolboo.co.kr/`: HTTP404. 사용할 HTML·JS·XHR·PDF 링크 자체를 확보하지 못했으며 JS-only 문제로 단정하지 않았다.
   - [공식 카카오 채널](https://pf.kakao.com/_Nxaxmxad): HTTP200이지만 현재 공개 HTML은 JS shell. 과거 메뉴 공지를 현재 전체 메뉴로 복사하지 않았다.
   - 대체 [공공 관광 매장 페이지](https://ydp.redtable.global/ko/store/28646) HTTP200, 매장명 **놀부부대찌개&철판구이**, 주소 **서울 신길로 39**를 확인. 원본 HTML의 실제 메뉴 카드·product ID·메뉴 이름을 파싱.
   - 메뉴 **7개**: 고기듬뿍김치찌개, 돼지고기 묵은지김치찜, 놀부햄김치찜, 우곱새, 놀부부대세트(2인), 우삼겹부대세트(4인), 통핑세트(4인). 이용권 제외 **1개**.
   - 브랜드 전체 최신 공통 메뉴로 단정하지 않고 **해당 공공 관광 매장 메뉴**로 출처·주소를 화면/기록 원문에 표시. nutrition **0개**, ingredient **0개**, allergen **0개**. 미확인 영양은 null 유지.
   - product detail 경로 8개는 HTTP200으로 목록 페이지에 redirect되어 상세 영양을 제공하지 않았다. 본사 영양표를 확보했다는 주장 없음.
   - 공식 지자체 추가 검색 경로 HTTP417. 조사 원문·실패 사유: [nolboo-source-audit.json](nolboo-source-audit.json), [nolboo-menu-details.json](nolboo-menu-details.json).

6. **Unknown category**
   - 변경 전 Kotlin generator/audit 재실행: **274개**.
   - 명확한 개별 메뉴명으로 자동 분류 **36개** / 공식 개별 설명을 검토한 정확한 ID 분류 **13개** / 기존 미분류 유지 **225개**.
   - 놀부 신규 메뉴 중 미분류 **4개**, 최종 **229개**. 신규 3개는 한식.
   - 기존 미분류 유지 사유: 지원하는 수프 category 없음 **16**, 재료·토핑·분량 identity **32**, 혼합 세트 **27**, 모호한 이름 또는 기존 taxonomy 미지원 **150**. 놀부 3개 혼합 세트와 우곱새 1개 추가.
   - 브랜드 업종을 개별 메뉴에 강제 적용하지 않았다. 예: 아보홀릭은 공식 설명의 샌드위치 근거, 후추 테바나카는 닭날개 튀김 근거, 15cm 감자전은 명백한 메뉴명으로 분류. 아보홀릭 세트는 unknown 유지.
   - 전체 274개별 후보·근거·분류 가능 여부: [all-unknown-menu-audit.csv](all-unknown-menu-audit.csv), [menu-summary.json](menu-summary.json).

7. **수정 파일**
   - Android: `RecipeCaloriePolicy.kt`, `FoodMetadataStore.kt`, `FoodMenuCategoryPolicy.kt`, `ReviewedMenuCategorySources.kt`, `FranchiseCatalog.kt`, `OfficialFranchiseMenus.kt`, `AddRecordScreen.kt`, `WellnessManualRecordScreen.kt`, `SmartFoodInputScreen.kt`.
   - 테스트: `RecipeMenuCompletionTest.kt`, `CatalogQaAuditTest.kt`(메뉴 감사 전용 진입점), `CatalogExpansionSamsungTest.kt`(신규 targeted methods).
   - 도구: `build_recipe_calorie_references.py`, `import_franchise_expansion.py`(신규 snapshot 읽기), `research_nolboo_completion.py`, `capture_nolboo_menu_details.py`, `integrate_nolboo_public_menus.py`, `build_reviewed_menu_categories.py`, `audit_recipe_menu_completion.py`, `test_recipe_menu_completion.py`, `qa_recipe_menu_preservation.py`.
   - 생성 데이터: `recipe_ingredient_estimates.csv`, `recipe-calories/audit.json`, `recipe-calories/reviewed-nutrient-facts.json`, 이 디렉터리의 메뉴 원문/감사/snapshot/baseline/보고서.
   - 검색 상태·대표 브랜드 필터 로직 수정 없음. `SmartFoodInputScreen` 변경은 신규 공공 메뉴 출처 문구만 해당.

8. **테스트**
   - Python targeted data test **10 PASS**: 공식 source parser, 중량·미확정 단위, alias·cooked/raw 안전성, nutrition identity, 부분/완전 연결, kcal 계산, 메뉴 loader, 274개 감사·immutable asset 검증.
   - Kotlin targeted **12 PASS**: 신규 정책 4 + 메뉴 정책 7 + 메뉴 전수 감사 1. 최종 reference 필드 변경 후 신규 정책 4개 재검증 PASS.
   - `assembleQa`, `assembleQaAndroidTest` PASS. 전체 Unit/connected/Lint/Release 실행 없음.

9. **Samsung QA**
   - SM-S948N/API36, QA APK `adb install -r -t` 성공.
   - 최종 실행 **OK (5 tests), 16.543초**: 완전/부분 recipe UI 및 김밥 유지, 놀부 실제 메뉴/출처/미확인 nutrition, 신규 분류 필터, 완전 recipe가 있는 음식의 실제 저장값, 짜파게티 공식정보·상세·기록·수정 610kcal 보존.
   - 초기 instrumentation 반환형/수동 화면 경로 실패는 수정 후 재실행했으며 PASS로 계산하지 않음. 최종 로그: `app/build/recipe-menu-completion/samsung-final.log`.

10. **DB 및 사용자 데이터**
    - DB **8 → 8**, migration **0개**.
    - 요청 시작에 QA databases/shared_prefs/files **16개 파일, 72,343,552바이트** 백업.
    - 마지막 테스트 후 원문 백업 복원·비교 **PASS**: 모든 허용 private file bytes 동일, SQLite 모든 행·schema·sequence 동일, changedTables **0**, QA firstInstallTime 유지, 제품 package identity 유지.
    - 기존 food_items/product_items/franchise_official_items **3개 asset의 파일 bytes 모두 동일**. 음식 ID·영양값·기준량 변경 **0개**.
    - 최종 검증: `app/build/recipe-menu-completion/preservation-restore.json`. QA는 데이터 복원 후 종료된 상태이며 검증 APK는 설치되어 있다.

11. **남은 한계**
    - 완전 recipe 1개를 제외한 59개는 부분 연결. 김밥의 밥·햄·단무지 전체 구성은 완전 연결되지 않았다.
    - 같은 이름은 원문 recipe reference이며 선택 음식의 확정 배합/공식 serving이 아니다. 사용자 기록/추천/공식 총 kcal를 대체하지 않는다.
    - 놀부 본사 전체 현행 메뉴·nutrition은 미확보. 확보한 7개는 한 매장 공개 자료.
    - unknown229개를 0개로 맞추지 않았다. 공개 출처의 추가 검토와 지원 taxonomy 확대 없이 억지 분류하지 않는다.

12. **QUESTION_REQUIRED = 없음**

13. **Production**
    versionCode **7**, versionName **1.0.6**, production 변경 **0건**. 제품 APK overwrite/uninstall/pm clear 없음. GitHub Release/Supabase/Vercel 배포 없음.
