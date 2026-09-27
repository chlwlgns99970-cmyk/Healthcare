# 추천 292개 원재료·알레르기 공식 출처 검증 (2026-09-27)

## 범위와 판정 기준

- 대상: `meal_templates.csv`의 stable template 292개 전부
- COMPLETE: 공식·공공·기존 검증 metadata로 주요 구성과 알레르기 판정 정보가 충분함
- PARTIAL: 공식 자료에서 주요 구성 일부만 확인됨
- UNKNOWN: 신뢰 가능한 구성 원재료 또는 알레르기 정보를 확보하지 못함
- 음식명, 열량, 비슷한 메뉴만으로 구성 원재료나 알레르기를 추정하지 않음
- UNKNOWN/PARTIAL은 추천을 막지 않고 화면에서 짧은 미확인 안내만 표시함

## 현재 프로젝트 원본 점검

K-FIND 원본 두 파일과 생성기를 대조했다.

- `data-source/kfind/kfind-food-db-2026-08-28.xlsx` (19,617행)
- `data-source/kfind/kfind-processed-food-db-2026-08-28.xlsx` (316,734행)
- 생성기에서 사용하는 공식 필드는 식품코드, 식품명, 분류, 영양 기준량, 영양성분, 중량, 업체/제조사, 데이터 기준일이다.
- 추천 template 292개는 모두 source food code에 연결되어 있지만, 런타임 K-FIND 자산에는 구성 원재료/알레르기 전용 필드가 없다.
- 따라서 연결된 음식명 하나를 구성 원재료 목록으로 간주하지 않았다.

## 공식 브랜드 페이지 확인

### 뚜레쥬르 통밀 크랜베리치킨 샌드위치

- stable ID: `kfind-dinner-convenience-1`
- 공식 페이지: https://www.tlj.co.kr/product/detail.asp?prod_num=3861
- 주요 구성: 통밀빵, 크랜베리 치킨, 오이
- 공식 알레르기: 달걀, 우유, 대두, 밀, 아황산류, 닭고기, 쇠고기
- 원재료 COMPLETE / 알레르기 COMPLETE

### 할리스 그릴드 치킨 샐러드

- stable ID: `kfind-catalog-d214-640000000-0002`
- 공식 페이지: https://m.hollys.co.kr/menu/menuView.do?idx=665&menuDiv=BAKERY
- 확인된 주요 구성: 그릴드 치킨, 오리엔탈소스
- 공식 알레르기: 밀, 우유, 대두, 달걀, 토마토, 닭고기
- 공식 설명의 `풍성한 재료`가 구체적으로 열거되지 않아 원재료 PARTIAL 유지
- 법정 알레르기 표시가 명시되어 알레르기 COMPLETE

## 식약처 표준레시피 대조

- 공식 서비스: 식품안전나라 `조리식품의 레시피 DB (COOKRCP01)`
- 안내: https://www.foodsafetykorea.go.kr/api/openApiInfo.do?menu_grp=MENU_GRP31&menu_no=661&svc_no=COOKRCP01
- 공개 필드에 메뉴명, 1인분 중량, 영양성분, 재료정보와 조리법이 있음을 확인했다.
- `tools/find_official_recipe_matches.ps1`는 추천의 연결 원본명과 공식 레시피명이 정규화 후 정확히 같은 경우만 후보로 남기도록 작성했다.
- 샘플키 호출 한도(`INFO-310`)에 도달해 1,156개 전체를 인증 없이 내려받을 수 없었으며, 우회하거나 새 인증키를 요구하지 않았다.
- 이번 작업에서 표준레시피를 근거로 앱 metadata에 추가 반영한 항목은 0개다.

## 탄단지 31개

- kcal 292/292, 탄수화물 261/292, 단백질 292/292, 지방 262/292, 네 값 완전 261/292를 재확인했다.
- 공식 페이지가 당류·단백질·포화지방만 제공한 메뉴에서 탄수화물/총지방을 역산하지 않았다.
- 새로 보강한 값 0개, 기존 미확인 31개를 유지했다.

## 결과

- 원재료: COMPLETE 36 / PARTIAL 1 / UNKNOWN 255
- 알레르기: COMPLETE 37 / PARTIAL 0 / UNKNOWN 255
- 공식 페이지로 구조화한 메뉴: 2개
- 근거 없는 원재료·알레르기·탄단지 생성: 0개
- 상세 행별 상태와 source identifier는 `recommendation-292-audit.csv`에 기록했다.
