# 고해상도 앱 사진 출처

기준일: 2026-09-26

아래 파일은 저해상도 시안의 확대본이 아니라 Pexels에서 직접 받은 원본 사진이다.
각 페이지에는 `Free to use` 또는 `License: Free`가 표시된다. 앱에는 원본 비율을 유지한
`drawable-nodpi` JPEG를 사용하고, Compose에서는 확대 보간 없이 `ContentScale.Crop`으로
필요한 영역만 자른다.

| 앱 파일 | 원본 크기 | Pexels 원본 페이지 |
|---|---:|---|
| photo_onboarding_arches.jpg | 3836×5754 | https://www.pexels.com/photo/tropical-interior-design-with-banana-plants-35377225/ |
| photo_breakfast_yogurt_bowl.jpg | 3067×2537 | https://www.pexels.com/photo/healthy-breakfast-bowl-with-oatmeal-and-fruits-4220141/ |
| photo_sandwich.jpg | 4000×6000 | https://www.pexels.com/photo/healthy-sandwiches-on-a-plate-7936685/ |
| photo_salmon_avocado_salad.jpg | 3047×5090 | https://www.pexels.com/photo/baked-salmon-with-vegetables-15782638/ |
| photo_walking_park.jpg | 4000×6000 | https://www.pexels.com/photo/back-view-of-a-man-walking-at-the-park-10135437/ |
| photo_fried_rice.jpg | 5328×4000 | https://www.pexels.com/photo/gourmet-fried-rice-in-artisan-bowl-35479259/ |
| photo_sushi.jpg | 3255×4890 | https://www.pexels.com/photo/food-japanese-food-photography-sushi-9210/ |
| photo_curry_rice.jpg | 4000×6000 | https://www.pexels.com/photo/indonesian-meal-with-rice-and-curry-36363496/ |
| photo_food_journal.jpg | 3607×6418 | https://www.pexels.com/photo/stylish-breakfast-bowl-with-blueberries-and-diary-34005601/ |
| photo_chicken_sandwich.jpg | 원본 고해상도, 앱 자산 2048×1152 | https://www.pexels.com/photo/delicious-chicken-sandwich-with-fresh-toppings-36879173/ |
| photo_udon_soup.jpg | 원본 고해상도, 앱 자산 2048×1365 | https://www.pexels.com/photo/bowl-of-udon-noodle-soup-with-tofu-and-vegetables-37683010/ |

추천 메뉴 매핑은 안정적인 meal template ID를 우선 사용한다. 정확한 메뉴 또는 동일 식사
형태를 확인할 수 없는 경우 사진을 억지로 재사용하지 않고 중립 placeholder를 표시한다.
