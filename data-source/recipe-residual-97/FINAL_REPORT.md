# 잔여 97개 참고 구성 처리 결과

1. **STATUS: PARTIAL**. 97개 모두 현재 조사 범위의 개별 최종 판정을 저장하고 데이터·Unit·QA·Samsung 검증을 마쳤다. 516/516 달성은 못했으며 안전하지 않은 ID·중량·영양값을 생성하지 않았다.
2. **전체: 516개**.
3. **앱 완전 구성: 419 → 421 / 516**. 낙지볶음은 기존 공유 asset의 완전 구성을 원문 조리 문맥과 연결하여 누락 집계를 바로잡았다. 돼지두루치기는 새 Food-참고 구성 연결을 asset에 추가했다.
4. **남은 음식: 97 → 95개**. 완전 배합은 있으나 Food가 없는 6개도 이 95개에 포함된다.
5. **기존 물리 asset 기준 재분류: A6 / B8 / C82 / D1**. 첫 문자열 판정 A4/B7/C85/D1에 원문 조리 문맥 검토를 추가하여 두릅산적·도라지양념구이의 전체 참고 구성과 돼지두루치기의 Food 정체를 확인했다. 현재 개선된 matcher로 immutable 419 baseline asset을 다시 대조한 분류이며 새 asset을 시작 상태로 사용하지 않았다. 각 항목은 한 분류에만 속한다.
6. **전체 구성 상태: ORIGINAL_COMPLETE41 / REFERENCE_COMPLETE386 / PARTIAL83 / UNRESOLVED6**. 앞의 완전 상태 427개 중 Food 없는 6개를 제외하면 앱 제공421개다. 이번 잔여 scope의 최종 판정은 REFERENCE_COMPLETE2 / PARTIAL_WITH_EXHAUSTED_EVIDENCE83 / UNRESOLVED_WITH_EXHAUSTED_EVIDENCE6 / QUESTION_REQUIRED_NEW_FOOD6이다. `exhausted`는 확인한 cache·원문·문서색인·기록된 검색 범위에 한하며 모든 인터넷 자료가 없다는 뜻이 아니다.
7. **foodId 없는 complete: 4 → 6개**. 기존4개에 두릅산적·도라지양념구이의 검증된 whole reference2개를 추가 발견했다. 완전한 데이터를 앱 제공 완료로 잘못 집계하지 않았다.
8. **이번 완전 연결/집계 정정2개**: RDA-91542 낙지볶음(낙지전골), MFDS-317 돼지두루치기. 전체 목록·Food IDs·근거는 `new-app-complete-foods.json`에 저장.
9. **기존 Food 연결: 음식2개, 6개 ID**. 돼지두루치기→돼지고기볶음_채소는 돼지고기·채소·고추장 팬 볶음 원문을 비교했다. 낙지볶음/전골의 모순 제목은 원문 팬·센 불 볶음으로 해소하여 낙지볶음 ID만 연결했다. 새 Food row 생성0개.
10. **Reference**: 기존 농촌진흥청 whole reference2개 재사용. MENUZEN-D102007 돼지고기볶음(고추장, 야채) 16행을 공유 asset에 새로 추가; MENUZEN-D101006 낙지볶음(고추장)은 기존 asset 재사용. 새 학회·공공 원문4후보는 새싹 종/제품 영양, 후추약간, 불특정 견과류, 냉동 설향 nutrition 문제로 publish0개. 딸기잼 자기참조와 qualifier 어순 자기참조를 validator에서 제외했다. 상세는 `new-primary-candidate-reviews.json`.
11. **원본 ingredient**: linked2384 / excluded154 / unresolved1720 유지. 이번 reference 연결을 원본 ingredient 해결로 계산하지 않았다.
12. **원본 미해결 원인**: identity850 / unit305 / amount563 / nutrition1 / source1 유지. UNRESOLVED6개, 주꾸미 먹물 nutrition1과 딸기잼 source1은 별도 새 조사 및 원문 검토를 기록했다. `focused-unresolved-and-source-review.json` 참조.
13. **계량 충돌212건**: 전부 기존 원문 비교에 새 학회 recipe의 g/생활단위 병기를 대조. 신규 해결0 / 미해결212. 같은 기관이라도 다른 recipe의 크기·상태·계량법을 같다고 추정하지 않았다. 평균·중간값·전역 큰술값0. `current-212-conflict-review.json` 참조.
14. **김밥**: 원본 linked18/excluded1/unresolved18 유지. 일반 원본 partial과 햄김밥 whole reference는 별도 표시. Samsung에서 qualifier·전체 재료·경고를 확인했다. 햄김밥을 일반 김밥 원본 배합으로 바꾸지 않았다.
15. **잔여95개 개별 artifact**: `remaining-foods.json`. 각 음식의 상태, Food 후보/확정 ID, 기존·신규 source, reference 후보와 미채택 이유, 실제 다음 단계, 새 Food 필요 여부를 저장. 전체97개는 `all-97-final-evidence-ledger.json`.
16. **수정 파일**: 공유 `app/src/main/assets/fooddata/official_recipe_reference_estimates.csv`; matcher `tools/finish_recipe_reference_mapping.py`; validator `tools/recipe_composition_validation.py`; 문맥 규칙 `tools/recipe_context_identity.py`; `tools/audit_recipe_residual_97.py`, `tools/plan_recipe_residual_97_searches.py`, `tools/finalize_recipe_residual_97.py`; 새 테스트 `tools/test_recipe_residual_97.py`, `app/src/androidTest/java/com/example/healthcare/Residual97SamsungTest.kt`; QA 준비/검증/보고 도구 `tools/prepare_residual_97_qa.py`, `tools/pull_residual_97_images.py`, `tools/verify_residual_97_artifacts.py`, `tools/write_residual_97_report.py`; 기존 `tools/test_final_recipe_residuals.py`의 역사적419 기준 검증 명시, `tools/qa_recipe_final_residual_preservation.py`의 이번 QA 백업 폴더 허용; 관련 생성 evidence는 `data-source/recipe-residual-97/`와 기존 `data-source/recipe-final-residual/`의 상태/매칭/검증 파일, shared 원문 cache manifest에 저장. 기존 Home·검색·추천·기록·통계·franchise·updater UI/계산 로직 수정0.
17. **검증**: Data40/40, 관련 Unit12/12, QA APK 및 instrumentation APK 빌드 PASS. 요구한23개 범주별 검증 근거를 `validation.json`에 매핑. 실패한 validator regression과 stale referenceComplete flag를 수정한 뒤 모두 PASS.
18. **Samsung SM-S948N Android16/API36:24/24 PASS**. 새 연결2개 모든 ingredient, original/partial/조사평균, 김밥 qualifier, 공식 kcal, MealRecord, 실제 검색→상세→섭취량 화면을 검증. 360dp와 fontScale1.30에서 새 카드 행 전체를 scroll하여 표시 확인. 공유 RecipeReferenceCard를 사용하며 홈 스크롤 변경0. 새 카드2개의 실제 캡처도 시각 확인. 로그 `app/build/recipe-residual-97/samsung-instrumentation.txt`.
19. **DB8 / migration0 / 사용자 데이터 보존 PASS**. QA 위에 `install -r`만 사용. 작업 전 백업16개 private file을 복원·검증하여 바이트 동일, changedTables0, QA 최초 설치 시간 보존. 제품 package identity 변경 없음. `app/build/recipe-residual-97/preservation-verify.json`.
20. **67,354 Food 보존 PASS**: food_items/product_items/franchise_official_items의 작업 전 SHA256와 일치. 새 Food0개. 설치된 QA APK의 공유 asset도 소스와 바이트 동일.
21. **공식 kcal·탄단지 보존 PASS**. Food의 기존 공식 값 및 영양 기준량 변경0. 참고 합계는 설명 카드에만 사용.
22. **MealRecord kcal 보존 PASS**. 메모리 Room 테스트와 실제 record 화면 값 검증, 기존 private DB table 변경0 및 복원 파일 바이트 동일 확인.
23. **QUESTION_REQUIRED_NEW_FOOD:6개**. 두릅산적 / 도라지양념구이 / 딸기롤샌드위치 / 연근치즈구이 / 도토리묵밥 / 잣스프. 이유와 완전 배합 source를 `question-required-new-food.json`에 저장. 딸기 과일 롤을 육류 샌드위치에, 연근치즈를 콘치즈에, 육수 없는 도토리묵밥을 국물 묵밥에, 잣·고구마·우유 스프를 쌀 잣죽에 붙이지 않았다. 두릅산적과 도라지고추장구이는 67,354 Food 전체에서 같은 적/구이 항목을 확인하지 못했다. 신규 Food의 공식 영양/기준량/ID 정책과 생성 승인이 필요한 상태로 분리했다.
24. **Production: versionCode7 / versionName1.0.6 / 제품 설치0 / 초기화0 / 배포0**. GitHub Release·Supabase·Vercel·release build 변경0.
