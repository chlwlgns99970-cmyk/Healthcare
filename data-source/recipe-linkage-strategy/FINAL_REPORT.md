# 잔여 공식 레시피 재료 연결 — 2026-10-05

1. **STATUS: PARTIAL**
   원본 516개 중 완전 연결 41개. 미완료 475개를 완전 연결로 표시하지 않았다.

2. **Recipe**
   complete 30 → **41**, partial 419 → **409**, unlinkable 67 → **66**.
   앱 asset에는 정확한 음식 연결과 대표 참고 레시피 선택을 통과한 80개 레시피 / 1,295행을 게시했다. 이 중 complete는 13개다. 원본 감사 41개와 게시 13개는 서로 다른 집계다.

3. **Ingredient**
   linked 2,143 → **2,357**, excluded **154 유지**, unlinked 1,961 → **1,747**.
   기존 linked/excluded 2,297행 판정은 동일하게 보존했다. 잔여 1,961행 각각의 조회·근거·결과·날짜는 `residual-attempts.json`에 기록했다.

4. **남은 주원인**
   identity 1,058 → **850**, unit 338 → **332**, amount **563 유지**, nutrition **1 유지**, recipe source **1 유지**.
   합계 1,747행. 복합 실패 축은 `compound-failure-groups.json`에 별도 기록했다.

5. **이번에 새로 해결**
   identity **302행**, amount **0행**, unit **6행**, nutrition **0행**, complete recipe **11개**.
   identity를 해결해도 수량/단위가 남는 행이 있어 최종 LINKED 증가는 **214행**이다. 축별 해결 수를 합산하지 않는다.

6. **김밥**
   원본 2개 / 37행: linked **18 → 18**, excluded **1 → 1**, unlinked **18 → 18**. 둘 다 partial.
   18행 신규 시도는 `kimbap-18-attempts.csv`와 `kimbap-new-attempts.json`에 기록했다. 깨소금·다진 파의 정체를 확인해도 작은술 환산 근거가 충돌하여 연결하지 않았다. 밥 양념의 참기름 ‘약간’에 다른 조리 단계의 쇠고기 양념 참기름 0.3g을 대입하지 않았다.
   UI에 공식 김밥 참고 구성이며 선택 음식의 실제 배합이 아니라는 안내를 추가했다.

7. **source 전략과 기여**
   - 새 공공 원재료 bulk **3,672행**, kcal 미기재 24행 제외한 **3,648행** 사용 가능. 새 연결 **1행**.
   - 식약처 공식 다운로드 기존 영양 DB **4,906행**. 새 연결 **203행**. 채택한 일반 식재료 영양값은 **2017년 자료**이며 최근 분석값으로 표시하지 않았다. 동명·동년도 충돌은 제외하고 실제 제공량에서 100g 기준으로 변환했다.
   - 기존 공식 RDA 10.4 캐시 **5행**, 기존 USDA 캐시 **5행** 기여. 합계 **214행**.
   - 공식 PDF: 제조사 조리 설명서 잼 레시피의 딸기 300g·설탕 120g 확인. 다른 레시피이므로 원본 연결 기여 **0행**, 게시 **0건**.
   - 공식 recipe: 서울상상나라 접는 김밥 밥 200g·볶음김치 30g 확인. 별도 참고 자료로만 기록, 원본 연결 기여 **0행**, 게시 **0건**. 두 대체 레시피의 확인 중량은 총 **4행**이다.
   - 공식 portion: RDA 감자전분 8g/큰술, 다진 생강 4g/반 큰술의 원문 쌍을 같은 형태·큰술에 한정해 **6행** 적용. 참고 환산임을 표시했다. 위 214행에 포함되며 별도로 더하지 않는다.
   - canonical dictionary: 정확 일치 / 공식 동의어 / 원문 조리 문맥을 구분하여 identity **302행** 해결. 불명확 D 등급은 연결하지 않았다.
   공개 bulk, 원본 HTML·내장 JSON·다운로드 링크, 기존 공식 조리책, 추가 PDF·기관 레시피·수산 영양 JSON을 분리 검토했다. 원본 및 파생 파일 SHA는 `source-captures.json`, `bulk-transform-provenance.json`에 보존했다.

8. **새 complete recipe 11개**
   냉이된장국, 아욱된장국, 배오이무침, 감자채튀김, 연근치즈구이, 열무된장무침, 봄동겉절이, 달걀국, 고등어찌개, 오징어찌개, 삼겹살구이.
   원본 ID별 전체 재료 감사는 `newly-complete-recipes.json` 참조. 다른 이름/원본을 합쳐 완전 연결 수를 늘리지 않았다.

9. **남은 unresolved와 한계**
   identity **850**: 품종·부위·생/건조·조리 상태가 생략되거나 일치 영양 행이 단일하게 확정되지 않는다.
   amount **563**: 원본 ‘약간/적당량’ 또는 미기재량을 특정 중량으로 확정할 근거가 없다.
   unit **332**: 재료/형태에 맞는 계량 근거가 없거나 공식 계량 값이 충돌한다.
   nutrition **1**: 주꾸미 먹물. 수산 영양 JSON 37행에도 먹물 행이 없어 개체 전체·다른 종으로 대체하지 않았다.
   recipe source **1**: 식약처 MFDS-223 잼. 완성량 81g만으로 원재료 배합을 복원하지 않았다.
   **source 한계**: 현재 확보한 공개 원본에 필요한 정체·중량·계량·먹물 영양값이 없다. 인증 API 및 미확보 원전 책자까지 부재를 증명한 것은 아니다.
   **pipeline 한계**: 검증 가능한 단일 정체와 계량 근거만 허용한다. 조리 문맥 규칙·동의어 사전은 검토 범위로 한정되며 사진이나 모호한 표기를 추정 수치로 변환하지 않는다. 남은 행의 재료별 추가 원전 확인은 향후 보강 대상이다.

10. **수정 파일**
   - `tools/collect_recipe_strategy_sources.py`, `tools/index_recipe_strategy_downloads.py`: 새 원본 수집 및 bulk 변환.
   - `tools/relink_recipe_strategy.py`, `tools/audit_recipe_strategy_sources.py`: 잔여 판정·정체·수량·계량·출처 감사.
   - `tools/publish_adjudicated_recipe_references.py`: 검증한 영양 ID 출처 인식 및 참고 asset 게시.
   - `tools/test_recipe_strategy.py`, `tools/qa_recipe_strategy_preservation.py`: targeted 검증과 QA 보존.
   - `app/src/main/assets/fooddata/recipe_ingredient_estimates.csv`: 최종 참고 재료 asset.
   - `app/src/main/java/com/example/healthcare/ui/screens/AddRecordScreen.kt`: 김밥 참고 구성 안내.
   - `app/src/androidTest/java/com/example/healthcare/CatalogExpansionSamsungTest.kt`: 신규 complete·부분 연결·공식 계량·기록 kcal 테스트.
   - `data-source/recipe-linkage-strategy/`: 원본, 감사, 사전, 신규/잔여 판정 및 결과 파일.
   기존 working tree의 다른 작업 파일은 되돌리지 않았다.

11. **테스트**
   데이터 targeted **10/10 PASS**, 관련 Unit **6/6 PASS**. 전체 회귀·전체 Lint·release build는 실행하지 않았다.
   최종 QA APK에 포함된 asset SHA: `995810d5b8693598472c35c8320348f640496872eaf233f5a8f56fda6276e3ca`.
   테스트 로그 및 APK 증거: `app/build/recipe-linkage-strategy/final-data-tests.log`, `final-apk-evidence.json`. Unit 집계: `targeted-unit-test-results.json`.

12. **Samsung QA**
   SM-S948N / Android 16 / API36. QA 앱만 `install -r`. 최종 APK 대상 **5/5 PASS**, 17.446초.
   신규 complete 3개(냉이된장국·아욱된장국·달걀국), 김밥 partial·참고 안내, RDA 큰술 환산 2개, 공식 kcal·기록 저장/상세/수정 보존 확인. 캡처 6개 저장·확인.
   근거: `app/build/recipe-linkage-strategy/samsung-final-targeted.log`, `strategy-complete-1.png`~`3.png`, `kimbap-ingredients.png`, `strategy-spoon-1.png`~`2.png`.

13. **DB**
   version **8 유지**, migration **0**. QA 테스트 후 기존 데이터 복원 및 별도 재검증 **PASS**.
   보존 대상 private 파일 **16개** 바이트 동일, 변경 테이블 **0**, 제품 앱 identity 동일, QA 최초 설치 시각 동일.
   공식 food/product/franchise asset SHA 동일. 공식 음식 ID·kcal·탄단지·제공량과 제품 사용자 데이터는 변경하지 않았다.
   근거: `app/build/recipe-linkage-strategy/preservation-restore.json`, `preservation-verify.json`.

14. **QUESTION_REQUIRED: 없음**
   DB 스키마 변경이나 임의 수치 승인 없이 확인 가능한 근거만 반영했다.

15. **Production**
   versionCode **7**, versionName **1.0.6**. production 설치·덮어쓰기·삭제·초기화·배포 **0건**.
   GitHub Release / Supabase / Vercel 변경 **0건**. 변경한 공통 소스와 asset은 다음 빌드 대상이며 이번 실행에서 정식 제품으로 배포하지 않았다.
