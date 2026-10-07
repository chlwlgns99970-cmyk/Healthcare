# 프랜차이즈 출처와 재현

현재 레지스트리와 번들 데이터의 전수 집계는 `brand-audit.csv`, 전후 요약은
`expansion-audit-summary.json`이 기준이다. 메뉴 수는 영양정보 행과 별도 공식 메뉴명 행의
합계이며, 이름 기준 중복을 접은 수는 `uniqueCombinedMenuIdentityCount`로 따로 표시한다.
기존 K-FIND 원본의 서로 다른 행을 이름만 같다는 이유로 합치지 않는다.

| 항목 | 변경 전 | 변경 후 |
|---|---:|---:|
| 브랜드 | 63 | 63 |
| 메뉴 데이터 행 | 3,116 | 3,979 |
| 브랜드·메뉴명 기준 고유 항목 | 3,050 | 3,913 |
| kcal 제공 | 2,538 | 2,702 |
| 탄수화물·단백질·총지방 모두 제공 | 623 | 716 |
| 생활 단위와 중량이 명시된 serving | 111 | 111 |
| 영양정보 없이 메뉴명만 확인 | 578 | 1,277 |
| 메뉴 0개 브랜드 | 27 | 1 |

이번 품질 작업의 변경 전 수치는 `quality-baseline.json`에 고정했다. 영양정보 미제공
메뉴가 늘어난 이유는 공식 메뉴명 699개를 추가하고 누락 수치를 그대로 보존했기 때문이다.
기존 578개를 영양 수치 없이 기록 가능한 것으로 바꾸지 않았다.

2026-10-04 공식 홈페이지·메뉴 카드·상세·연결 이미지의 원문을 검토한 결과, 빈 브랜드
27개 중 26개에서 863개 메뉴 identity를 확보했다. `zero-brand-investigation.json`은
각 브랜드의 성공/실패 URL, 확인일, HTTP 상태, 원문 해시, 실제 남은 이유를 기록한다.
놀부부대찌개는 공식 홈페이지/메뉴 URL에서 404 또는 timeout이 발생해 메뉴가 여전히 없다.

`quality-nutrition.csv`의 새 영양정보 164개는 슬로우캘리 93, 써브웨이 63, 에그드랍 8개다.
원문의 명시된 g 기준량과 kcal만 복사하며 총탄수화물/총지방이 없는 써브웨이·에그드랍은
빈칸을 유지한다. 중량·열량·탄수화물·단백질·총지방 간 심한 모순 12개, 에그드랍 중량 단위
미제공 15개, 영양 기준량 미제공 6개는 원문과 사유를 `quality-nutrition-exclusions.json`에
보존하고 기록 가능한 음식에서 제외했다. 기준량 g를 1인분/개 중량으로 바꾸지 않는다.

기존 영양정보 미제공 578개는 `menu-information-audit.json`의 ID별 감사에 모두 남긴다.
BON 상세 371개는 브랜드 코드/메뉴 ID로 검토했고 주요 재료 토큰 286개 메뉴, 직접 알레르겐
180개 메뉴, 교차접촉 알레르겐 176개 메뉴를 확인했다. 원문 알레르기표 exact 이름 매치
181개 중 본도시락 36개는 기본 밥/국/반찬을 제외한 주메뉴 표시여서 `PARTIAL_DECLARATION`이다.
현재 상세에 연결된 과거 알레르기표는 원문 날짜와 `staleCandidate`를 보존한다.
나머지 공식 목록은 전체 원재료/알레르기 음성 선언으로 사용하지 않는다.
기존 수동 메뉴 참고 15개는 `legacy-menu-snapshot.csv`로도 내보내어 sidecar identity 누락을 막는다.

## 원자료

- 기존 K-FIND 음식 DB 2026-08-28 스냅샷: 업체명이 레지스트리 브랜드와 정확히 일치하는
  2,410행을 유지한다. 원본 100g/100ml 기준을 그대로 보존한다.
  [공식 DB](https://various.foodsafetykorea.go.kr/nutrient/general/down/list.do)
- 기존 공식 브랜드 영양정보 30행(BBQ·네네치킨·메가MGC커피·매머드커피)은 보존한다.
  개별 출처는 `OFFICIAL_SOURCES.md`에 있다. 기준일만으로 현재 조리법과 같은 값이라고 단정하지 않는다.
- BON 공식 공개 메뉴 API의 브랜드 코드와 메뉴 ID: 본죽 65, 본죽&비빔밥 90,
  본도시락 80, 본설렁탕 56, 본우리반상 37, 멘지 29, 본흑염소·능이삼계탕 14.
  본죽 BF101 / 본죽&비빔밥 BF102 / 본도시락 BF104를 공식 브랜드대로 구분한다.
  2026-10-02 확인이며 `sourceDate`는 API 응답 스냅샷 날짜다. 메뉴 개정일을 뜻하지 않는다.
  메뉴명 371개에 kcal나 중량을 부여하지 않는다. `official-menu-snapshot.csv`, `expansion-sources.json` 참조.
  [공식 본죽 메뉴](https://www.bonif.co.kr/brand/menu?brdCd=BF101)
- 샐러디의 공식 2026.09 영양성분 PDF: 샐러디와 샐러디&샌드위치를 구분한 표준 메뉴 96행.
  원문 제공량(g), kcal, 총탄수화물·단백질·총지방·나트륨을 복사한다.
  교체 베이스·드레싱·음료·개별 토핑을 완성 메뉴로 합산하지 않는다.
  2026-10-02 확인, `salady-nutrition-2026-09.csv` 및 PDF 해시/페이지 기록 참조.
  [공식 PDF](https://salady.com/pdf/nutrition.pdf?ver=260915)
- 김가네 61, 고봉민김밥인 64, 신전떡볶이 48, 두찜 19개의 공식 현재 목록 메뉴명을
  2026-10-04 확인했다. HTML 주석으로 숨겨진 이전 메뉴를 제외하고 브랜드·이름을 중복 제거했다.
  kcal 기준량을 확인하지 못한 항목은 메뉴명만 제공한다. `additional-menu-snapshot.csv`,
  `additional-menu-sources.json`의 페이지 URL·원문 해시를 유지한다.
- 신전떡볶이의 공개 SGS 시험성적서 2개는 **2018-11-16 과거 자료**다.
  순한맛/매운맛의 kcal/100g와 총탄수화물·단백질·총지방·나트륨을 복사하고
  `LEGACY_OFFICIAL_NUTRITION`으로 감사에 구분한다. 현재 메뉴의 kcal와 일치하지만
  조리법 전체가 같은지는 확인하지 못했다. 1인분 중량을 만들지 않고 과거 날짜와 한계를 기록 화면에 유지한다.
  `sinjeon-nutrition-2018-11.csv` 참조.
  [공식 시험성적서 목록](https://www.sinjeon.co.kr/doc/info07.php)

현재 목록 등재는 모든 점포의 재고를 보장하지 않는다. 게시/개정일이 없는 목록의
`sourceDate`는 빈칸이며, 기존 K-FIND 음식·기존 공식 영양 행은 현재 판매 여부/개정일 미확인으로 남긴다.
내부 상태 값은 사용자에게 그대로 노출하지 않는다.

## 앱 처리와 재현 명령

영양정보가 없는 메뉴는 별도 `FranchiseMenu`로 보관한다. `energyKcal`, serving은 null이며
0kcal `FoodItem`을 만들지 않는다. 메뉴를 누르면 브랜드·이름·확인일·공식 URL을 직접 기록에 전달한다.
100g 영양 기준량만 있는 메뉴를 1인분이라고 바꾸지 않는다. 기존 메뉴명 참고 15개도 유지한다.
런타임 자동 갱신 대신 검토된 스냅샷을 번들로 배포하며 Room DB8 구조는 바꾸지 않는다.

`python tools/import_franchise_quality.py`는 저장된 공식 응답/검토된 이미지 해시로
새 메뉴·영양·원재료/직접 알레르겐/교차접촉 CSV 및 최초 작업의 27개/578개 감사를 재현한다.
578개는 최초 메뉴 집합의 역사적 감사이며 현재 nutrition missing 수가 아니다.
`python tools/import_franchise_expansion.py`는 검토된 CSV만으로 Kotlin 메뉴 카탈로그와
공식 영양 CSV를 재현한다. 새 원문 캡처를 검토할 때에만 `--capture-inputs` 또는
`--capture-additional-inputs`를 사용한다. 확인일은 실제 캡처 날짜로 유지한다.
`python tools/generate_franchise_brand_audit.py`로 감사를 재생성한다.
`python tools/test_franchise_quality.py`는 이번 작업의 27개/578개 전수 결과, 누락값,
직접/교차접촉 구분, 모순 제외, 기준량과 생활 단위 구분, 재생성 보존을 집중 검증한다.
기존 `test_franchise_expansion.py`의 이번 데이터 확장에 영향을 받는 집계/재생성 기대값도 유지한다.

계란김밥/고추김밥 원본 전체 열·숨김 열 설정과 제조사 일치 조사는
`unresolved-kimbap-research.json`에 기록했다. 일반 음식 3행의 100ml 기준을 다른 제품의
중량으로 보정하지 않았다. 원본 재조사는 `tools/research_unresolved_kimbap.py`로 재현한다.

## 2026-10-04 후속 정보 복원

후속 작업은 시작 시 실제 최신 missing 메뉴 **1,277개/41개 브랜드**를 다시 연결했다.
`followup-menu-information-audit.json`은 각 stable menu ID·sourceFoodCode·브랜드·이름과
검토한 공식 URL/응답 상태/원문 해시·개별 unresolved 원인을 보존한다.
시작 수는 `food-quality/followup-baseline.json`과 baseline 원본 음식 CSV에서 계산하며
기존 578개를 현재 메뉴 수로 사용하지 않는다.

- 포케올데이 공식 밸런스박스 목록의 **5개**는 제목만 읽던 parser가 이미 공개된
  중량(g)·kcal·탄수화물·단백질·총지방을 버렸다. 카드 내부의 동일 메뉴 필드를 복원해
  `quality-nutrition.csv` **164→169**, `quality-menu-snapshot.csv` **699→694**로 이동한다.
  같은 ID가 그대로 영양 음식으로 연결되며 기존 영양값을 바꾸지 않는다.
  sodium 미제공은 빈칸, 중량만으로 가정한 생활단위는 만들지 않는다.
- 샐러디/샐러디&샌드위치의 같은 브랜드·이름 상세에서 **80개**의 베이스·토핑·기본
  드레싱 **원문 전체**를 복원한다. 시즈닝·복합 소스의 하위 원재료는 공개하지 않아
  `PARTIAL_MENU_COMPONENTS`이며 완전 원재료 선언으로 집계하지 않는다.
- 샐러디 공식 **2026.10 19종 알레르기 PDF**, 포케올데이 공개 HTML의 literal JS 표,
  스쿨푸드 공식 22종 알레르기 문자열, 홍익돈까스 공식 이미지 표를 exact identity에 연결했다.
  `food-metadata-followup.csv` **285행/206개 고유 ID**, canonical 긍정 근거 **204개**다.
  스쿨푸드 메뉴명이 달라진 음식이나 맛 변형은 근사 연결하지 않는다.
- 샐러디 PDF는 제품명·원재료로 알 수 있는 일부 성분의 표시 생략을 허용한다.
  홍익돈까스 표는 반찬을 별도 표시하고 일부 원 번호/표 O·X가 모순된다.
  이 모든 후속 알레르기 행을 `PARTIAL_DECLARATION`으로 유지하며 공란/대시/X를
  알레르기 안전으로 바꾸지 않는다. 우동/메밀 공동 조리시설 원문은 교차접촉으로 별도 보존한다.
- **완전 원재료 0의 원인:** 공식 메뉴 설명·구성·알레르기 표시 원문은 복원했으나
  복합 원재료의 전체 선언 자체가 없다. 알레르기 표의 `재료명` 열을 전체 원재료로 오인하지 않는다.
  HTML script를 통째로 제외하던 기존 처리 때문에 포케올데이 알레르기 양성 표시가 사라진
  경우는 literal 레코드 parser로 복원했다.
- 놀부는 기존 공식 도메인 외 모바일 도메인, 공식 Instagram→공식 Linktree,
  관광공사 명동점 영어/중문 대체 자료를 확인했다. 모바일/도메인 실패, 관광공사 direct HTTP400,
  공개 프로필은 배달 앱만 연결해 현재의 정확한 한국어 메뉴 identity를 새로 만들지 않았다.
  계속 0-menu 1개이며 빈 카드 노출은 없다.

후속 신규 영양 **5개**, 남은 nutrition missing **1,272개**. 기존 source의 에너지/탄단지
모순 및 중량 단위 미표시 등은 평균이나 추정으로 보정하지 않고 개별 감사에 남긴다.
`python tools/research_franchise_followup.py --generate`는 네트워크 없이 후속 evidence와
최신 전체 missing 감사를 결정적으로 재현한다. 공개 원문 캡처는 `--capture`,
`--salady-details`, `--binary URL LABEL`, `--hongik-images`로 요청당 한 번만 저장한다.
`python tools/test_franchise_followup.py -v`의 **신규 6개 집중 검증 통과**:
카드 필드 복원, 기존 영양 불변, 현재 모든 ID 감사, 복합재료 부분 표시,
표 생략·충돌·교차접촉/알류 canonical, 오프라인 재현과 binary integrity.
