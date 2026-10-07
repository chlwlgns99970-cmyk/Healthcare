# 생활단위 추가 보강

`FoodItem`의 이름·코드·영양값·영양 계산 기준은 이 파이프라인에서 수정하지 않습니다.
`followup-household-evidence.csv`는 15개의 정확한 음식 identity에 대한 생활단위 근거이며,
생성기는 원래 ID, sourceFoodCode, 이름, 브랜드를 모두 검증한 뒤 metadata에 연결합니다.

- USDA SR Legacy: 공식 `food.csv`와 `food_portion.csv`의 FDC ID + portion ID를 연결했습니다. 13개 중 달걀·바나나 2개는 기존 환산의 재확인, 11개는 새 개/장/조각 근거입니다. 기존 단위 이름을 유지하며 공식 크기 조건을 `servingSourceSize`로 표시합니다. 다른 동명이품·한국 음식 평균값에 적용하지 않습니다.
- 매일두유 99.9: 정확한 품목보고번호 19810227007211/20000441043301, 제조공장, 원래 제품명, 190ml를 함께 확인했습니다. 공식 개별 멸균팩 1팩 190ml를 연결합니다. 200ml 섭취참고량이나 950ml 변형 제품을 포장량으로 사용하지 않습니다.
- 원재료 전체 표시: 공식몰이 링크한 원본 이미지의 플레인 제품 표를 직접 확인했습니다. 복합원재료 괄호를 포함한 전체 원문을 보존하고, 대두 함유와 같은 시설 표시를 구분합니다. 토마토·복숭아는 기존 canonical 알레르기 체계에 추가하지 않고 교차접촉 원문에 보존합니다.
- 원래 고형 100ml 김밥: 정확한 g 중량·밀도·줄 수 연결이 없어 계속 미확정입니다. 2012 식약처 자료의 다른 김밥 1인분 200g은 영양값과 identity가 일치하지 않으므로 연결하지 않습니다.

`followup-household-audit.csv`는 전체 food identity를 검사합니다. 이름 기반 `expectationHint`는 조사 대상 분류만 제공하며 환산 근거가 아닙니다. 공식 원본 전체 열을 확인했어도 생활단위 선언이 없으면 `HOUSEHOLD_EXPECTATION_NEEDS_EXACT_SOURCE`로 남깁니다. baseline에 없던 새 음식은 실제 Kotlin policy 감사 전까지 `RUNTIME_AUDIT_PENDING_FOR_NEW_IDENTITY`로 표시합니다.

2026-10-04 최종 Kotlin runtime 감사는 31,849개를 모두 포함하며 미검사 identity는 0개입니다. 실제 생활단위는 14,699개(공식 제공량 11,979개 + 검증된 환산 2,720개), g/ml만 가능한 항목은 14,768개(g 13,957개 + ml 811개), 미확정은 2,382개입니다. 생활단위가 기대될 수 있는 이름 조사 힌트 5,977개는 검증된 환산 coverage에 포함하지 않습니다. 이 5,977개 전체의 현재 제조사 사이트를 개별 재방문했다는 의미도 아닙니다.

`followup-household-recommendation-source-amount-audit.csv`는 현재 추천 292개에 연결된 292행(고유 음식 291개)을 원본 식품중량과 비교합니다. 원본 행은 모두 연결됐고 같은 g 단위의 식품중량은 290행에 있으며, 현재 ingredient baseAmount가 원본 중량을 넘는 행은 144개입니다. 이것은 기본 후보량 비교이며 최종 선택된 식단의 초과 건수와 같지 않습니다. 원본 `식품중량`은 `영양성분함량기준량=100g`과 별도 열입니다. 어탕 D305-239000000-0001의 식품중량은 150g, 멸치볶음 D110-472000000-0001은 50g이며 두 항목의 1인(회)분량/1회 섭취참고량은 비어 있습니다. 따라서 이 중량으로 그릇이나 인분을 만들지 않습니다. 무국물 D305-259000000-0001과 김말이튀김 D312-541000000-0001의 식품중량은 원본에서 비어 있어 중량 비교에서 제외합니다. 원본 workbook SHA, 실제 160개 열 및 19,617개 행 재검사 결과는 같은 이름의 JSON에 보존했습니다.

공식 [공공데이터 제공 표준 전문](https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000003049184&fileDetailSn=2)의 음식 표에서 `식품중량`은 “음식의 1회 분량”으로 정의합니다. 가공식품 표에서는 “제품의 총 내용량”입니다. 해당 2024-10-30 공식 HWPX(2,256,495 bytes, SHA256 `d3d464f6a9dd9f6dfd3ff61fec966c95ccce8aec085c234ba3af9e665d3c67cd`)를 직접 읽어 확인했습니다. 음식 식품중량은 분석시료 질량으로 분류하지 않으며, 개인별 최대 허용 섭취량으로도 해석하지 않습니다.

`followup-household-actual-plan-amount-audit.csv`는 최종 Unit 실제 식단 32행과 Samsung 실제 식단 로그 20행, 총 52선택행(고유 음식 33개)을 동일 identity의 원본 중량과 비교합니다. 모든 선택행에서 g 기준 비교가 가능했고 원본 1회 분량을 초과한 것은 16행(고유 음식 10개)입니다. 이는 실제 섭취가 비현실적이라고 확정한 건수가 아니라 원본 참고 분량과의 차이입니다. 이전 기본 후보량 초과 144행 목록과 겹치는 실제 초과는 13행입니다. 미숫가루 D120-730000000-0001은 원본 ‘음료 및 차류’, 100g당 70kcal, 식품중량 300g이므로 선택 250/300g을 마른 분말량으로 판단하지 않습니다. 충무김밥 400g은 원본 400g과 같고, 잣죽 450g은 원본 700g 이하, 고등어찌개 550g은 원본 600g 이하입니다. 꽁치찌개 550g/원본 300g, 두부전골 550g/원본 500g 등 차이는 표에 남겼으며, 이 감사에서 추천 정책·음식 asset·단위 변환을 추가 변경하지 않았습니다. 같은 이름의 JSON은 실제 로그 SHA와 공식 정의, 해당 6개 exact code의 원본 영양값·분류·중량을 보존합니다.

공식 원자료 다운로드:

```powershell
& $python tools/collect_followup_household_sources.py --only usda-sr-legacy.zip --only maeil-99-9-190ml.html --only maeil-99-9-declaration.jpg
```

캐시 SHA/크기 확인 후 결정론적 생성 및 감사:

```powershell
& $python tools/audit_followup_household_servings.py
# 최종 runtime serving CSV가 생성되면 실제 절대 경로로 다시 집계합니다.
$runtimeServingCsv = (Resolve-Path 'app/build/food-quality-followup/final-serving-audit.csv').Path
& $python tools/audit_followup_household_servings.py --runtime $runtimeServingCsv
& $python -m unittest discover -s tools -p test_followup_household_servings.py -v
```

원본 링크:

- [USDA SR Legacy 공식 다운로드](https://fdc.nal.usda.gov/download-datasets/)
- [USDA food_portion 필드 정의](https://fdc.nal.usda.gov/docs/Download_Field_Descriptions_Oct2020.pdf)
- [매일두유 99.9 공식 제품가이드](https://productguide.maeil.com/products/99-9-190ml)
- [매일두유 공식몰](https://direct.maeil.com/m/product/productView.do?productCode=P00145)
- [원본 전체 제품 표시 이미지](https://freshmaeil.cafe24.com/freshmaeil/05beverage/a_maeilsoy/MaeilSoy99_detail_09.jpg)

전체 원재료 완전성은 해당 현재 공식 표시 원문 전체를 확보했다는 의미이며, 실제 사용자가 가진 포장의 제조 시점이나 변경 레시피까지 보증하지 않습니다.
