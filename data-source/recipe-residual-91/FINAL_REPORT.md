# 잔여 91개 처리 결과 · 2026-10-06

1. **STATUS: PARTIAL.** 실제 앱 연결은 426/516입니다. 90개가 남았으며 516/516 목표를 달성하지 못했습니다. 91개 모두의 새 검색 영수증을 기록했지만 모든 기관/인터넷 경로를 소진했다고 주장하지 않습니다.

2. **전체 Recipe: 516.** 현재 물리 asset의 완전 구성 행과 실제 존재하는 Food ID를 대조했습니다. 과거 보고 숫자만 사용하지 않았습니다.

3. **앱 완전 구성: 425 → 426 / 516.** 새 연결은 애호박젓국의 별도 공식 애호박새우젓국 참고 구성 1개입니다.

4. **남은 음식: 91 → 90.** 원문 원재료의 불확정 부분을 억지로 해소하거나 다른 음식 ID에 붙이지 않았습니다.

5. **분류 변화:** PARTIAL 81→80, UNRESOLVED 6→6, 완전 구성/음식 자체 영양 부족 4→4. 해당 네 항목은 재료 구성 완전성과 실제 앱 등록 가능성을 별개로 유지했습니다.

6. **새 ORIGINAL_COMPLETE: 0.** 원본 recipe asset은 바이트 단위로 동일합니다.

7. **새 REFERENCE_COMPLETE: 1.** MFDS-96 애호박젓국 → MENUZEN-D051300 애호박새우젓국. 원문 조리문에 애호박, 재료표에 새우젓이 명시된 국입니다. 원본 된장/양파 배합을 메뉴젠 두부/들기름/건새우 배합과 혼합하지 않았습니다. 7개 재료 정량·영양·출처가 실제 참고 asset에 연결됩니다.

8. **Food-level nutrition 부족 4개:** 딸기롤샌드위치, 연근치즈구이, 도토리묵밥, 잣스프는 모두 미해결입니다. 정확한 음식 자체 공식 kcal와 basis가 확보되지 않았습니다. 도토리묵비빔밥의 공식 kcal를 원본 도토리묵밥으로 옮기지 않았습니다. 현재 FoodItem.energyKcal가 필수 Double이고 reference-only unknown 등록 정책이 없어 0/NaN/재료 합계로 새 Food를 생성하지 않았습니다. 원문·검색·후보·미채택 이유는 [4개 개별 결과](food-nutrition-four-results.json)에 기록했습니다.

9. **기관별 source 조사/확보/publish:** 아래 수량은 URL 확인 단위이며 서로 다른 레시피 수가 아닙니다. 동일 문서 URL 변형을 포함합니다. 새 검색은 route-00~88 영수증에 91개 모두가 포함됩니다. 추가 집중 검색 영수증도 별도 보존했습니다.

   | 기관 | 확인 URL | 확보 URL | 기존 캡처 재사용 | 실제 참고 publish |
   |---|---:|---:|---:|---:|
   | 충북교육청 | 4 | 4 | 4 | 0 |
   | 샘표 세미네 | 1 | 1 | 1 | 0 |
   | 아워홈 | 1 | 1 | 0 | 0 |
   | 대한당뇨병학회 | 1 | 1 | 1 | 0 |
   | 대한영양사협회 | 14 | 13 | 2 | 0 |
   | 농식품정보누리 | 5 | 5 | 3 | 0 |
   | 수산물안전정보 | 1 | 1 | 1 | 0 |
   | 경기교육청 | 3 | 3 | 3 | 0 |
   | 한국소비자원 | 1 | 0 | 0 | 0 |
   | 농촌진흥청 전통음식 책자 | 1 | 1 | 1 | 0 |
   | 농촌진흥청 메뉴젠 | 1 | 기존 원문 1 재검증 | 1 | 1 |

   32개 문서 URL 중 30개 캡처가 성공했습니다. 실패 2개는 영양사협회 index_10의 404와 소비자원 다운로드 TLS 오류입니다. 소비자원 두릅숙회는 web 도구로 읽었지만 두릅 중량이 없습니다. 이번 publish는 이전에 확보한 메뉴젠 원문 SHA를 검증한 뒤 새로 연결한 것입니다. 새로 내려받은 외부 문서에서 추가 완전 구성은 publish하지 못했습니다. [기관별 원문 목록](institution-source-results.json), [문서 색인](new-document-index.json).

10. **신규 Food: 1.** 애호박새우젓국 / `rda-menuzen-d051300`. 기존 67,356행의 정확한 이름·정규화 이름·alias 중복이 없음을 확인했습니다. [농촌진흥청 메뉴젠 D051300](https://www.nics.go.kr/food/kfi/mgnNewmenumkFoodSelectNew/selectFoodDetail.json?fdCode=D051300) 기관 header의 64.5g 기준 76.9kcal, 탄수화물 2.9g, 단백질 3g, 지방 5.8g을 사용했습니다. 64.5g은 기관 제시 재료 구성 중량이며 조리 후 완성 중량/1인분으로 주장하지 않습니다. sodium은 미확보 상태를 유지했습니다. 재료 합계를 공식 kcal로 사용하지 않았습니다.

11. **새로 앱 완전 연결된 전체 목록:** [new-app-complete-foods.json](new-app-complete-foods.json). 원문 SHA, recipe ID, verified alias, Food ID, 공식 음식 영양, 전체 재료의 원문 정량·영양 ID·계산 열량·단위를 포함합니다.

12. **여전히 남은 전체 90개:** [개별 검토 문서](remaining-90-review.md), [전체 상세 JSON](remaining-90-foods.json). 음식마다 Food ID/미연결 상태, 미확정 재료와 unit/identity 이유, 원문, 기존 후보와 미채택 이유, 새 검색, 문서 페이지 색인, 추가 경로를 보존했습니다. 신규 공개 검색에서 완전한 근거를 얻지 못한 항목의 다음 경로는 원 발행기관의 규격·계량·완성 음식 영양 원표 확보입니다. 해당 원표를 이미 확보했거나 조사 경로가 전부 소진됐다고 기록하지 않았습니다.

13. **이번 수정 파일:** 공통 asset `food_items.csv`(+1), `food_metadata.csv`(+1), `official_recipe_reference_estimates.csv`(+7), `food_data_manifest.properties`; 상태 ledger `data-source/recipe-final-residual/recipe-final-states.json`; QA test `Residual91SamsungTest.kt`; `tools/audit_recipe_residual_91.py`, `collect_recipe_residual_91.py`, `publish_recipe_residual_91.py`, `verify_recipe_residual_91.py`; 기존 보존 helper에는 작업 폴더 allowlist만 추가했습니다. 이번 근거/검증 파일은 이 디렉터리에 저장했습니다. 기존 dirty working tree를 되돌리지 않았습니다.

14. **Data test: PASS, 22개 검사.** 요청한 18개 범주와 metadata/reference 기존 행 보존, QA APK asset 동등성, 516개 상태의 물리 연결 일치까지 확인했습니다. [검사 결과](data-verification.json). alias 검사의 최초 문자열 비교 실패는 기존 `|alias|` 직렬화 형식에 맞춰 parser 검사로 수정 후 PASS했습니다.

15. **관련 Unit test: PASS, 26개, 실패/오류/skip 0.** FullRecipeReferenceTest 6, RecipeMenuCompletionTest 4, FoodDetailPolicyTest 7, RecordSaveConfirmationTest 7, SaveAcknowledgementTest 2. [결과](unit-verification.json).

16. **QA build: PASS.** `assembleQa`, `assembleQaAndroidTest` 성공. QA applicationId는 `com.example.healthcare.qa`, test는 `.qa.test`입니다. Release build, 전체 lint, 전체 회귀검증은 실행하지 않았습니다. build log: `app/build/recipe-residual-91-build.log`.

17. **Samsung QA: PASS, 신규 연결 1/1.** SM-S948N에서 Residual91SamsungTest의 실제 검색 → 상세 → 공식 참고 구성 7행/예상 열량 → 64.5g/공식 77kcal → 저장 완료 팝업 → 기록 상세 → 편집/저장 팝업을 검증했습니다. 테스트 중 기존 식사 기록을 보존하고 새 시험 기록만 삭제했습니다. 실행 결과: `app/build/recipe-residual-91/samsung-instrumentation.txt`; 실제 상세 캡처: `new-food-detail.png`. QA만 `install -r`했고 제품 앱에는 설치/화면 조작을 하지 않았습니다.

18. **DB: version8, migration 추가0.** QA 설치 전 17개 private 파일 36,649,984byte를 백업했습니다. 테스트 후 복원/검증에서 17개 파일 전부 바이트 일치, schema 동일, 변경 table0. QA는 복원 완료 후 force-stop 상태로 두었습니다. 복원된 DB의 Food 수는 설치 전 67,356개이며 설치된 QA APK에는 67,357행의 새 asset이 있습니다. 다음 실행 시 기존 seeder 정책을 따릅니다.

19. **기존 Food/공식 kcal 보존: PASS.** 기존 67,356 Food의 모든 셀이 동일합니다. 기존 metadata 행 전체와 참고 recipe 7,022행을 보존했습니다. Food 총수는 신규1 포함67,357, 참고 ingredient 행은7,029입니다. 새 참고 합계는 공식 Food 영양과 독립적으로 유지합니다.

20. **MealRecord 보존: PASS.** Samsung 복원 전후 실제 meal_records 4개와 행 SHA가 같고 전 DB 테이블 변경이 없습니다. MealRecord 계산/통계/추천 코드는 이번 작업 전 baseline SHA와 동일합니다.

21. **저장 완료 팝업: targeted PASS.** 실제 신규 저장/편집 팝업과 관련 Unit 9개가 통과했습니다. 저장 버튼 8개 경로의 공통 코드는 수정하지 않았고 SHA 동일성을 확인했습니다. 8개 경로 전체 기기 회귀를 재실행한 것은 아닙니다. Home, updater, 검색 상태, 프랜차이즈, 추천 UI도 수정하지 않았습니다.

22. **QUESTION_REQUIRED: 없음.** Samsung 연결 요청은 처리됐습니다. 90개 데이터 부족을 임의값 사용 승인으로 해결하지 않습니다. 추가 원표 확보는 미완료 상태입니다.

23. **Production: versionCode7 / versionName1.0.6 유지.** 제품 설치0, 초기화0, overwrite0, 배포0. Samsung 제품 앱의 firstInstallTime/lastUpdateTime/version 식별 정보가 동일합니다. 제품 DB/파일에 접근하지 않았습니다. 실제 제품 사용자 데이터의 전후 hash를 읽어서 검증한 것은 아닙니다.
