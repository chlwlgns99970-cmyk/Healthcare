# 공식 브랜드 영양정보 보강 (2026-09-26 확인)

앱 번들 `franchise_official_items.csv`에는 아래 브랜드가 직접 공개한 값만 전사했다. 공식 표에 없는 탄수화물·지방 등은 빈 값(null)으로 유지했으며, 치킨 한 마리/조각 중량과 옵션 영양값은 계산하거나 추정하지 않았다.

- BBQ: https://dt.bbq.co.kr/menu/menuView.asp?midx=2247 등 공식 메뉴 상세의 `100g 당 함량` 표기. 4개 메뉴.
- 네네치킨: https://nenechicken.com/process/origin_detail.asp 의 공식 `영양성분표/중량`. 9개 메뉴·부위 유형. 공식 표가 제공하는 100g 기준만 사용.
- 메가MGC커피: https://mega-mgccoffee.com/menu/ 및 공식 `menu.php` 응답. HOT/ICE와 공식 컵 용량을 분리한 9개 항목.
- 매머드커피/매머드 익스프레스: https://www.mmthcoffee.com/sub/notice/view.html?noticeSeq=78 의 `매머드커피 익스프레스 디저트 영양성분표 (26.06.23 업데이트)`. 8개 디저트.

제외: 공식 영양표에 없는 신메뉴·옵션·세트, 공식 기준량이 불명확한 항목, 샷/시럽/휘핑 등 사용자 옵션.
