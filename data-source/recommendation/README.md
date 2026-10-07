# 추천 292개 원재료·식품군 후속 조사

확인일: 2026-10-04. 기존 292개의 ID·연결 food ID·음식명·kcal·탄단지·분량을 유지하고 실제 원문의 주요 재료·식품군을 보강했다. `recommendation-ingredient-research.csv`에는 292개 모두 연결 근거 또는 조사 후 미확인 사유가 있다.

| 항목 | 이번 후속 작업 전 | 현재 |
| --- | ---: | ---: |
| 출처로 확인한 부분 재료 | 151/292 (51.7%) | 163/292 (55.8%) |
| 출처로 확인한 식품군 | 145/292 (49.7%) | 163/292 (55.8%) |
| 스타일 후보 | 106 | 115 |
| 스타일 아침/점심/저녁/간식 | 40/95/96/5 | 44/101/102/7 |
| 재료 미확인 | 141 | 129 |

새 일반 음식의 재료 연결은 12개다. 식품군은 이 12개와 앞선 공식 제품 조사에서 재료를 이미 확보한 6개를 더해 18개 증가했다. 기존 스타일 후보 삭제는 0개다. 같은 음식이 여러 끼니에 허용되므로 끼니별 수를 합해 고유 후보 수로 계산하지 않는다. 참고 조리법과 부분 메뉴 설명으로 제품의 완전 원재료 선언·전체 알레르기·영양·제공량을 확정하지 않는다. 90% 목표는 아직 확보하지 못했다.

작업 시작 시 실제 파일에서 계산한 baseline은 `followup-baseline.json`에 고정했다. `followup-ingredient-evidence-summary.json`에 새 ID·기존 후보 유지·주요 원문 복원·현재 미확인 사유가 있다. 앞선 19개 식품군/16개 후보와 51개 기존 재료 태그는 역사적 baseline으로 남아 있으며 이번 Before 수치로 사용하지 않는다. 이름/태그만 있는 기존 51개나 과거 합집합 집계 169개를 검증 재료로 승격하지 않는다.

농촌진흥청 전통향토음식 전체 목록 3,248개, 식품안전나라 공개 조리법 1~350번, 한국관광공사 음식 목록 970개를 다시 대조했다. 현재 원문 재료를 확보한 참고 조리자료는 516개: 농촌진흥청 전통향토음식 203개, 식약처 211개, 새 농촌진흥청 공개 추천식단 개별 메뉴 102개다. 한국관광공사 음식 설명은 35개를 검토했다. 실제 163개 연결의 주된 근거는 참고 조리법 108개, 공공 음식 정의 47개, 브랜드 공식 주요 재료 설명 8개다. 다른 출처와 겹치는 항목을 중복 집계하지 않는다.

새 출처는 농촌진흥청 공개 웹페이지 `https://api.nongsaro.go.kr/sample/rest/recomendDiet/recomendDiet.jsp`의 공식 샘플 화면이다. 인증키나 인증된 API를 사용하지 않았다. 홈페이지에 명시된 5개 분류의 유한한 목록 30페이지/268개 식단에서 정확한 일반 음식명만 골라 102개 개별 메뉴의 재료 필드를 확보했다. `official-followup-public-recipe-index.csv`, `official-followup-public-recipe-facts.csv`, `followup-public-diet-source-audit.json`에 목록·선별 기준·요청 URL·원문 SHA-256·파서 버전·확인일을 보존한다. 건강 효과를 주장하는 식단 소개나 소스의 영양값을 앱 음식 수치로 복사하지 않는다.

`official-public-recipe-index.csv`, `official-public-recipe-facts.csv`, `official-kto-food-index.csv`는 앞선 정규화 원본이다. 각 조리법의 `mainIngredientText`와 `additionalIngredientText`는 전체 필드를 보존한다. 한국어 `<소>`·`<만두소>`는 HTML 태그가 아닌 원문 재료 구획이다. 이 명시적 속 재료를 구성에 연결하고 다음 구획 직전에서 멈춘다. 개피떡/바람떡의 `<소> 거피팥 420g(2컵)`이 이전 generator에서 빠졌던 문제를 복원했다. 고명·양념을 주요 재료로 추가하지 않아 소량 고명이 견과·채소 후보를 만들어내지 않는다. 간식 추가 2개는 이 개피떡과 정확한 커피빈 그릭치킨 샌드위치의 공식 파프리카·양파 구성이다.

같은 음식명 또는 원문에서 명시된 다른 이름으로만 연결한다. 별도 검토한 표기 차이는 `reviewed-recipe-identity-aliases.csv`에 고정한다. 새 검토는 김치국/김칫국, 마늘쫑/마늘종, 소고기/쇠고기, 실제 생 노각을 무치는 노각생채다. 다른 재료·브랜드·포장 크기·판매 시점을 비슷한 이름만으로 연결하지 않는다. 한국관광공사 계란말이 소개의 양파·당근은 선택 사항이므로 달걀말이에 채소 식품군을 추가하지 않는다. 김말이는 원문 제목과 본문 모두 deep-fried를 명시해 김말이튀김 참고 구성으로만 연결한다.

`reviewed-followup-brand-groups.csv`는 앞선 공식 메뉴를 food ID·품목코드·브랜드·원래 음식명으로 다시 검증한 6개 식품군이다. 공식 description의 literal 재료만 분류하며 복합 빵·소스·향료 속 재료를 추정하지 않는다. 기존 공식 알레르기 8개는 별도 metadata 근거로 유지한다. 이 파일 자체는 알레르기 확인 상태를 생성하지 않는다. 피자스쿨은 확인된 공식 원문이 HTTP여서 식품군 근거를 보존하되 기존 스타일의 HTTPS 최소 출처 조건을 통과시키지 않는다.

일부 오래된 추천식단 재료 칸은 `두부 25.3`처럼 숫자의 단위를 제공하지 않는다. 원문 숫자를 g로 바꾸지 않는다. literal 재료·식품군은 보존하되 주요 재료량 단위가 미확인인 조리법은 스타일에 사용하지 않는다. 같은 식품의 검토된 명칭과 실제 단위가 있는 자료가 있으면 그 자료를 먼저 사용한다. 원문 `1회 섭취량`이 빈 값일 때 재료량 합이나 물의 부피로 완성 음식의 제공량·1인분을 만들지 않는다.

현재 스타일 가중치·건강 정책은 변경하지 않았다. 잡곡/통곡물·콩류·채소·견과/씨앗·과일·생선 등의 확인된 주요 구성, 붉은 고기 감점, 튀김·가공육·명시된 고당 디저트/가당 음료/과일주스 제외를 유지한다. 최소 정보 조건은 주요 재료·식품군·출처·확인일이다. 가중치는 식품 구성 선호 기준이며 건강 효과 점수나 임상 판정이 아니다. 하루 조합 평가와 기존 목표 kcal·알레르기·비선호·당일/같은 끼니 중복 방지를 유지한다.

새 공공 조리자료는 `REFERENCE_PARTIAL`, 공식 주요 재료 설명은 `PARTIAL_DESCRIPTION`이다. 참고 조리자료 알레르기는 UNKNOWN이며 안전으로 처리하지 않는다. 기존 INGREDIENTS_COMPLETE 태그 36개 및 9/27의 `recommendation-292-audit.csv`는 역사적 원본이고 제품의 완전 원재료 인증으로 사용하지 않는다. 실제 조리법/재료가 달라질 수 있다는 화면 안내도 유지한다.

미확인 129개는 정확한 공공·공식 원재료 연결을 확보하지 못한 일반 음식 91개와 브랜드 제품 구성 38개다. 기존 전통향토음식/공공 음식 목록과 이번 전체 공개 추천식단 목록을 다시 대조했지만 해당 이름·재료·제품 version의 같은 identity 근거가 없었다. 브랜드별 403·서비스 종료·메뉴 변경·중량 차이는 `../food-quality/cafe-targeted-investigation.json`와 `product-targeted-investigation.json`의 개별 감사로 남아 있다. C002 인증키 없이 공개 자료로 조사했으며 추정 원재료·알레르기·함량을 추가하지 않았다. 최종 metadata 통합 수치는 `../food-quality/linked-quality-summary.json`을 따른다.

오프라인 재현 순서:

1. `tools/collect_public_recipe_evidence.py`: 캐시된 RDA/MFDS 전체 재료 필드 재생성.
2. `tools/collect_recommendation_followup.py`: 캐시된 새 공개 추천식단에서 exact 개별 메뉴 생성. 새 수집은 --collect가 있을 때만 수행.
3. `tools/build_recommendation_followup.py`: 정확한 공식 메뉴의 검토된 식품군 생성. 최초 baseline은 이후 덮어쓰지 않음.
4. `tools/build_recommendation_ingredient_evidence.py`: 292개 근거·식품군·조사 요약 생성.
5. `tools/generate_verified_ingredient_groups.py`: 검토된 CSV로 GeneratedIngredientGroups.kt 생성.
6. `data-source/recommendation/generate-theme-audit.py`와 `tools/build_recommendation_followup.py --audit`: 스타일 수 및 후속 Before/After 생성.

후속 focused 검사는 `tools/test_recommendation_followup.py`의 신규 5개와 기존 재료 조사 검사의 ID/출처에 관한 변경된 2개만 실행했다. 기대 원문 변수를 잘못 선택한 테스트 한 곳을 수정한 뒤 해당 method만 재실행해 고유 7개 검사 모두 통과했다. 전체 Unit/UI suite는 실행하지 않았다. 실제 하루 조합·생활단위·Samsung 화면 검증은 root 통합 단계에서 수행한다.
