# 국내 배달 체인 공식 source 확장 — 2026-10-04

기존 63개 브랜드와 본죽 65개 원본 메뉴를 보존하고 실제 공식 메뉴가 확인된 26개 브랜드를 연결했다. 새 입력은 메뉴 전용 1,390개, 영양표 131개, exact identity metadata 1,521개다. 브랜드 수는 89개가 된다. 매장 재고와 공식 웹 목록 전체의 완전성을 보증하지 않는다.

`delivery-chain-candidate-audit.json`은 요청한 18개 업종의 기존·추가·미확인 후보 106개와 URL별 성공/실패 및 사유를 기록한다. 공식 페이지 캡처는 명시적으로 선택한 유한 목록이며 배달앱 scraping, 자동 무한 탐색, 인증 또는 CAPTCHA 우회는 사용하지 않았다. 브랜드를 발견해도 메뉴가 확인되지 않으면 앱 registry에 빈 브랜드를 추가하지 않는다.

| 추가 브랜드 | 메뉴 전용 | 영양표 | 합계 |
|---|---:|---:|---:|
| 피자스쿨 | 38 | 26 | 64 |
| 파파존스 | 37 | 0 | 37 |
| 청년피자 | 20 | 25 | 45 |
| 노랑통닭 | 47 | 0 | 47 |
| 지코바 | 3 | 5 | 8 |
| 처갓집양념치킨(처갓집 alias) | 20 | 0 | 20 |
| 피자마루 | 31 | 0 | 31 |
| 멕시카나 | 33 | 0 | 33 |
| 60계치킨 | 60 | 0 | 60 |
| 반올림피자 | 2 | 0 | 2 |
| 프랭크버거 | 34 | 0 | 34 |
| 노브랜드버거 | 55 | 0 | 55 |
| 새마을식당 | 11 | 0 | 11 |
| 빽다방 | 359 | 0 | 359 |
| 배스킨라빈스 | 104 | 35 | 139 |
| 파리바게뜨 | 16 | 40 | 56 |
| 요아정 | 95 | 0 | 95 |
| 미소야 | 54 | 0 | 54 |
| 할리스 | 26 | 0 | 26 |
| 7번가피자 | 27 | 0 | 27 |
| 토마토도시락 | 109 | 0 | 109 |
| 동대문엽기떡볶이 | 53 | 0 | 53 |
| 공차 | 52 | 0 | 52 |
| 던킨 | 60 | 0 | 60 |
| 족발야시장 | 12 | 0 | 12 |
| 호식이두마리치킨 | 32 | 0 | 32 |
| 합계 | 1,390 | 131 | 1,521 |

메뉴 합계에는 공식 표에 별도로 명시된 오리지널/R/L 영양 variant가 포함된다. 같은 브랜드·정규화 이름의 메뉴와 영양 항목은 중복시키지 않는다. 피자스쿨의 크러스트/토핑 옵션 및 파파존스 굿즈를 음식 메뉴로 생성하지 않는다. 메뉴 이름은 원문 표기를 보존한다.

## 원문과 영양 근거

- 공식 HTML 메뉴 card/list: 모든 추가 브랜드의 실제 identity. `raw/delivery-chain-pages.json`에 URL, 확인일, 성공/실패, 원본 bytes SHA-256 및 UTF-8 text SHA-256을 보존한다.
- 공식 HTML 영양표: 피자스쿨·청년피자·배스킨라빈스·파리바게뜨. 명시된 1회량/총 내용량의 g와 kcal를 사용한다. 당류를 탄수화물로, 포화지방을 총지방으로 바꾸지 않는다.
- 공식 원문 이미지: 지코바 메뉴에 직접 연결된 `info_1.jpg`–`info_5.jpg`와 `txt.jpg`. 육안 검증 전사 및 hash가 `gcova-reviewed-nutrition.csv`, `gcova-label-sources.json`에 있다. `100g당 영양량`이므로 한 마리의 중량·1인분으로 바꾸지 않는다. 조리 전 `950g 이상`도 serving으로 사용하지 않는다.
- 빽다방은 컵용량이 영양 기준 질량을 확정하지 않는다. 요아정은 선택 분량/조합 및 포화지방 필드의 UI 맥락이 완전히 연결되지 않았다. 제공된 수치는 metadata의 `rawNutritionText`로 보존하고 recordable nutrient를 추정하지 않는다.
- 파리바게뜨 `샤인머스캣 그린티 제로`, `산베네데토 워터`의 kcal 0 및 protein 0은 명시된 공식 라벨의 값이다. 다른 빈 수치는 0으로 대체하지 않는다.
- 청년피자 10개 R/L 행은 공식 표의 `1조각 = Ng`를 그대로 연결한다. 피자스쿨 26개는 명시한 2조각 또는 3조각의 g를 그 조각수로 나눈 exact arithmetic만 `VERIFIED_CONVERSION`으로 보존한다. 총피자중량이나 추정 조각수를 쓰지 않는다. HTTP 공식 원문을 임의 HTTPS URL로 바꾸지 않는다.

metadata는 확인된 부분 재료와 positive allergen 463개 identity를 연결한다. 모두 원문 description/label에 근거하며 complete recipe/allergen-safe 표시를 만들지 않는다. 전 1,521개 identity가 자신의 정확한 sourceFoodCode/name과 menuCategory/sourceCategory/sourceHash를 가진다. 피자 브랜드의 파스타·치즈볼과 실제 피자를 분리하고, 파파존스 `더블 치즈버거`는 공식 피자 그룹으로 연결한다. 남은 메뉴 category는 빈 값으로 보존하여 앱의 개별 메뉴 정책이 판정한다. 미소야 `신선합니다/맛있습니다/건강합니다` 세 marketing 문구는 실제 메뉴에서 제외한다.

## 미확인 후보

펠리카나·고피자는 동적 페이지에 실제 menu card가 없었다. 배떡·신참떡볶이·롤링파스타는 이미지의 exact identity/table 전사가 미완료다. 뚜레쥬르·설빙·푸라닭은 entry/redirect shell, 더벤티는 브랜드 landing의 현재 menu route 미확인, 빽보이피자는 timeout, 또래오래는 TLS 검증 실패다. 쥬씨·가장맛있는족발·신의주찹쌀순대는 current official domain 미확인, 오봉도시락·짬뽕지존은 placeholder가 반환되었다. 땅스부대찌개 후보 domain은 parked/for-sale 페이지여서 음식 source로 거절했다. 각 시도와 exact URL은 candidate audit에 남아 있다.

## 재현과 검증

`tools/import_delivery_chains.py`는 저장한 raw 공식 source만 읽어 5개 source sidecar와 `FranchiseCatalog.kt`의 generated registry block을 재현한다. registry 밖 기존 entry/method는 수정하지 않는다. `tools/import_franchise_expansion.py`가 이 메뉴/영양 입력을 기존 입력에 추가하여 Kotlin/food asset을 생성한다. 공통 asset/metadata/audit의 최종 생성은 root 통합 단계에서 수행한다.

```text
python tools/import_delivery_chains.py
python tools/test_delivery_chain_expansion.py
```

targeted Python 검사 7개 PASS: 필수 6개/18업종, exact source hash/UNKNOWN, 메뉴 분류 false positive, 영양 기준과 missing macros, positive allergen/exact ID, 기존 63 registry/본죽 65 보존, 오프라인 재생성 결정성. Gradle/adb/전체 test/Release는 이 작업에서 실행하지 않았다. DB 8 및 제품 7/1.0.6 변경은 없다.
