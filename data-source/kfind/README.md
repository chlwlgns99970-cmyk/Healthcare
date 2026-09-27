# K-FIND source data

`kfind-food-db-2026-08-28.xlsx` is the unmodified official 음식 DB downloaded from the
K-FIND 식품영양성분 데이터베이스 on 2026-09-17.

- Provider: 식품의약품안전처
- Data version: 2026-08-28
- Korean attribution: 식품영양성분 데이터베이스
- English attribution: Korean Food Composition Database system(K-FCDB)
- Original SHA-256: `1EF3551F9A1D0EE87891D6306FA22BBD6A7FFCC90F2C70A4A184DBFE3FCE6EA6`
- Rows: 19,617
- Runtime use: none. `tools/import_kfind_foods.py` creates the local Android CSV assets.

The official 음식 DB does not contain a product barcode column. Barcode records are
therefore created only through the app's user-confirmed product registration flow.

## Processed-food products

`kfind-processed-food-db-2026-08-28.xlsx` is the unmodified official 가공식품 DB
downloaded from K-FIND on 2026-09-22.

- Provider: 식품의약품안전처
- Official download: https://various.foodsafetykorea.go.kr/nutrient/general/down/list.do
- Data version: 2026-08-28
- Original SHA-256: `B074D98E75D2D087DC1B193F0056AFFBF9AFD14524C9CCB556CB2B20978504F7`
- Source rows: 316,734
- Eligible rows after exact deduplication: 45,306
- Bundled product rows: 11,921 (category-capped mobile subset; package evidence,
  total content, maker and canonical product names receive priority)
- Generated asset: `app/src/main/assets/fooddata/product_items.csv`
- Generated asset SHA-256: `8984DC1C4CF881728D383EB4EAEA273B658E0886BD75A8DACB8CCAF8F8AD08B8`
- Generator: `tools/import_kfind_processed_foods.py`

The offline asset copies official product names, makers, nutrition bases, nutrients,
total weights and dates. It does not infer nutrients. A named package unit such as
`봉`, `병`, `팩`, or `조각` is emitted only when the official row provides that
evidence; otherwise the UI can say only `제품 전체` based on official total content.
Pizza/chicken pieces are never inferred from weight alone.

K-FIND requires the attribution `식품영양성분 데이터베이스` and
`Korean Food Composition Database system(K-FCDB)`, which the app shows with search
results. The related public-data API is free, allows unrestricted use, needs a service
key, has automatic development approval and a published 10,000-call development quota:
https://www.data.go.kr/data/15127578/openapi.do. Runtime API access is not used, so this
offline bundle requires no user key and has no per-user request cost.

The older 2022 processed-food file at
https://www.data.go.kr/data/15112364/fileData.do is not used because it is marked
KOGL Type 4 (attribution, non-commercial use, no derivatives), which is unsuitable for
commercial redistribution of a transformed offline app asset.
