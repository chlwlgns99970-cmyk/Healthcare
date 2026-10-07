# 잔여 89개 최종 검증 보고 — 2026-10-05

1. **STATUS: PARTIAL**. 조사·채택 판정과 실제 반영/QA 검증을 완료했으나 전체 516개가 완전 구성인 상태는 아니다. 확보한 공식 자료·검색 경로의 검토 완료를 뜻하며 인터넷 전체에 자료가 없다는 주장은 하지 않는다.
2. 전체 **516개**.
3. 실제 앱 완전 구성 **421 → 423 / 516**.
4. 남은 음식 **95 → 93개**: 구성 미완전 87 + 승인 필요한 신규 Food 후보 6.
5. 작업 큐 89개: 새 ORIGINAL_COMPLETE **0**, 새 REFERENCE_COMPLETE **2**, PARTIAL_WITH_EXHAUSTED_EVIDENCE **81**, UNRESOLVED_WITH_EXHAUSTED_EVIDENCE **6**. 전 음식별 판정은 `all-89-final-evidence-ledger.json`에 저장했다. PDF·제조사·의료/학술/공공 검색 세 계열, 기존 1388개 구성·메뉴젠 3250개 메뉴·Food 67354개 재색인을 사용했다. 대구 책자는 전체 100개 레시피 제목과 관련 페이지를 시각 검토했다.
6. 신규 Food 후보 **두릅산적·도라지양념구이·딸기롤샌드위치·연근치즈구이·도토리묵밥·잣스프**: 재검색에서 안전한 같은 기존 Food를 확인하지 못했다. 여섯 음식 각각 신규 생성 필요 여부는 여전히 승인 대기다. 임의 Food/id 생성 **0**. `question-required-six-final.json`.
7. 전체 상태: ORIGINAL_COMPLETE **41**, REFERENCE_COMPLETE **388**, PARTIAL **81**, UNRESOLVED **6**. QUESTION_REQUIRED_NEW_FOOD **6**은 complete 중 앱 미연결 항목이므로 위 상태와 중복 집계한다. 데이터상 완전 구성 **429**, 앱 **423**.
8. 새 앱 완전 연결 음식 전체: **양상추샐러드, 감자베이컨볶음**. `new-app-complete-foods.json`.
9. 신규 연결 **7 foodId**. 양상추샐러드 5개: kfind-d114-640230000-0001 / d414·d514·d614·d714-704000000-0001. 감자베이컨볶음 2개: kfind-d510-450032500-0001 / kfind-d610-450032500-0001. 양상추 중심 생채소+소스 구성과 감자+베이컨 팬 볶음의 전체 구성을 검토했다. `_감자_베이컨` 중복 주재료 표기의 제한된 matcher 규칙은 베이컨 qualifier와 볶음 조리법을 보존한다.
10. 새 확보한 완전 reference **3개**: 대구시 양상추샐러드, 메뉴젠 버섯전 모둠, 미역나물 식초. 그중 양상추샐러드 **1개 publish**. 혼합 버섯전/초고추장 없는 미역나물은 동일 음식 구성 근거가 부족하여 publish하지 않았다. 기존 KDCA 감자볶음/베이컨 조사 평균 **1개를 재사용해 새 연결**했다. 합계 앱에 새 연결한 구성 2개, asset **6687 → 6766행(+79)**. `new-reference-publication-review.json`.
11. 원본 ingredient 상태 유지: linked **2384 → 2384**, excluded **154 → 154**, unresolved **1720 → 1720**. reference 증가를 원본 해결로 세지 않았다.
12. 원본 미해결 유지: identity **850**, unit **305**, amount **563**, nutrition **1**, source **1**. 주꾸미 먹물과 딸기잼 원본 자료의 별도 검토 결과는 `separate-nutrition-and-source-review.json`. 딸기잼 기관/recipe ID는 확인됐으나 원문은 완성 음식 81g만 제공한다.
13. 계량 충돌 **212 → 212**. 공식 직접 g reference를 사용했고 원본 다른 레시피의 큰술 값을 평균하거나 역대입하지 않았다. `current-212-conflict-review.json`.
14. 김밥: 원본 불완전 상태와 별도 명시된 완전 reference를 구분했다. Samsung 김밥 변형명·재료 표시·참치김밥 검색→상세→기록 검증 PASS.
15. 남은 **87개 전수**에 대해 음식명·상태·확인 source·reference 후보·미채택 이유·필요한 다음 근거를 `remaining-87-foods.json`에 저장했다. 검색 점수를 동일 음식 증거로 쓰지 않았다. 조리법의 누락 재료, 약간/개수/컵 중량, 가식부 수율, 제품별 원료 영양을 임의로 채우지 않았다.
16. 수정 파일: 공통 `app/src/main/assets/fooddata/official_recipe_reference_estimates.csv`; matcher `tools/finish_recipe_reference_mapping.py`; QA 백업 경로 allowlist `tools/qa_recipe_final_residual_preservation.py`; 새 `app/src/androidTest/java/com/example/healthcare/Residual89SamsungTest.kt`. 이번 데이터 수집/색인/빌드/판정/테스트 도구는 `tools/*residual_89*.py`, 근거와 결과는 이 폴더. 다른 기존 working tree 변경은 이번 변경으로 주장하지 않는다.
17. 테스트: 필수 Data **22개 PASS**(`data-22-test-output.txt`), 기존 관련 Data **31개 PASS**, 관련 Unit **12개 새 실행 PASS**. QA / QAAndroidTest build PASS. `validation-summary.json` 및 `app/build/recipe-residual-89/targeted-build.txt`. Unit 태스크 실행용 임시 init script의 첫 구성 오류는 수정했고 최종 빌드는 성공했다. Release build·전체 Lint·전체 회귀 실행 없음.
18. Samsung SM-S948N Android16/API36: **29개 targeted PASS**. 새 Food 연결 **7개 전수** + 기존 안전성 20개 + 검색→상세→기록 2개. 360dp / fontScale 1.30에서 각 신규 구성의 모든 재료 행을 스크롤하여 표시 확인했다. 전체 7개 스크린샷 저장·시각 확인. `app/build/recipe-residual-89/samsung-targeted.txt` / `screenshots/`.
19. DB **8**, migration **0**. QA non-destructive install 전에 DB/설정/파일 16개를 백업했고 테스트 후 복원 및 재검증 PASS. 원본/복원 DB 테이블 변경 **0**, 허용된 private 파일 바이트 모두 동일. 제품 앱 identity와 QA firstInstallTime 보존.
20. 기존 **67354 Food**: food_items 19631 + product_items 47295 + franchise_official_items 428. 세 CSV SHA-256 모두 동일. 원본 ingredient asset도 동일. 기존 reference 6687행 모든 필드 값 보존.
21. 공식 kcal·탄단지 보존: 세 Food CSV 불변 및 Samsung 실제 기록 화면 검증 PASS. reference 합계는 설명용으로만 표시한다. QA APK 공통 asset은 소스와 바이트 동일(SHA `b16f64b1bd3b658c3542bb7de46c1a40878628969676ef023b62a6906e1a1931`). QA source set에는 이 asset/UI override가 없다.
22. MealRecord 보존: reference 예상 kcal를 기록에 쓰지 않는 테스트 PASS. 실제 QA 백업과 복원 이후 모든 DB 테이블·파일 일치. `app/build/recipe-residual-89/preservation-verify.json`.
23. QUESTION_REQUIRED: 신규 Food 후보 **6개**만 생성 승인 미확정 상태로 유지. 이 때문에 다른 89개 조사·검증을 중단하지 않았다. 이 여섯 생성만 승인해도 자료가 미완전한 87개는 자동으로 해결되지 않는다.
24. Production **versionCode 7 / versionName 1.0.6** 유지. 제품 설치 **0**, overwrite **0**, uninstall **0**, 초기화 **0**, 배포 **0**. Supabase/Vercel/GitHub Release 변경 없음.
