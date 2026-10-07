1. STATUS = PARTIAL. 516개 완전 연결 목표는 미달성.

2. Recipe: Before total 516 / complete 13 / partial 407 / unlinkable 96 → After total 516 / complete 29 / partial 419 / unlinkable 68. 신규 완전 연결 17개, 원재료 없이 완성품 중량만 제공한 딸기잼 1개는 완전 연결 취소.

3. Ingredient: 전체 4258행 / identity 연결 2545 / g 중량 근거 3210 / nutrition 연결 2545 / 최종 연결 2072 / 비열량·공정 제외 154 / 최종 미연결 2032. 신규 연결 246행, 순환 연결 삭제 1행 → 순증 245행. Identity·amount·nutrition 숫자는 독립 항목이며 전부가 최종 계산 가능하다는 뜻은 아님.

4. 최종 실패 단일 원인: identity 1126 / unit 341 / amount 563 / nutrition 1 / recipe source 1 = 2032행. 사용량 미정과 겹치는 identity 모호성까지 포함하면 1558행. 소금도 사용량이 없으면 완전 연결로 인정하지 않도록 강화하여 기존 비열량 제외 136행을 다시 처리했다.

5. 추가 활용 공식 source: RDA 10.4 일반 생재료·조리 완료 밥·동일 조리법의 다진 대파 명시 → 신규 최종 연결 103행; USDA SR Legacy 7793 식품/14449 portion의 건표고·동일 재료 계량스푼 → 91행; K-FIND 316734행 중 동일명·동일 식품군·제조사 없는 단일 분석 어묵/식빵/생크림/버터/고추장 → 52행. USDA 재료별 계량스푼 환산 124행, 5개 이상 공식 원문 중량 병기가 모두 일치하는 설탕 컵·달걀 개·통깨 작은술 참고량 7행. RDA와 USDA의 공식 근거·source 해시 보존. 모든 컵·개수에 공통 중량을 적용하거나 약간의 가짜 중량은 미생성.

식약처 COOKRCP01 추가 공식 레시피 8개를 확보했다. 공개 샘플은 INFO-310 호출 한도 소진 후 중단했다. 한도 응답은 레시피가 없다는 뜻으로 처리하지 않았다. 다른 배합은 별도 원문 catalog에 보존하며 원본 516개 완전 연결 수에 넣지 않았다.

추가 김밥 source: 농사로 새싹 김밥과 농식품 올바로 수수떡갈비김밥도 조사했지만 선택 배합·종류 미정 및 참기름/참깨 약간이 남아 원래 두 레시피를 완전 연결로 바꾸는 근거로 쓰지 않았다. 원본 516 identity와 ingredient spans는 그대로 유지했다. source-captures와 remaining-recipes에 확인 URL을 보존했다.

6. 김밥: 원본 2개 / 37재료 / 연결 18 / 물·공정 제외 1 / 미연결 18. 일반 김밥 18재료 중 연결 10, 충무김밥 19재료 중 연결 8. 실제 UI는 공식 100g=140 kcal, 근거 있는 g 수량 입력, 참고 구성·재료별 예상값·부분 연결 안내 PASS. 검증된 1줄 중량은 없으므로 임의 줄 환산은 추가하지 않았다.

7. 완전 연결 예시 6개. 아래 예상값은 원문 전체 재료량 기준이고 오른쪽 공식 kcal는 선택 식품의 기존 기준량 값이므로 서로 다른 분모이며 기록값으로 대체하지 않는다.

곤드레나물밥 (RDA-DIET-89332-3): 곤드레나물(말린 것) 10g → 29.000 kcal; 백미 60g → 219.600 kcal; 현미 30g → 107.100 kcal; 참기름 2g → 18.400 kcal; 소금 1.5g → 0.000 kcal. 참고 전체 합 374.100 kcal. 공식 100g=149 kcal. 레시피 [원문](https://api.nongsaro.go.kr/sample/rest/recomendDiet/recomendDiet.jsp?cntntsNo=89332&tabNo=3); 재료별 nutrition URL은 complete-examples.json.

현미밥 (MFDS-148): 현미 360g → 1285.200 kcal. 참고 전체 합 1285.200 kcal. 공식 100ml=121 kcal. 레시피 [원문](https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=148); 재료별 nutrition URL은 complete-examples.json.

갈치구이 (MFDS-158): 갈치 280g → 417.200 kcal; 소금 4g → 0.000 kcal. 참고 전체 합 417.200 kcal. 공식 100ml=147 kcal. 레시피 [원문](https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=158); 재료별 nutrition URL은 complete-examples.json.

어묵국 (MFDS-173): 어묵 280g → 618.800 kcal; 대파 20g → 4.600 kcal; 다진마늘 4g → 5.960 kcal; 소금 8g → 0.000 kcal. 참고 전체 합 629.360 kcal. 공식 100ml=21 kcal. 레시피 [원문](https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=173); 재료별 nutrition URL은 complete-examples.json.

알감자구이 (MFDS-247): 감자 520g → 400.400 kcal; 소금 2g → 0.000 kcal. 참고 전체 합 400.400 kcal. 공식 100ml=123 kcal. 레시피 [원문](https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=247); 재료별 nutrition URL은 complete-examples.json.

꽃게찜 (MFDS-263): 꽃게 350g → 269.500 kcal. 참고 전체 합 269.500 kcal. 공식 100ml=52 kcal. 레시피 [원문](https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=263); 재료별 nutrition URL은 complete-examples.json.

8. 남은 487개 전체 목록: remaining-recipes.json. 각 원본 레시피의 미연결 재료·원문 span·nutrition 후보·단위 근거 후보·조사 source·추가 API 접근 상태·잔여 원인 포함.

9. 실제 수정 소스/테스트/asset 11개와 이번 생성 artifact는 modified-files.json. 기존 메뉴·식품 영양값·추천·업데이트·기록 정책 파일은 수정하지 않았다.

10. targeted Data 10/10 PASS, Unit 2/2 PASS, Samsung UI/기록 6개 고유 테스트 PASS. 최신 재료 데이터 QA 빌드·install -r 후 영향 있는 3개 UI/기록 테스트 재실행 PASS(samsung-latest.log). 완전 연결 화면 3음식=알감자구이/현미밥/곤드레나물밥, 부분 김밥/미연결 보리밥, 짜파게티 상세·610 kcal 기록, 123 kcal 저장 유지 확인. 추가 김밥 수량 검증에서 기본 입력이 빈칸인데 100으로 가정한 테스트 실패 1회는 실제 100g 입력 후 140 kcal 검사로 수정해 PASS. 전체 suite/Lint/Release 빌드 미실행. 로그와 QA 실제 화면은 app/build/recipe-linkage-maximization.

11. DB 8 유지 / migration 0. QA 사용자 데이터 테스트 전 baseline 복원 후 검증 PASS: changedTables=[], DB 8→8, allowlisted 16 private files byte-identical, QA 최초 설치 시각 보존, 제품 identity 불변. 최종 QA APK만 install -r.

12. 남은 문제: 419 부분 연결 + 68 연결 불가. 곡종·양념 제법·육류 부위 등 미확정 identity, 부피/개수 가식부 환산, 약간/무단위/범위량, 주꾸미 먹물 공식 nutrition 1행, 완성 딸기잼 중량만 있는 source 1행. 다른 공식 레시피의 배합을 원본에 복사하거나 임의 중량을 만들지 않았다.

13. QUESTION_REQUIRED = 없음. 추가 승인이나 임의 데이터 결정은 요청하지 않는다.

14. Production versionCode=7 / versionName=1.0.6 / production 변경=0. 제품 APK 설치·삭제·clear, production 배포·서비스 변경 없음.
