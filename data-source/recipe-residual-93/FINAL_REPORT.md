# 최종 잔여 93개 처리 결과

1. **STATUS: PARTIAL.** 516개 완성 목표는 달성하지 못했다. 87개 모두 이번 조사 범위에서 최종 판정했으며, 값이 부족한 항목을 완전 구성으로 올리지 않았다.
2. 전체 대상: **516개**.
3. 실제 앱 완전 구성: **423 → 425 / 516**.
4. 남은 음식: **93 → 91개**. 구성 미완료 87개와 구성은 있으나 음식 자체 영양 근거가 부족한 4개다.
5. 신규 후보 6개: 기존 67,354개 exact/normalized/alias 중복 재검사에서 동일 Food를 확인하지 못했다. 두릅산적 `rda-menuzen-d093044`, 도라지양념구이 `rda-menuzen-d083004`를 생성하고 reference 및 앱 흐름을 검증했다. 딸기롤샌드위치·연근치즈구이·도토리묵밥·잣스프는 미생성/foodId 없음/앱 검증 대상 아님. 상세: [approved-six-final.json](approved-six-final.json).
6. 어려운 87개: 새 ORIGINAL_COMPLETE 0, 새 REFERENCE_COMPLETE 0, PARTIAL_WITH_EXHAUSTED_EVIDENCE 81, UNRESOLVED_WITH_EXHAUSTED_EVIDENCE 6. EXHAUSTED는 이번 검색과 획득 자료의 범위이며 인터넷 전체에 자료가 없다는 의미가 아니다.
7. 전체 구성 상태: ORIGINAL_COMPLETE 41, REFERENCE_COMPLETE 388, PARTIAL 81, UNRESOLVED 6. 완전 구성 429개 중 앱 연결은 425개다.
8. 새 Food: **2개**. 농촌진흥청 Menuzen의 기관 제시 전체 영양값과 기준량을 그대로 사용했다. 두릅적 221.8kcal/144.8g, 도라지구이(고추장) 167.9kcal/112g. 조리 후 완성 중량이라고 표시하지 않았다. 나트륨 미확인은 빈 값으로 유지했다. [공식 두릅적 원문](https://www.nics.go.kr/food/kfi/mgnNewmenumkFoodSelectNew/selectFoodDetail.json?fdCode=D093044), [공식 도라지구이 원문](https://www.nics.go.kr/food/kfi/mgnNewmenumkFoodSelectNew/selectFoodDetail.json?fdCode=D083004).
9. 새 실제 앱 완전 연결: **2개**. 기존 확보 완전 reference를 Food에 연결한 것이며 87개에서 새로 완성한 recipe는 없다. reference 재료 행 **20개** 추가.
10. 새 완전 음식 전수 목록: [new-app-complete-foods.json](new-app-complete-foods.json).
11. 남은 91개 음식별 상태·source·후보·미채택 이유: [remaining-91-foods.json](remaining-91-foods.json). 87개 상세: [all-87-final-evidence-ledger.json](all-87-final-evidence-ledger.json). 나머지 4개는 음식 자체 kcal 미확보. `FoodItem.energyKcal`는 non-null Double이며 seeder도 필수 Double을 읽는다. 기존 unknown kcal Food 표현 정책은 확인되지 않아 새 정책·0·NaN·재료 합계로 채우지 않았다.
12. 기관별 조사/확보/연결: [source-institution-results.json](source-institution-results.json). 문서 URL 36개 조사, 33개 획득, 3개 접근 실패, 관련 페이지 173건 판정. 별도로 농촌진흥청 2개 원문 영양 확인, 기존 완전 reference 2개 재사용, 실제 앱 연결 2개. 87개 전수에 새 학교급식 검색 경로를 실행했고 기존 문서의 검토 가능한 근거도 재사용했다. 이미지 문서와 해외 후보의 추가 탈락 이유: [additional-source-decisions.json](additional-source-decisions.json).
13. 원본 ingredient: linked **2,384 → 2,384**, excluded **154 → 154**, unresolved **1,720 → 1,720**. reference 추가를 원본 해결로 계산하지 않았다.
14. 원본 미해결 사유: identity **850 → 850**, unit **305 → 305**, amount **563 → 563**, nutrition **1 → 1**, source **1 → 1**.
15. 미해결 계량 충돌: **212 → 212**. 평균·중간값 사용 없음. 신규 reference는 원문 직접 g 사용. [current-212-conflict-review.json](current-212-conflict-review.json).
16. 김밥: 기존 공식 nutrition 및 원본 부분 구성과 김밥 변형 reference의 구분을 유지했다. Samsung targeted 보호 테스트 PASS.
17. 실제 반영 파일: `app/src/main/assets/fooddata/food_items.csv`, `food_metadata.csv`, `food_data_manifest.properties`, `official_recipe_reference_estimates.csv`; `tools/publish_recipe_residual_93_foods.py`, `finish_recipe_reference_mapping.py`, `qa_recipe_final_residual_preservation.py`, `collect_residual_93_documents.py`, `finalize_recipe_residual_93.py`, `test_recipe_residual_93.py`, `report_recipe_residual_93.py`; `app/src/androidTest/java/com/example/healthcare/Residual93SamsungTest.kt`; `data-source/recipe-final-residual/`의 생성 mapping/상태 산출물 및 현재 `recipe-residual-93/` 근거 산출물. 기존 dirty working tree의 다른 변경을 이번 작업 변경으로 계산하지 않았다. 제품 화면·추천·검색 상태·updater·통계 로직 변경 없음.
18. 테스트: Data **24 PASS**, 관련 Unit **12 PASS**, QA 앱 및 AndroidTest build **PASS**. QA APK에 공통 source의 현재 Food/metadata/reference asset이 그대로 포함됨을 비교 검증했다. [validation-summary.json](validation-summary.json), [Data 로그](data-24-test-output.txt). 전체 회귀/Lint/Release build는 실행하지 않았다.
19. Samsung SM-S948N / Android 16 / API36: 신규 2개 각각 실제 검색 → 상세/reference/source → 양 입력 → 기록 저장 → 기록 상세 → 편집 저장 **2 PASS**. 기존 공식값/MealRecord/김밥 등 보호 targeted **20 PASS**. 새 완전 연결 2개 전수 검증. 진단 단계의 테스트 selector 실패는 테스트 코드만 수정하고 최종 재실행 PASS. 새 2개 기기 화면 캡처도 검토했다.
20. DB **8 유지**, migration 추가 **0**. QA만 `install -r`. 테스트 전 private 파일을 백업하고 테스트 후 복원했으며, 16개 보호 파일의 바이트·DB schema·기존 모든 테이블 값이 동일함을 별도 검증했다. QA는 검증 후 force-stop 상태로 유지했다. 복원된 DB는 테스트 전 67,354개이며 설치된 APK 공통 asset은 67,356개다. 다음 실행 때 기존 seeder가 신규 정적 Food를 반영한다.
21. Food 정적 asset: **67,354 → 67,356**, 신규 2개와 일치. 기존 67,354개 모든 행/필드 동일. 기존 reference 6,766개 모든 행 동일; 현재 6,786행. [protected-data-verification.json](protected-data-verification.json).
22. 기존 공식 kcal/탄단지: 전수 보존 PASS. ingredient reference 합계로 Food 공식값·추천·통계값 덮어쓰기 없음.
23. MealRecord: 신규 음식 저장 시 기관 제공 Food kcal를 사용함을 검사했다. 임시 테스트 기록 제거 및 private 데이터 복원 후 기존 4개 MealRecord 포함 모든 테이블 변경 **0**. 검증: `app/build/recipe-residual-93/preservation-verify.json`, 테스트 로그 `samsung-flow-output.txt`, `samsung-protection-output.txt`.
24. Production: **versionCode 7 / versionName 1.0.6** 유지. 제품 앱 설치 **0**, 초기화 **0**, overwrite **0**, 배포 **0**. 제품 package identity 동일. DB 접근/제품 UI 조작 없음.
