1. **STATUS = COMPLETE**. 공식 source 전수 판정 완료. 미검토 identity/단위/menu 모두 **0**. 연결 불가와 원문 미확정은 아래 사유로 확정했으며 성공으로 세지 않았다.

2. **Recipe 전체**: 516개 / ingredient 4,258행 / 검토 완료 4,258행 / 미검토 0행. 괄호 속 g·개수 병기는 한 재료에 귀속하며 이전 parser 조각을 재료로 부풀리지 않았다. [recipe-final-audit.json](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/full-adjudication/recipe-final-audit.json)

3. **Ingredient nutrition identity**: Before 미검토 2,276건. After 연결 2,248, 공식 nutrition 없음 1, identity 모호 1,719, recipe identity 문제 0, 비열량/공정 제외 290, 기타 0, 미검토 0. 독립 identity 판정이며 amount까지 계산 가능한 재료는 1,827행이다.

4. **Ingredient unit**: Before 미확정 2,134건. After 직접 중량(g/kg) 3,068, 공식 동일 source 계량스푼 환산 11, 안전한 g 환산/사용량 근거 없음 1,035, 비열량 공정 제외 144, 미검토 0. ml/L를 1g=1ml로 바꾸지 않았다. 공식 환산 11행은 고춧가루 작은술·물엿 큰술에 한정하며 복수 원문 중량이 모두 일치하는 경우만 사용했다. 개수·컵·충돌값은 적용하지 않았다.

5. **Recipe 결과**: Before complete 1 / partial 59. After complete 13 / partial 407 / unlinkable 96. 정확한 일반 음식과 고유 recipe identity로 공개 연결한 것은 recipe 72개, 음식 230개, 완전 연결 공개 recipe 3개, 참고 ingredient asset 1,015행이다. 공식 food kcal/ID/serving은 변경하지 않았다.

6. **실제 complete 예시 3개**. 다음 예상 kcal는 원문 전체 재료량 기준이고 공식 음식 값은 개별 선택 food의 기준량이다. 서로 다른 기준이며 합계를 맞추지 않았다. 고형 음식의 ml 표기는 정확한 K-FIND 원문 행과 일치해 유지했다.

   갈치구이: 갈치 280g → 417.2 kcal ([갈치, 생것](https://www.nics.go.kr/food/kfi/fct/fctIntro/list?menuId=PS03562)); 소금 4g → 0 kcal ([Salt, table](https://fdc.nal.usda.gov/food-details/173468/nutrients)). 공식 레시피 4인분 전체 재료량. 선택 음식 공식 100ml당 147 kcal. [공식 레시피](https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=158) / food ID `kfind-d408-355000000-0001`.

   알감자구이: 감자 520g → 400.4 kcal ([Potatoes, flesh and skin, raw](https://fdc.nal.usda.gov/food-details/170026/nutrients)); 소금 2g → 0 kcal ([Salt, table](https://fdc.nal.usda.gov/food-details/173468/nutrients)). 공식 레시피 4인분 전체 재료량. 선택 음식 공식 100ml당 123 kcal. [공식 레시피](https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=247) / food ID `kfind-d408-356000000-0001`.

   꽃게찜: 꽃게 350g → 269.5 kcal ([게, 꽃게, 생것](https://www.nics.go.kr/food/kfi/fct/fctIntro/list?menuId=PS03562)). 공식 레시피 4인분 전체 재료량. 선택 음식 공식 100ml당 52 kcal. [공식 레시피](https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=263) / food ID `kfind-d407-357000000-0001`.

7. **김밥**: recipe 2, ingredient 37, 계산 연결 13, 비열량 제외 4, identity 모호 6, 단위/사용량 미확정 14. 일반 김밥은 18행 중 10행 계산 연결; 충무김밥은 19행 중 3행 연결. 쌀의 도정/종류, 고기 부위·등급, 치즈 종류, 식용유 종류, 간장 제법, 약간 양념의 양을 추정하지 않았다. UI에서 김 1.5g 약 3 kcal·계란 30g 약 42 kcal·오이 13g 약 2 kcal와 부분 연결 안내 확인. 김밥 공식 100g 140 kcal 및 기록 계산 유지. [김밥 실제 앱 렌더링 화면](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/full-adjudication/screens/kimbap-ingredients.png)

8. **Unknown menu**: Before 229. After category assigned 157 / category unresolvable 72 / unreviewed 0. 모든 229개를 실제 패키지 lookup 결과와 대조해 일치. 브랜드 업종 추정 사용 0. [menu-final-audit.json](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/full-adjudication/menu-final-audit.json)

9. **놀부부대찌개**: 공식 홈페이지·기업/모바일/robots/sitemap 접근 결과, 공식 카카오 채널 전체 21개 공지, 연결된 주문 서비스 HTML/Next.js/API를 확인했다. 인기 목록 16개에서 끝내지 않고 공개 매장 47개의 5페이지와 모든 메뉴 API를 확인하여 제육놀부세트를 추가 확보했다. 부대 코드 0027에 해당하는 브랜드 주문 메뉴 **17개/인분 옵션 47개** 반영; 기존 공공 관광의 특정 신길로 매장 **7개** 유지. 47개 요청은 동일 브랜드 카탈로그 응답으로, 각 매장의 현재 판매 확정 근거로 사용하지 않았다. nutrition 0 / 전체 원재료 0 / 부분 메뉴 설명 14 / 원산지 선언 9 / allergen 0. 나머지 3개 설명은 미제공. 2022~2023 원문 갱신일과 현재 판매 미확인을 표시하며, 공식 홈페이지의 완전한 현재 판매 메뉴는 검증 불가로 명시했다. 보쌈 코드 메뉴는 부대 브랜드에 넣지 않았다. [nolboo-order-catalog-audit.json](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/full-adjudication/nolboo-order-catalog-audit.json) / [nolboo-branch-menu-audit.json](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/full-adjudication/nolboo-branch-menu-audit.json) / [출처 범위 화면](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/full-adjudication/screens/nolboo-brand-scope.png)

10. **Nutrition basis audit**: 전체 67,354행 / 정확한 원본 food code 66,761행 비교. suspicious 15,676 / source confirmed 15,666 / parser corrected 0 / unresolved 10. 의심 태그는 중복될 수 있다: SOLID_WITH_VOLUME_BASIS=4,015, REFERENCE_LARGER_THAN_PACKAGE=8,751, BEVERAGE_WITH_MASS_BASIS=4,712. 잔여 10건은 원문 payload 없는 기존 메가커피 9건과 동일 품목번호의 source 기준량 차이 1건이다. 근거 없이 값을 통합/수정하지 않았다. [nutrition-basis-audit.json](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/full-adjudication/nutrition-basis-audit.json)

11. **실제 수정 파일**: 아래 링크에 이 요청의 앱 데이터/lookup/출처 문구/테스트 및 source 파이프라인 파일을 나열했다. 3개 기존 nutrition asset의 원본 bytes와 기존 metadata 70,016행의 값은 전부 보존했고, 새 metadata는 놀부 메뉴 전용 17행이다. 음식 상세의 구조, 기록·추천·홈·업데이트·DB schema는 수정하지 않았다. [정확한 파일 목록](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/full-adjudication/modified-files.txt) / [existing-metadata-preservation.json](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/full-adjudication/existing-metadata-preservation.json)

12. **Targeted tests**: Data 11 PASS; Kotlin Unit 12 PASS; Samsung 서로 다른 targeted 검증 7 PASS. 전체 test suite/Lint/Release build 미실행. 삼성 6개 기능 테스트 + 229개 분류 전수 런타임 테스트 1개이며, 앱 렌더링 캡처 방식 변경 후 해당 화면 3개도 재검증 PASS. [targeted-data.log](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/full-adjudication/targeted-data.log) / [targeted-unit.log](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/full-adjudication/targeted-unit.log) / [samsung-final.log](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/full-adjudication/samsung-final.log) / [samsung-runtime-menu-audit.log](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/full-adjudication/samsung-runtime-menu-audit.log) / [samsung-final-screen-verification.log](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/full-adjudication/samsung-final-screen-verification.log)

13. **Samsung QA**: SM-S948N / Android 16 / API36 / R5KL20HFPAK. 완전 연결 알감자구이, 김밥과 부분 안내, 연결 불가 보리밥의 fake kcal 부재, 공식 총 kcal/기록 저장값 보존, 놀부 브랜드/매장 출처 분리, 새로 분류한 동백 도시락·죠스쿨 음료, 짜파게티 610 kcal 및 영양 macro/상세·편집값 확인 PASS. QA 앱만 install -r; 제품 앱 제거/clear/덮어쓰기 없음. [완전 연결 화면](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/full-adjudication/screens/complete-recipe.png) / [연결 불가 화면](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/full-adjudication/screens/unlinkable-recipe.png)

14. **DB**: version 8→8 / migration 0. 작업 전 snapshot으로 복원 후 모든 DB schema/테이블 일치, changedTables=[]; 보존 대상 private 파일 16개/72,343,552 bytes 동일; QA firstInstallTime 유지. 복원 이후 QA를 다시 실행하지 않았다. [preservation-verify.json](C:/Users/young/AndroidStudioProjects/Healthcare/app/build/full-adjudication/preservation-verify.json)

15. **최종 연결 불가 사유**: 계산 기준 identity 모호 1,241행, 단위/실제량 환산 불가 899행, 공식 nutrition 없음 1행(주꾸미 먹물). 비열량/공정 290행은 계산 제외. 독립 identity/단위 집계는 3·4번과 다르며 같은 행의 복수 제약을 중복 합산하지 않은 최종 계산 상태다. 메뉴 분류 불가 72개는 식품 종류를 현 taxonomy로 대응할 근거 부족, 혼합 세트, 설명 미제공 등 개별 사유를 확정했다. recipe 공개 연결이 안 된 경우는 정확한 일반 음식 없음 또는 복수 recipe identity 등이며 별도 artifact에 전수 기록했다. 새 놀부 메뉴 17개도 분류 12/불가 5로 각각 이유 기록. [ingredient-decisions.json](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/full-adjudication/ingredient-decisions.json) / [nolboo-order-category-audit.json](C:/Users/young/AndroidStudioProjects/Healthcare/data-source/full-adjudication/nolboo-order-category-audit.json)

16. **QUESTION_REQUIRED = false**. schema/정책 추가 결정 필요 없음. source 부족 항목은 미검토로 남기지 않고 최종 이유로 기록했다.

17. **Production**: versionCode=7 / versionName=1.0.6 / production 변경=0. 제품 package identity와 lastUpdateTime은 작업 전과 동일. GitHub Release·Supabase production·Vercel 변경 없음.
