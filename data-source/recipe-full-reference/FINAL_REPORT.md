1. STATUS: PARTIAL

2. Recipe 전체: 516개.

3. Before: original complete 41 / partial 409 / unlinkable 66.

4. After: ORIGINAL_COMPLETE 41 / REFERENCE_COMPLETE 233 / PARTIAL 233 / UNRESOLVED 9.

5. 데이터 완전 상태 41+233=274/516. 실제 앱 완전 구성 제공 262/516. 원본 complete 중 12개는 현재 정확한 일반 FoodItem 연결 대상이 없어 차이가 남는다. 기존 음식 식별자를 임의로 추가하거나 유사 음식에 연결하지 않았다.

6. Ingredient: linked 2357→2382 / excluded 154→154 / unresolved 1747→1722. 새 원본 연결 25행 중 선택된 원본 composition asset에 실제 추가된 것은 3개 레시피·6개 foodId 행(모두 설탕)이다. 나머지 19행은 판정 기록에는 연결됐지만 현재 선택된 원본 composition으로 출판되지 않았다.

7. 원인별: identity 850→850 / unit 332→307 / amount 563→563 / nutrition 1→1 / recipe source 1→1. 기본 원인 분류이며 identity와 unit 같은 복수 축을 중복 집계하지 않았다.

8. 공식 참고 구성: 확보 382 / 출판 136 / foodId 468 / asset 3785행. 미출판 246구성은 중복 후보·정확한 음식 대상 없음 등의 사유로 선택하지 않았다. 미출판을 전부 영양 연결 실패로 계산하지 않는다. 원래 미해결 1747행 가운데 928행은 원본 그대로 미해결일 수 있지만 별도 완전 참고 구성으로 해당 음식 설명이 제공된다.

9. 기존 자료: 공식 중량 23/23행을 두 독립 카드의 전체 재료 문구에 반영, nutrition 9/9행 예상 kcal로 표시. 238개 충돌 전부 재검토; 설탕 스푼 25행 해결 / 213행 보류. 동일 재료의 1작은술 4.2g, 5mL/15mL 정의에 따른 큰술 12.6g 참고 환산이며 전역 spoon/cup 환산을 하지 않았다.

10. 김밥: 원본 linked18 / excluded1 / unresolved18, original complete 아님. 메뉴젠 김밥(햄) 9재료·276g의 별도 REFERENCE_COMPLETE. 원본 부분 구성, 메뉴젠 완전 구성, MFDS 두 부분 구성을 카드별로 분리하고 변형명·기관·출처·전체 분량을 유지한다. 밥210g 약319kcal 등 재료별 예상값을 실제 표시한다.

11. 새 완전 상태 233개 원본 recipe / 중복 제거 음식명 115개. 전체 목록: newly-complete-recipes.csv. 별도 원본 complete 증가는 0개.
음식명: 가오리찜, 가자미구이, 가자미조림, 가지전, 가지찜, 갈비탕, 감자볶음, 감자전, 감자조림, 감자탕, 고구마전, 고들빼기김치, 고등어조림, 고등어찌개, 고추전, 골뱅이무침, 굴무침, 굴전, 근대된장국, 기장밥, 김밥, 김치볶음, 김치전, 김치찌개, 깍두기, 깨죽, 깻잎전, 깻잎찜, 꼬막무침, 꼬막찜, 꽁치조림, 꽃게탕, 나박김치, 낙지볶음, 낙지전골, 느타리버섯볶음, 닭볶음탕, 닭죽, 대구탕, 더덕구이, 도미찜, 동태국, 동태전, 동태조림, 돼지갈비찜, 돼지고기볶음, 돼지고기장조림, 두부부침, 두부전골, 두부조림, 땅콩조림, 떡국, 떡볶이, 마늘쫑볶음, 마파두부, 매작과, 멸치볶음, 미숫가루, 미역국, 미역줄기볶음, 배추김치, 뱅어포구이, 버섯전골, 병어조림, 보리밥, 북어국, 북어찜, 비빔국수, 비빔냉면, 비빔밥, 삼계탕, 삼치구이, 상추겉절이, 새우튀김, 설렁탕, 수수밥, 수제비, 시금치나물, 알탕, 양미리조림, 연근조림, 열무김치, 오곡밥, 오리탕, 오이냉국, 오이생채, 오이소박이, 오징어찌개, 옥수수밥, 완두콩밥, 유채나물, 율무밥, 임연수구이, 잡곡밥, 잣죽, 전복죽, 조기구이, 증편, 차조밥, 찰밥, 청국장찌개, 총각김치, 칼국수, 콩국수, 콩나물국, 콩나물무침, 콩나물밥, 탕평채, 팥밥, 팥죽, 풋고추찜, 호박잎된장국, 호박죽, 흑미밥, 흰죽

12. 기관별 수집 구성 / 실제 출판 구성 / 출판 완전 구성 / 중복 foodId 확장 전 linked 재료:

   농촌진흥청 국립식량과학원 메뉴젠: 295 / 125 / 125 / 891

   식품의약품안전처: 2 / 2 / 0 / 9

   원광대학교·서울대학교 식품공학 학술논문: 1 / 0 / 0 / 0

   질병관리청 국민건강영양조사: 84 / 9 / 2 / 169

   영양·계량 source: RDA, MFDS/K-FIND, USDA. source-captures.json에 raw·SHA·URL·확인일, source-institution-summary.json에 구성 기관별 수치, research-route-audit.json에 추가 조사 결과를 기록했다. KDCA 조사 평균은 조리 레시피와 별도 표시한다.

13. unresolved: remaining-unresolved-ingredients.json 1722행. 각 음식·재료·원문·문맥·후보 nutrition·계량 검토·확인 source·실패 원인·참고 구성 연결 여부를 저장했다. 516개 전체 판정은 recipe-final-states.json. 현재 확보 근거로 확정할 수 없는 원본 품종/상태/제품, 불명확한 단위·소량, 주꾸미 먹물, 딸기잼 원본 배합은 추정하지 않았다.

14. 이번 변경: RecipeCaloriePolicy.kt(독립 composition 조회), FoodMetadataStore.kt(공통 asset 2개 로드), AddRecordScreen.kt의 RecipeReferenceCard(기관·original/reference/survey 구분), 공통 recipe asset 2개, FullRecipeReferenceTest.kt, FullRecipeReferenceSamsungTest.kt, tools/*full_recipe* 및 collect_menuzen_recipe_details.py와 생성 data-source/recipe-full-reference/*. 기존 dirty 작업은 유지했다. DB/Home/추천/음식 공식 nutrition 데이터는 변경하지 않았다.

15. Data 16 PASS / 관련 Unit 12 PASS / Samsung 10+설탕 환산1=11 PASS. SM-S948N Android16/API36, 360dp·720dp·font1.30 참고 카드 확인. 실제 AddRecord 화면140kcal·in-memory Room 기록140kcal 보존 확인. 최종 QA APK 내부 두 asset은 현재 source 파일과 바이트 동일. screenshots 및 app/build/recipe-full-reference 로그에 증거 저장. 앞선 통화 방해 실행의 실패를 PASS로 재분류하지 않았고 최종 실행으로 확인했다.

16. DB version8 / migration0. QA 개인 파일16개 원본 복원 후 전체 바이트 동일, DB 변경 tables0, QA firstInstallTime 유지, 제품 package identity 동일. 제품 private DB를 읽거나 수정하지 않았다.

17. 기존 공식 kcal·탄단지·기준량: 보호된 3개 food/product/franchise asset SHA 동일, 기존 원본 asset 1295행의 gram/nutrition/kcal 기준값 동일. 재료 합으로 FoodItem/MealRecord/추천/통계 kcal를 덮어쓰지 않는다.

18. QUESTION_REQUIRED: 없음. 정확한 추가 source가 필요한 항목은 unresolved 근거로 기록했으며 API key 없이 공개 자료를 사용했다.

19. Production: versionCode7 / versionName1.0.6. 제품 설치0 / 초기화0 / 배포0 / Release build0. QA만 install -r -t.
