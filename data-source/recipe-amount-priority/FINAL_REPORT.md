# 재료 중량 우선 추가 조사 결과 — 2026-10-05

1. **STATUS: PARTIAL**
   원본 516개 완전 연결 목표는 미달성이다. 이번 단계는 새로운 공식 bulk/책자 자료의 확보·색인·근거 분리 및 안전성 검증까지 진행했다. 앱에 새로운 대체 레시피를 게시한 단계가 아니다.

2. **Recipe Before → After**
   complete 41 → 41, partial 409 → 409, unlinkable 66 → 66. 조사 평균 5개와 별도 김밥 레시피 2개를 원본의 신규 complete로 집계하지 않았다.

3. **Ingredient Before → After**
   전체 4,258행, linked 2,357 → 2,357, excluded 154 → 154, unlinked 1,747 → 1,747. 기존 linked/excluded 2,511행을 포함해 기존 판정 전체를 그대로 보존했다.

4. **잔여 원인**
   primary identity 850, unit 332, amount 563, nutrition 1, recipe source 1. 새 자료의 동일 음식명과 원본 recipe ID의 동일 배합은 별개다. 공식 자료들 사이의 계량 중량 충돌, 컵 기준량·제품·원재료 형태 불명확성이 남는다. 확보한 자료에서 확인하지 못한 것이며, 모든 미확보 원전의 정보 부재를 증명한 결과는 아니다.

5. **Amount**
   원본 563행 해결 0, 잔여 563. 원본에 적용한 상태는 NO_SAFE_AMOUNT 563행이다.
   새 공식 김밥 레시피 2개에서 OFFICIAL_REFERENCE_RECIPE_AMOUNT 23행을 별도 확보했고 9행은 재료 영양값까지 연결했다. 두 레시피는 여전히 부분 연결이다. 나머지 ORIGINAL_EXACT_AMOUNT / OFFICIAL_ALTERNATE_FORMAT_AMOUNT / OFFICIAL_PORTION_DERIVED 신규 확정은 0이다.
   KDCA 공식 XLSX 613개 조사 평균 구성·13,165개 재료행을 색인했다. amount 대상과 정확한 음식명이 일치하는 26개 음식·43개 조사 구성이 있어 원본 161행에 별도 참고 근거를 기록했다. 그중 5개 조사 구성은 정확한 qualified 영양 항목까지 연결됐다. 기관이 조리용 정보로 적절하지 않다고 명시한 자료이므로 요리 레시피·원본 중량으로 대체하지 않았다.

6. **Identity**
   원본 850행 신규 해결 0. 별도 공식 김밥 참고 자료의 9개 연결은 EXACT_VERIFIED / ALIAS_VERIFIED / CONTEXT_VERIFIED로 구분했다. 정확한 마요네즈·참기름·매실청, 조리 전 생달걀·생당근·생파인애플, 기존 검증 깻잎·설탕 alias를 사용했다. 생선 종류, 새싹채소 구성, 밥 종류, 파프리카/피망 종류, 저염간장 제품 등은 임의 확정하지 않았다.

7. **Unit**
   원본 332행 해결 0. 새 책자와 기존 원본의 같은 재료·단위 조합을 함께 비교했다. 238행은 공식 중량 충돌, 74행은 일치 형태 근거 없음, 20행은 단일 수치 후보지만 형태·계량 정의 추가 검토가 필요하다.
   예: 다진 파 작은술 3/4/5g, 깨소금 작은술 3/4/6g. 평균이나 유리한 단일 값을 선택하지 않았다. 단일 후보가 있다고 확정으로 처리하지 않았다. 전체 컵을 200g으로 환산하지 않았다.

8. **김밥**
   기존 두 원본: linked 18 → 18, excluded 1 → 1, unlinked 18 → 18, complete false.
   미연결 18행의 새 조사 근거와 실패 이유를 kimbap-new-attempts.json에 기록했다. 식약처 3권 PDF 20쪽 새싹참치김밥·22쪽 오징어불고기김밥에서 전체 표기 재료 23행의 g 중량을 확인했다. 원본 충무김밥/김밥과 다른 구성이라 원본에 혼합하지 않았다. PDF 재료 표는 화면으로도 확인했다.

9. **Nutrition 1건**
   MFDS-295 주꾸미먹물: 기존 국가표준식품성분표·MFDS·USDA·공공 원재료 인덱스를 재사용했고 새 KDCA 자료도 대조했다. 해당 종 먹물 자체의 기준량별 열량 근거는 확보하지 못했다. 주꾸미 몸통·전체나 다른 종 먹물로 대체하지 않았다.

10. **Recipe source 1건**
    MFDS-223 딸기잼(4인분): 기존 상세 HTML·원본 audit를 재사용했고 추가 확보한 식약처 공식 책자 2~7권을 조사했다. 동일 원본의 딸기/당류별 투입량은 확인하지 못했다. 다른 잼 레시피나 완제품 kcal로 배합을 역산하지 않았다.

11. **새 complete recipe**
    원본 신규 0개. 별도 김밥 2개도 부분 연결이다. 정확한 영양 연결을 마친 5개 KDCA 구성은 조사 평균이며 원본 recipe complete 목록에 포함하지 않았다.

12. **새 공식 source와 기여**
    - 질병관리본부 국민건강영양조사 음식별 식품재료량 자료집(2019), 공개 record 281의 XLSX/PDF: 613개 평균 구성 색인, 원본 161행의 별도 근거, 정확한 영양 연결 5개 평균 구성. 원본 연결 증가 0.
    - 질병관리청 2022년 음식별 식품재료량 DB 구축 최종보고서(record 417): 김밥 실험조리·분량 산출의 한계 확인. 원본 연결 증가 0.
    - 식약처 우리 몸이 원하는 삼삼한 밥상 2~7권: 별도 김밥 중량 23행, 영양 연결 9행, 재료별 계량 비교·충돌 검출. 원본 연결 증가 0.
    - 농식품정보누리 멸치호두조림: 같은 음식명이지만 원본과 다른 재료/중량이며 계량 미확정도 포함함을 확인, 대체 적용하지 않음.
    기존 책자 1권/영양 bulk/원본 recipe cache를 다시 다운로드하지 않았다. 새 원본은 SHA256과 요청 방식·수집일을 source-captures.json에 기록했다. KDCA 다운로드는 정상 공개 사이트의 같은 세션에서 새 공개 다운로드 URL을 받아 즉시 이용하는 방식으로 재현 가능하게 구현했다.

13. **미완성 recipe artifact**
    data-source/recipe-amount-priority/remaining-recipes.json, 475개. 음식명, 미연결 ingredient, 기존 실패 이유, 새 확인 source, query/result/date/nextPossibleSource를 포함한다. 이 목록 작성만으로 완전 연결 또는 모든 원전 검토 완료를 주장하지 않는다.

14. **이번 수정 파일**
    tools/collect_recipe_amount_sources.py, tools/index_recipe_amount_references.py, tools/audit_recipe_book_portions.py, tools/audit_recipe_amount_priority.py, tools/test_recipe_amount_priority.py 및 data-source/recipe-amount-priority/의 원본·색인·감사·결과 자료. app 코딩 파일·게시 recipe asset은 이번 단계에서 변경하지 않았다.

15. **테스트**
    새 Data targeted 12/12 PASS (1.941초). alternate amount, PDF/XLSX parser, canonical/context identity, conflicting ingredient-specific portions, bulk markers, linkage kcal math, fake amount/nutrition 방지, provenance, complete 집계, 공식 kcal/asset 보존을 확인했다.
    로그: app/build/recipe-amount-priority/data-tests.log. 증거: 같은 폴더 verification.json.
    앱 asset SHA가 직전 최종 QA APK 및 현재 소스와 동일함을 확인했다. 기존 Unit 6/6·Samsung 5/5 결과는 기존 앱 코드/asset 검증 근거로만 재사용한다. 새 대체 recipe는 게시하지 않았으므로 새 UI 표시 검증은 미실행이다. 전체 회귀/전체 Lint/release build 없음.

16. **Samsung QA**
    SM-S948N adb authorized 연결을 확인했다. 이번 단계에서 신규 설치·UI 조작·private 데이터 접근 없음. 기존 동일 asset QA의 신규 complete 3개·김밥 partial·공식 참고 안내·기록 kcal 검증 5/5 PASS 근거 유지. 새 대체 recipe UI는 미검증이며 PASS로 보고하지 않는다.

17. **DB**
    version 8, migration 0. 이번 단계는 로컬 공식 자료·감사 파일만 변경했으며 MealRecord/설정/선호·제외/제품 사용자 데이터 접근·수정 없음. 공식 food/product/franchise 및 참고 asset SHA 모두 동일.

18. **QUESTION_REQUIRED**
    없음. DB 변경이나 모호한 수치를 허용하는 승인 요청은 하지 않았다. 단일 계량 후보의 추가 검토 및 새 대체 recipe의 앱 게시·UI 검증은 미완료 범위로 남는다.

19. **Production**
    versionCode 7, versionName 1.0.6. 설치·덮어쓰기·삭제·초기화·배포·버전 증가 0건. GitHub Release/Supabase/Vercel 변경 0건.
