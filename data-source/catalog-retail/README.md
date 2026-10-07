# 국내 소비자 가공식품 출처 파이프라인

`collect_catalog_retail.py`는 인증키 없이 접근 가능한 제조사 공식 제품 및
영양정보 응답을 저장한다. `import_catalog_retail.py`는 SHA-256이 고정된
K-FIND 2026-08-28 가공식품 원본 316,734행과 저장된 공식 응답에서 추가 행을
만든다. 공통 앱 CSV/metadata는 이 도구가 직접 수정하지 않는다.

원본 전체 분류를 검사한 후 `scope.json`의 소비자 제품 제조사·편의점 공급사·
명시 PB/소매점 제품명에 속하는 식품을 채택한다. 수량 cap은 없다. 명시 업소용,
산업 원료, 냉동 생지, 벌크, 별도 검증 없는 5kg/5L 초과 포장은 제외 사유와
정확한 원본 식품코드를 `excluded-scope.csv`에 남긴다. 미확인 제조사를 제외한
상태는 해당 제품이 실제 판매되지 않는다는 뜻이 아니다. 차후 공식 소비자제품
목록에서 확인되면 scope를 확장할 수 있다.

기존 11,921개 제품 및 일반식품의 ID는 baseline에서 읽고 보존한다. 신규 행은
정확한 식품코드에만 연결한다. 이름·제조사·영양값이 같은 행도 서로 다른
원본 ID이면 합치지 않는다. 공식 제조사 행은 K-FIND 과거 행과 이름으로
연결하지 않고 패키지/맛/용량 식별자로 분리한다.

출력:

- `product-items.csv`: 추가 FoodItem 행. kcal 미제공은 0으로 보충하지 않는다.
- `raw-identity-fields.csv`: 추가 K-FIND 행의 공식 분류, 중량, 품목보고번호.
- `evidence.csv`: 정확한 ID별 원재료, 알레르겐, 교차접촉, 생활단위, 출처.
- `alias-patches.csv`: 기존 ID에 대한 검색 별칭 보강. 원본 제품 정체성은 유지.
- `excluded-scope.csv`: 범위 제외 전수 결과. 제조사/총내용량/원본코드 보존.
- `unresolved.csv`: 공식 kcal/기준량/패키지 근거 부족.
- `search-smoke.csv`: 제품군별 여러 제품·긴 품목명·브랜드·띄어쓰기 변형.
- `summary.json`, `validation.json`: 채택/제외 수 및 targeted 검증 결과.
- `raw/*.raw`, `raw/*.json`: 정확한 HTTP 요청, 원문 응답, checked date, 해시.

날짜와 상태는 분리한다. K-FIND `HISTORICAL_SNAPSHOT`은 공식 등록 원본의
날짜이며 현재 판매 여부는 `availabilityStatus=UNKNOWN`이다. 빙그레의 현재
공식 목록은 `CURRENT_OFFICIAL_CATALOG`이며 제품 영양정보의 실제 업데이트일을
별도로 보존한다. 농심 공식 표시 이미지에는 게시일이 없어 `UNKNOWN_DATE`로
기록하고 확인 날짜를 대신 최신 출시일로 주장하지 않는다.

원재료·알레르겐 상태는 `UNKNOWN`, 공식 전체 표시사항에 한해
`COMPLETE_DECLARATION` / `CONFIRMED_LABEL`이다. 교차접촉 문구는
`crossContactText` / `mayContainAllergens`로 분리한다. 긍정 원재료 알레르겐과
같은 시설 경고를 합쳐서 실제 함유라고 주장하지 않는다. 빈 필드는 안전이나
영양 0이라는 뜻이 아니다. 영양 API의 미만/이하 경계값도 정확한 수치로 복사하지
않는다.

올리브 짜파게티는 농심 공식몰 실제 5봉지 외포장의 표시 이미지를 전체 검수했고
`1봉지(140g)` 기준 610kcal, 탄수화물 96g, 단백질 9g, 지방 20g을 저장했다.
5봉지 묶음을 1봉으로 환산하지 않는다. `chapagetti-label-review.json`의 전사 내용은
공식 이미지 바이트 해시에 결합되어 이미지 변경 시 재검수를 요구한다.

재현 명령 (PowerShell):

```powershell
$python = 'C:\Users\young\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$env:PYTHONIOENCODING = 'utf-8'
& $python tools/collect_catalog_retail.py
& $python tools/import_catalog_retail.py
& $python tools/test_catalog_retail.py
```

Collector는 검증된 성공 캐시를 재사용한다. `--refresh`는 명시적 공식 데이터
재수집이며 출처 변경 시 검수 기록도 갱신해야 한다. 네트워크 오류에는 무한
재시도가 없으며 실패 URL/원인을 보존한다. importer/tests는 오프라인으로 작동한다.
baseline 경로는 `app/build/catalog-qa/baseline/app/src/main/assets/fooddata`다.

공통 통합 시 추가 제품 CSV를 기존 제품 CSV 뒤에 정확 ID로 추가하고 alias는
기존 alias와 합집합만 적용한다. 추가 raw/evidence를 공통 metadata generator에
연결하고 `OFFICIAL-RETAIL-PRODUCT` sourceType을 앱 product 검색에 포함한다.
`sourceStatus`, `sourceHash`, `sourceVersion`, `distributionGroup`을 출처 audit에
남긴다. 이 단계의 Python smoke는 Android 화면·실제 ranking·Samsung QA를
대신 인증하지 않는다.
