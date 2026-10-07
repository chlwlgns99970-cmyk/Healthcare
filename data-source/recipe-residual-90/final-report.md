# 잔여90개 새 공개 자료 조사 결과

1. **STATUS: PARTIAL.** 완전 연결426/516. 90개 모두 기존 실패와 새 공개 자료 경로를 대조했지만 추가 완전 연결은 확보하지 못했다. 이번 공개 근거 범위의 판정이며 모든 인터넷/기관 소진을 주장하지 않는다.

2. **Recipe 총수:** 516 →516.

3. **완전 연결:** 426 →426 /516. 실제 자산의 recipeComplete와 Food ID를 독립 집계했다.

4. **잔여:** 90 →90.

5. **Before 분류:** PARTIAL80 /UNRESOLVED6 /Food-level nutrition 부족4.

6. **After 분류:** PARTIAL80 /UNRESOLVED6 /Food-level nutrition 부족4. 새 판정 표기는 PARTIAL_WITH_EXHAUSTED_EVIDENCE84 /UNRESOLVED_WITH_EXHAUSTED_EVIDENCE6. EXHAUSTED는 이번 확인 자료 범위만 뜻한다. 추가 현실적 경로가 모두 사라졌다는 뜻이 아니다.

7. **신규 ORIGINAL_COMPLETE:** 0.

8. **신규 REFERENCE_COMPLETE:** 0.

9. **영양 부족4개:** 딸기롤샌드위치·연근치즈구이·잣스프는 동일 완성 음식 자체 공식 영양표 미확보. 도토리묵밥은 충남농업기술원 연구의45.6kcal/100g를 확보했지만 원본 묵/밥 배합과 연구 시료의 육수·개별 재료·밥량 동일성을 확인하지 못해 미연결. 별도 공식 열량을 원본에 빌려오지 않았다. 각 음식 상세는 food-nutrition-four-results.json.

10. **신규 Food:** 0. 이름/foodId/앱 영양 신규 등록 없음. 단호박샌드위치 후보에134.69kcal 유아1인분 영양표와6개 재료 수량이 있었지만 재료량 단위가 없어 보류했다. 숫자를 g로 간주한 임시 반영은7개 기준 자산 검증을 통해 완전히 복원했다. 후보의 수량은 rawSourceAmount로 보존하고 unit/null·published/false로 표시했다.

11. **새 source:** 153개 URL 조사, 원문/HTML148개 확보, 접근 오류 응답5개 별도 분류. 고양시 XLSX131건, 관광공사248쪽/서울시 연구180쪽/육아정책연구소206쪽/고양시·풀무원32쪽 PDF, 충남농업기술원 연구 HWP 변환본, 아워홈 공식 레시피 등을 확인했다. HTML 목차·E-book shell·구매 완제품 투입량을 전체 레시피로 집계하지 않는다. 기관별 조사/확보/실제 publish는 institution-source-results.json. 모든 기관 publish0.

12. **새 완전 연결 전체 목록:** new-app-complete-foods.json의 빈 배열. 신규0.

13. **잔여90개 상세:** remaining-90-review.md /final-queue-90.json. 각 음식의 원문·기존 source/후보·부족 정보·새 음식별 전략/검색 영수증·새 문서 적중/후보 판정·미채택 이유·추가 현실적 경로를 보존했다. 원 기관의 누락된 규격/계량/영양 원표 확보가 남아 있다. 외부 연락은 발송하지 않았다. 검색 계획90개 중 백합죽3개 원본에서 같은 검색어가2회 추가 중복 실행됐다. 이 실수는 search-dedup-audit.json에 공개했으며 중복0이라고 보고하지 않는다.

14. **수정 파일:** 앱 파일 변경 없음. 새 조사/검증 도구 tools/prepare_recipe_residual_90.py, collect_recipe_residual_90.py, publish_recipe_residual_90.py, finalize_recipe_residual_90.py와 data-source/recipe-residual-90/ 근거 파일. Publisher는 recipe별 명시 단위 증거 파일·SHA·인용이 없으면 자산 수정 전에 종료한다. 기존 작업의 Git 변경은 보존했다.

15. **Data tests:** 18 PASS. 90개 판정/검색 범위, 기존 실패 보존,516/426/90 물리적 자산 집계, Food67357 유일성,7개 기준 자산 SHA, 모든 main source SHA, 단위 미검증 보류, publisher fail-closed, 원문 SHA, QA APK 자산을 검증했다. data-verification.json.

16. **Unit tests:** 관련5개 suite 총26 PASS 결과 재사용. Gradle이 변경 없는 입력을 UP-TO-DATE로 판정했다. 새26개 실행이라고 보고하지 않는다. unit-verification.json 및 app/build/recipe-residual-90-build.log.

17. **QA build:** :app:assembleQa PASS(UP-TO-DATE). APK의7개 fooddata 기준 자산이 현재 소스와 동일함을 별도로 확인했다. Release build/전체 Lint/전체 회귀 미실행.

18. **Samsung QA:** 이번 신규 완전 연결0개로 테스트 대상 없음. QA 설치·실기 저장 테스트 미실행. 이전 실기 결과를 이번 신규 실행으로 보고하지 않는다.

19. **DB:** version8 유지 /migration0. DB·설정·사용자 데이터 접근/변경 및 장치 명령 없음. 모든 app/src/main 기준 SHA 동일.

20. **기존 Food 보호:** Food67357 유지. food_items/product_items/franchise_official_items 및 metadata/reference/manifest 원본 기준 bytes 동일.

21. **공식 kcal/탄단지:** 기존 값 모두 보존. 재료 합계를 완성 음식 공식 kcal로 등록하지 않음. 후보의 불명 단위를 추정하여 배포하지 않음.

22. **MealRecord:** 코드 SHA 동일, 사용자 기록 쓰기 없음.

23. **저장 완료 팝업:** 관련 RecordSaveConfirmation7 +SaveAcknowledgement2 기존 PASS 결과 유지. 저장8요소 코드 변경 없음. 이번 실기 팝업 테스트 미실행.

24. **QUESTION_REQUIRED:** 없음. 미확보 원표/단위는 추정하지 않고 보류했다.

25. **Production:** versionCode7 /versionName1.0.6. 제품 설치0 /초기화0 /overwrite0 /배포0. Supabase/Vercel/GitHub Release 변경 없음.
