# 전체 Food 검색 → 상세 연결 최종 보고

검증일: 2026-10-06. 프로젝트: `C:\Users\young\AndroidStudioProjects\Healthcare`.

1. **STATUS: COMPLETE — 현재 확보된 정보의 연결 상태 기준.** 원재료/영양 정보가 모든 음식에서 완전하다는 뜻은 아니다. 실제 기기는 대표군을 검증했고, 전체 Food는 실제 공통 detail model을 전수 생성했다.

2. **전체 Food:** Before 67,356 → After 67,356. 이번 작업 신규 Food 0.

3. **전체 감사:** 실제 QA 패키지의 CSV parser·metadata loader·공통 FoodDetailPolicy·실제 QA DB의 ID를 사용했다. 67,356 / 67,356 생성 성공, 실패 0. 별도 공식 메뉴 카탈로그 2,685개 모델도 성공. source identity·공식 kcal·탄단지·기준량·제조사·제공량·출처·참고 구성을 검사했다.

4. **상세정보 연결 상태:** Before 기존 UI에서 확보한 metadata 필드가 누락된 Food 67,292개. After FULL 53,704 / MINIMAL_VALID 13,652 / 연결 누락 0 / 잘못된 mapping 0. FULL은 공식 kcal·기준량·탄단지와 현재 보유한 근거가 연결된 상태이며, 완전 원재료 확보 상태와는 다르다. MINIMAL_VALID은 일부 탄단지가 공식 원문에 미제공된 상태다.

5. **연결 누락:** 발견 67,292 / 수정 67,292 / 남음 0. 현재 코드가 가진 필드의 누락을 기준으로 집계했으며 중복 합산하지 않았다. 제조사·제공량·출처·재료 등은 공통 상세 모델로 연결했다. 기준량 검토가 필요한 2,382개 Food도 수동 입력 상세에서 원문 영양값을 유지한다.

6. **데이터는 있었지만 UI가 못 보여주던 Food:** 67,292개 수정. 대표 누락 필드: 원문 용량 표기 66,746, 자료 기준일 67,026, 식품 분류 67,045, 원문 제품/재료 설명 540, 생활단위 근거 URL 819, 원문 섭취 기준 42,185, 완전 원재료 원문 3. 이 수치는 서로 겹친다. 제품 설명을 완전 원재료로 표시하지 않는다.

7. **스파게티:** 원문 이름에 스파게티가 포함된 Food는 203개. 실제 repository 검색 결과는 동일 근거 행 접기를 적용하여 199개다. 토마토 스파게티 16, 미트소스 스파게티 1, 크림 스파게티 13, 까르보나라 57, 봉골레 8, 해산물 스파게티 5. 서로 다른 음식 ID·영양값은 유지했다. 해물 원문 8개에 검증된 해산물 검색 별칭을 추가했다. 검색 → 상세 → 양 → 저장 → 기록 상세 → 편집 PASS. 검토 필요 스파게티의 실제 수동 상세 진입도 PASS.

   | 실제 Food ID | 음식 | 공식 kcal / 기준량 | 탄수화물 / 단백질 / 지방 | 출처 / 참고 구성 |
   | --- | --- | --- | --- | --- |
   | kfind-product-p101-415000400-0828 | 떠먹는 스파게티 피자 | 264 / 100g | 29.58 / 13.16 / 10.29g | K-FIND-PRODUCT, (주)지지푸드; 실제 저장·편집 대표 |
   | kfind-d303-161490000-0001 | 스파게티_토마토소스 | 129 / 100g | 18.64 / 4.87 / 3.83g | K-FIND; 농식품정보누리 원문 참고 추가 |
   | kfind-d703-161000000-0001 | 스파게티 | 100 / 원문 100ml | 18.69 / 3.2 / 1.52g | K-FIND; 원문 기준량 검토, 밀도/중량 추정 금지 |
   | kfind-d303-161505000-0001 | 스파게티_해물_토마토소스 | 117 / 100g | 17.45 / 5.59 / 2.75g | K-FIND; 해산물 검색 별칭 |
   | kfind-d303-161504800-0001 | 스파게티_해물_크림소스 | 184 / 100g | 17.39 / 6.08 / 9.96g | K-FIND; 해산물 검색 별칭 |

   전체 결과별 상세값은 `spaghetti-after-detail-ledger.json`, 실제 repository 결과는 `app/build/all-food-detail-audit/spaghetti-subtype-search.json`에 기록했다. 공개 참고 레시피의 영양값을 선택한 Food의 공식값에 대입하지 않는다.

8. **Nutrition:** kcal·기준량 연결 67,356. 탄단지 모두 확인 53,704, 일부 미확인 13,652(탄단지 1종 12,891 / 2종 760 / 0종 1). 공식 kcal가 없는 Room Food 0. 모르는 값을 0으로 채운 변경 0. 실제 0 값은 그대로 유지. 별도 카탈로그의 미제공 값은 미확인 표시.

9. **Serving / 생활단위:** metadata 근거 생활단위 819개 표시, 신규 변환 0. 기존 FoodAmountPolicy의 실제 기본 선택은 생활단위 50,046 / g·ml fallback 14,928 / 기준량 검토로 직접 입력 2,382. 생활단위 기본 선택에는 기존 공식 제공량 설명·제품 전체 단위도 포함된다. 기존 단위·공식 제공량·계산 정책은 변경하지 않았다.

10. **Manufacturer / brand / provenance:** 제조사 값 보유 62,733, Food 브랜드 보유 63,161, 출처 근거 67,356 모두 정확한 ID로 연결. 기존 원문 metadata 값 변경 0. 서로 다른 Food 브랜드와 metadata 브랜드는 구분 표시. QA 한정 코드가 아닌 `app/src/main` 공통 source다.

11. **Recipe / Reference:** 구성 연결 Food 합집합 741 → 766. 현재 원본 구성 연결 Food 302 / 참고 구성 연결 Food 729(서로 중복 가능). 기존 완전 레시피를 정확한 한정 음식명·category·source로 재사용한 연결 26건 / 새롭게 참고 구성을 갖게 된 Food 25개 / 추가 ingredient row 236개. 공식 참고 asset 6,786 → 7,022행. 별도 공개 레시피 원문 22개 Food 연결은 완전 구성 영양 추정으로 계산하지 않는다. 기존 516 recipe 완전 구성은 425 / 516 → 425 / 516.

12. **기존 잔여 91개:** 새로 완전 연결 0. PARTIAL 81 / UNRESOLVED 6 / 원본 구성은 완전하나 음식 자체 공식 nutrition이 없어 Food를 안전하게 생성할 수 없는 항목 4. 새 농식품정보누리 전체 173개 제목·20개 목록 페이지와 91개를 재대조했다. 동일한 완전 원문 추가 근거는 확보하지 못했다. 이는 인터넷 전체에 자료가 없다는 판정이 아니다. 기존 원문·미확정 재료 정체성·실제 사용량·단위 근거를 보존했다. 4개 Food에 reference 합계·0·NaN을 공식 kcal로 넣지 않았다.

13. **신규 Food:** 0. 이 작업 전에 존재하던 두릅산적 등도 보존·검증했다.

14. **잘못된 foodId:** 발견 0 / 수정 0 / 남음 0. sourceFoodCode·normalizedName·실제 DB ID를 전수 대조했다. 검색 별칭 추가는 ID 병합 또는 nutrition 변경이 아니다.

15. **Orphan 최종:** nutrition 0 / metadata 0 / reference 0. Room Food에 속하지 않는 metadata 2,679개는 실제 공식 메뉴 및 legacy 카탈로그 ID namespace로 정확히 연결되어 있으며 orphan으로 오판하지 않았다. 89개 브랜드의 영양 Food 5,564개와 별도 공식 카탈로그 2,685개를 검사했다. 두 수치는 화면의 중복 제거 후 메뉴 수와 동일한 집계가 아니다. category/filter 정책 변경 0.

16. **수정 파일:** 이번 범위는 `FoodDetailPolicy.kt`, `FoodMetadataPolicy.kt`, `FoodMetadataStore.kt`, `AddRecordScreen.kt`, `WellnessManualRecordScreen.kt`, `AddRecordViewModel.kt`; 정적 `food_items.csv`, `product_items.csv`, `franchise_official_items.csv`, `food_metadata.csv`, `official_recipe_reference_estimates.csv`, `food_data_manifest.properties`; 관련 Unit/instrumentation 테스트 및 `tools/*all_food*`, 공식 자료 수집/연결 도구·근거 파일이다. QA 보존 wrapper의 허용 폴더도 추가했다. 원래 dirty working tree의 다른 변경은 이번 작업 변경이라고 집계하지 않았다. Home·updater·추천·통계·검색 상태 복원·프랜차이즈 업종 정책은 이번 작업에서 수정하지 않았다.

17. **Data 테스트:** `tools/test_all_food_detail_audit.py` 20 / 20 PASS. ID, 모든 모델, source identity, orphan, 원본 모든 영양/기준량 필드, 기존 metadata 모든 필드, 생활단위, 짜파게티 140g·610kcal·96/9/20g, 원본 recipe, 완전 reference, 검색 별칭, 실제 APK asset, QA 데이터 보존, 공식 이미지 SHA를 검사했다.

18. **Unit 테스트:** 61 / 61 PASS. FoodAmountPolicy 24, FoodDetailPolicy 7, FoodMetadataPolicy 7, FoodSearchPolicy 17, FullRecipeReference 6. unknown/null·공식값·정확한 ID·참고 구성/공식 nutrition 구분 검증.

19. **QA build:** `:app:assembleQa` 및 `:app:assembleQaAndroidTest` PASS. 최종 asset이 실제 QA APK 안의 byte와 일치. Release build·전체 Lint·배포 실행 0. QA와 production이 같은 main detail source/asset을 사용하며 variant 전용 상세 구현은 없다.

20. **Samsung QA:** SM-S948N / Android 16 API36. 대표 검색어 20개의 실제 상세 표시 PASS. 스파게티·일반 음식·가공식품·김밥·짜파게티·피자·치킨·아이스크림·음료·정보가 적은 Food·기존 신규 Food·새 reference Food·공식 프랜차이즈 등 16개 저장/기록 상세/편집 경로 PASS. 영양 미제공 메뉴는 가짜 기록값을 채우지 않고 실제 상세의 미확인 상태를 확인했다. 신규 완전 reference 연결 26개 및 공개 원문 연결 22개 모두 360dp·앱 글자 1.30의 공통 상세 UI에서 표시 PASS. 실제 수동 스파게티 상세와 하위 검색어 7개도 PASS. 관련 고유 테스트 29개 누적 최종 PASS. 진단 중 실패했던 lazy-list 대기/정확한 기록 선택/검토 필요 Food를 자동 계산 대상으로 선택한 테스트는 수정하고 필요한 항목만 재실행했다.

21. **DB:** version 8 유지, migration 추가 0. QA 테스트 전 immutable snapshot 확보 후 복원 및 별도 verify PASS. 원본 private file 17개 모두 byte 동일, DB 변경 테이블 0. 테스트 중 생성된 설정 파일 1개만 원본에 없음을 확인해 제거했다. 기존 QA 최초 설치 시각도 유지.

22. **기존 Food 보호:** 기존 ID·이름·category·nutrition·basis·source·생성/수정 시각 등의 근거 없는 변경 row 0. 변경은 검증된 검색 별칭과 추가 근거 연결이다. display 별칭 297건과 해산물 별칭 8건은 별도 근거 artifact로 기록했다. Food ID 대규모 병합/삭제 0.

23. **공식 kcal/탄단지:** 67,356개 전부 원본 필드 동일. UI 소수 표시는 보존하고 기존 kcal 정수 저장/계산 로직은 변경하지 않았다. 공개 레시피 text 및 참고 ingredient estimate는 공식 Food nutrition을 덮어쓰지 않는다.

24. **MealRecord:** 기존 모든 DB 테이블 및 private file 복원 후 동일. 각 실제 저장/편집 테스트에서도 이전 MealRecord 전체가 변하지 않았음을 검사하고 테스트로 만든 기록만 제거했다. 최종 QA baseline 복원 verify PASS.

25. **정보가 부족한 Food:** 탄단지 일부 미제공 13,652개. 대표 리틀텐은 공식 kcal·기준량·제조사·출처를 보여주고 미확인 탄단지를 0으로 만들지 않는다. 원재료·단위 근거가 없는 경우도 확보한 사실만 표시한다. 원문 solid-food ml 기준 2,382개는 원문 값을 보여주고 수동 입력 안내를 유지한다. 기존 recipe 잔여 91개는 완전 구성이라는 잘못된 표시를 추가하지 않았다. 현재 데이터 없는 모든 종류에 대해 외부 공개 자료가 전혀 없다고 주장하지 않는다.

26. **QUESTION_REQUIRED:** 없음. 확인되지 않은 외부 사실은 사용자 선택이나 임의 숫자로 대신하지 않는다.

27. **Production:** versionCode 7 / versionName 1.0.6 유지. 제품 앱 설치 0 / 초기화 0 / overwrite 0 / 배포 0. 제품 패키지 identity가 baseline과 동일. 제품 DB·private file·UI 접근 0. GitHub Release·Supabase·Vercel 변경 0.

## 근거 파일

- `before-food-ledger.json`, `after-food-ledger.json`: 67,356개 ID별 전수 목록.
- `before-summary.json`, `after-summary.json`: 집계 및 상태 정의.
- `new-exact-reference-links.json`: 26건/236행의 정확한 한정 음식명 연결.
- `new-verified-display-aliases.json`, `new-verified-seafood-aliases.json`: 실제 runtime 표시명 및 검토한 원문 별칭.
- `new-public-recipe-text-links.json`: 영양 추정과 분리한 공개 원문 연결 22건.
- `foodnuri/catalog.json`, `list-captures.json`, `exact-reference-source-candidates.json`, HTML cache 및 검토 이미지 SHA.
- `remaining-91-new-source-adjudication.json`: 잔여 91개 새 기관 catalog 대조.
- `data-test-output.txt`, `unit-test-results.json`.
- `app/build/all-food-detail-audit/model-result.json`, `reference-ui-result.json`, `spaghetti-subtype-search.json` 및 Samsung 실행 로그.
- `app/build/all-food-detail-audit/preservation-verify.json`: 최종 QA 17개 파일·DB·제품 identity 보존 검증. 개인정보가 포함될 수 있는 QA tar snapshot은 보고서 외부로 배포하지 않았다.
