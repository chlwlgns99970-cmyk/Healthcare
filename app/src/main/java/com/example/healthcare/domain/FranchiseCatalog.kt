package com.example.healthcare.domain

import com.example.healthcare.data.entity.FoodItem

/**
 * 공식 브랜드 사이트에서 현재 운영을 확인한 프랜차이즈 탐색 레지스트리입니다.
 * 영양 수치가 없는 공식 메뉴는 별도 카탈로그로 탐색하고 이름·출처를 수동 기록에 전달합니다.
 */
data class FranchiseBrand(
    val name: String,
    val category: String,
    val officialUrl: String,
    val aliases: Set<String> = emptySet(),
    val machineReadableNutrition: Boolean = false,
    val officialMenuNames: List<String> = emptyList(),
    val representativeCategories: Set<String> = setOf(category)
)

/** Menu identity is independent of nutrition: missing values never become a zero-kcal FoodItem. */
data class FranchiseMenu(
    val id: String,
    val brand: String,
    val name: String,
    val sourceUrl: String,
    val verifiedAt: String,
    val sourceDate: String = "",
    val saleState: String = "SOURCE_DATE_UNKNOWN",
    val energyKcal: Double? = null,
    val servingAmount: Double? = null,
    val servingUnit: String? = null,
    val category: String = ""
) {
    val recordName: String get() = "$brand $name"
    val sourceDescription: String get() = if (saleState == "PUBLIC_TOURISM_STORE_MENU")
        "공공 관광 매장 메뉴 · 서울 신길로 39 · $brand · 확인 $verifiedAt · $sourceUrl"
        else if (saleState == "OFFICIAL_ORDER_BRAND_SALES_UNVERIFIED")
            "공식 주문 브랜드 메뉴 · 현재 판매 여부 미확인 · 원문 갱신 $sourceDate · 확인 $verifiedAt · $sourceUrl"
        else "공식 메뉴명 · $brand · 확인 $verifiedAt · $sourceUrl"
}

object FranchiseCatalog {
    val entries: List<FranchiseBrand> = listOf(
        FranchiseBrand("맥도날드", "햄버거", "https://www.mcdonalds.co.kr/", setOf("mcdonalds"), true),
        FranchiseBrand("롯데리아", "햄버거", "https://www.lotteeatz.com/", setOf("lotteria"), true),
        FranchiseBrand("버거킹", "햄버거", "https://www.burgerking.co.kr/", setOf("burgerking"), true),
        FranchiseBrand("KFC", "햄버거", "https://www.kfckorea.com/", setOf("kfc"), true,
            representativeCategories = setOf("치킨", "햄버거")),
        FranchiseBrand("맘스터치", "햄버거", "https://www.momstouch.co.kr/", setOf("momstouch"), true),
        FranchiseBrand("도미노피자", "피자", "https://www.dominos.co.kr/", setOf("dominos"), true),
        FranchiseBrand("피자헛", "피자", "https://www.pizzahut.co.kr/", setOf("pizzahut"), true),
        FranchiseBrand("미스터피자", "피자", "https://www.mrpizza.co.kr/", setOf("mrpizza"), true),
        FranchiseBrand("BHC", "치킨", "https://www.bhc.co.kr/", setOf("bhc"), true),
        FranchiseBrand("교촌치킨", "치킨", "https://www.kyochon.com/", setOf("kyochon"), true),
        FranchiseBrand("굽네치킨", "치킨", "https://www.goobne.co.kr/", setOf("goobne"), true),
        FranchiseBrand("BBQ", "치킨", "https://www.bbq.co.kr/", setOf("bbq", "비비큐"), true),
        FranchiseBrand("네네치킨", "치킨", "https://nenechicken.com/", setOf("nene", "네네"), true),
        FranchiseBrand("스타벅스", "카페", "https://www.starbucks.co.kr/", setOf("starbucks"), true),
        FranchiseBrand("투썸플레이스", "카페", "https://www.twosome.co.kr/", setOf("twosomeplace"), true),
        FranchiseBrand("컴포즈커피", "카페", "https://composecoffee.com/", setOf("composecoffee"), true),
        FranchiseBrand("이디야", "카페", "https://www.ediya.com/", setOf("ediya"), true),
        FranchiseBrand("메가MGC커피", "카페", "https://www.mega-mgccoffee.com/", setOf("megacoffee", "메가커피", "메가mgc"), true),
        FranchiseBrand("매머드커피", "카페", "https://mmthcoffee.com/", setOf("mammothcoffee", "메머드커피", "매머드익스프레스", "메머드익스프레스"), true),
        FranchiseBrand("두찜", "찜닭", "https://www.twozzim.com/"),
        FranchiseBrand("봉추찜닭", "찜닭", "http://www.bongchu.com/layout/res/home.php?go=page1.0&mid=10"),
        FranchiseBrand("일미리금계찜닭", "찜닭", "https://goldjjimdak.com/main.php"),
        FranchiseBrand("육수당", "국밥", "https://yuksudang.com/", setOf("순대국", "육개장")),
        FranchiseBrand("현대옥", "국밥", "http://www.hyundaiok.com/menu", setOf("콩나물국밥")),
        FranchiseBrand("이화수전통육개장", "국밥", "https://ihwasoo.com/", setOf("이화수", "육개장")),
        FranchiseBrand("신전떡볶이", "분식", "https://sinjeon.co.kr/", setOf("신전", "떡볶이")),
        FranchiseBrand("죠스떡볶이", "분식", "https://jawsfood.co.kr/menu/menu.html", setOf("죠스", "떡볶이")),
        FranchiseBrand("청년다방", "분식", "https://youngdabang.com/", setOf("떡볶이")),
        FranchiseBrand("두끼", "분식", "https://www.dookki.co.kr/", setOf("떡볶이")),
        FranchiseBrand("스쿨푸드", "분식", "http://schoolfood.freewebclub.com/menu/menu.html"),
        FranchiseBrand("김가네", "김밥", "https://www.gimgane.co.kr/"),
        FranchiseBrand("고봉민김밥인", "김밥", "https://www.kobongmin.com/renewal/main/main.php", setOf("고봉민")),
        FranchiseBrand("바르다김선생", "김밥", "https://www.teacherkim.co.kr/", setOf("김선생")),
        FranchiseBrand("홍콩반점0410", "중식", "https://www.theborn.co.kr/theborn_brand/홍콩반점0410/", setOf("홍콩반점", "짬뽕")),
        FranchiseBrand("이비가짬뽕", "중식", "https://www.ebiga.co.kr/", setOf("이비가", "짬뽕")),
        FranchiseBrand("탕화쿵푸", "마라탕", "https://tanghuokungfu.co.kr/default/brand/Introduction.php", setOf("마라탕")),
        FranchiseBrand("춘리마라탕", "마라탕", "https://chunlimalatang.com/menu/", setOf("마라탕")),
        FranchiseBrand("본죽", "죽", "https://www.bonif.co.kr/brand/store?brdCd=BF101"),
        FranchiseBrand("본죽&비빔밥", "죽·비빔밥", "https://www.bonif.co.kr/brand/menu?brdCd=BF102", setOf("본죽앤비빔밥", "본죽비빔밥"),
            representativeCategories = setOf("죽", "한식")),
        FranchiseBrand("죽이야기", "죽", "https://www.jukstory.com/"),
        FranchiseBrand("국수나무", "국수·우동", "https://www.namuya.co.kr/food/food.php", setOf("국수", "우동")),
        FranchiseBrand("역전우동0410", "국수·우동", "https://udon0410.com/menu/", setOf("역전우동", "우동")),
        FranchiseBrand("백소정", "돈가스", "https://www.baeksojeong.com/", setOf("돈가스", "돈까스", "카츠"),
            officialMenuNames = listOf("돈카츠", "마제소바+돈카츠", "냉소바+돈카츠", "스페셜가츠동", "모짜렐라치즈카츠")),
        FranchiseBrand("홍익돈까스", "돈가스", "https://www.hongikdonkatsu.com/", setOf("홍익돈가스", "돈가스")),
        FranchiseBrand("원할머니보쌈", "족발·보쌈", "https://wonandone.co.kr/bossam/", setOf("원할머니", "보쌈")),
        FranchiseBrand("놀부부대찌개", "부대찌개", "https://nolboo.co.kr/", setOf("놀부", "부대찌개")),
        FranchiseBrand("한솥", "도시락", "https://www.hsd.co.kr/menu/menu_list", setOf("한솥도시락"),
            officialMenuNames = listOf("치킨마요", "동백", "돈까스도련님")),
        FranchiseBrand("본도시락", "도시락", "https://www.bonif.co.kr/brand/menu?brdCd=BF104"),
        FranchiseBrand("본설렁탕", "국밥", "https://www.bonif.co.kr/brand/menu?brdCd=BF105"),
        FranchiseBrand("본우리반상", "한식", "https://www.bonif.co.kr/brand/menu?brdCd=BF107"),
        FranchiseBrand("멘지", "라멘", "https://www.bonif.co.kr/brand/menu?brdCd=BF111"),
        FranchiseBrand("본흑염소·능이삼계탕", "삼계탕", "https://www.bonif.co.kr/brand/menu?brdCd=BF113", setOf("본흑염소", "능이삼계탕")),
        FranchiseBrand("핵밥", "덮밥", "https://www.hecbob.com/main", setOf("덮밥")),
        FranchiseBrand("채선당", "샤브샤브", "https://www.chaesundang.co.kr/", setOf("샤브", "전골")),
        FranchiseBrand("소담촌", "샤브샤브", "https://www.sodamchon.com/", setOf("샤브", "전골")),
        FranchiseBrand("샐러디", "샐러드·포케", "https://salady.com/menu/content2", setOf("샐러드", "포케", "salady"), true),
        FranchiseBrand("샐러디&샌드위치", "샌드위치", "https://salady.com/menu2/list_1?menu2=1", setOf("샐러디앤샌드위치", "샐러디샌드위치"), true),
        FranchiseBrand("포케올데이", "샐러드·포케", "https://pokeallday.co.kr/", setOf("포케")),
        FranchiseBrand("슬로우캘리", "샐러드·포케", "https://slowcali.co.kr/", setOf("포케")),
        FranchiseBrand("써브웨이", "샌드위치", "https://www.subway.co.kr/", setOf("서브웨이", "subway", "샌드위치"), true),
        FranchiseBrand("이삭토스트", "토스트", "https://isaac-toast.co.kr/", setOf("이삭", "토스트"),
            officialMenuNames = listOf("햄치즈 토스트", "햄 스페셜 토스트", "베이컨 베스트 토스트", "그릴드 불갈비", "새우 스페셜 토스트")),
        FranchiseBrand("에그드랍", "토스트", "https://eggdrop.com/menu/list.php", setOf("eggdrop", "에그샌드위치", "토스트")),
        FranchiseBrand("캠토토스트", "토스트", "https://camtotoast.com/", setOf("캠토", "토스트"),
            officialMenuNames = listOf("골드피자 토스트", "콘베이컨 에그마요 토스트")),
        // BEGIN GENERATED DELIVERY CHAIN REGISTRY
        FranchiseBrand("피자스쿨", "피자", "http://pizzaschool.net/menu/", setOf("pizza school", "pizzaschool"), true),
        FranchiseBrand("파파존스", "피자", "https://m.pji.co.kr/menu/pizza", setOf("파파존스피자", "papa johns", "papajohns"), false),
        FranchiseBrand("청년피자", "피자", "https://youngmanpizza.co.kr/sub01/menu2.php", setOf("youngman pizza", "youngmanpizza"), true),
        FranchiseBrand("노랑통닭", "치킨", "https://www.norangtongdak.co.kr/menu/chicken_list.html", setOf("norang tongdak", "norangtongdak"), false),
        FranchiseBrand("지코바", "치킨", "http://www.gcova.co.kr/sub_3_1.htm", setOf("지코바치킨", "gcova"), true),
        FranchiseBrand("처갓집양념치킨", "치킨", "https://www.cheogajip.co.kr/bbs/board.php?bo_table=menu", setOf("처갓집", "처갓집치킨", "cheogajip"), false),
        FranchiseBrand("피자마루", "피자", "https://www.pizzamaru.kr/", setOf("pizzamaru"), false),
        FranchiseBrand("멕시카나", "치킨", "https://www.mexicana.co.kr/menu/product.asp", setOf("멕시카나치킨", "mexicana"), false),
        FranchiseBrand("60계치킨", "치킨", "https://60chicken.com/bbs/content.php?co_id=menu", setOf("60계", "육십계치킨", "60chicken"), false),
        FranchiseBrand("반올림피자", "피자", "https://order.banolimpizza.com/menu/list?menuId=1", setOf("반올림피자샵", "banolim pizza", "banolimpizza"), false),
        FranchiseBrand("프랭크버거", "햄버거", "https://www.frankburger.co.kr/html/menu_1.html", setOf("frankburger", "frank burger"), false),
        FranchiseBrand("노브랜드버거", "햄버거", "https://www.shinsegaefood.com/nobrandburger/index.sf", setOf("no brand burger", "nobrandburger", "NBB"), false),
        FranchiseBrand("새마을식당", "한식", "https://newmaul.com/sub/menu.php", setOf("newmaul"), false),
        FranchiseBrand("빽다방", "카페", "https://paikdabang.com/menu/menu_coffee/", setOf("paiks coffee", "paikdabang"), false),
        FranchiseBrand("배스킨라빈스", "아이스크림", "https://www.baskinrobbins.co.kr/menu/list.php?category=A", setOf("베스킨라빈스", "베라", "배라", "baskin robbins", "baskinrobbins"), true),
        FranchiseBrand("파리바게뜨", "베이커리", "https://www.paris.co.kr/products/", setOf("파리바게트", "파바", "paris baguette", "parisbaguette"), true),
        FranchiseBrand("요아정", "디저트", "https://yoajung.co.kr/bbs/content.php?co_id=menustore", setOf("요거트아이스크림의정석", "yoajung"), false),
        FranchiseBrand("미소야", "돈가스", "https://www.misoya.co.kr/menu", setOf("misoya"), false),
        FranchiseBrand("할리스", "카페", "https://www.hollys.co.kr/menu/espresso.do", setOf("할리스커피", "hollys", "hollys coffee"), false),
        FranchiseBrand("7번가피자", "피자", "https://www.7thpizza.com/sub/menu/list.php", setOf("세븐스트리트피자", "7th street pizza", "7thpizza"), false),
        FranchiseBrand("토마토도시락", "도시락", "https://www.tomatodosirak.co.kr/board/index.php?board=menu_01&sca=all", setOf("tomato dosirak", "tomatodosirak"), false),
        FranchiseBrand("동대문엽기떡볶이", "분식", "https://www.yupdduk.com/sub/menu/yup-menu", setOf("엽기떡볶이", "엽떡", "yupdduk"), false),
        FranchiseBrand("공차", "음료", "https://www.gong-cha.co.kr/brand/menu/product?category=001001&scroll=y", setOf("gong cha", "gongcha"), false),
        FranchiseBrand("던킨", "베이커리", "https://www.dunkindonuts.co.kr/menu/all", setOf("던킨도너츠", "던킨도넛", "dunkin", "dunkin donuts"), false),
        FranchiseBrand("족발야시장", "족발·보쌈", "https://xn--ih3bm7ju8bi1cb3a.com/html/menu.html", setOf(), false),
        FranchiseBrand("호식이두마리치킨", "치킨", "https://www.9922.co.kr/menu", setOf("호식이", "호식이치킨", "hosigi"), false),
        // END GENERATED DELIVERY CHAIN REGISTRY
    )

    val brands: List<String> = entries.map(FranchiseBrand::name)
    val categories: List<String> = entries.map(FranchiseBrand::category).distinct()
    /** Shared group labels; brand and individual-menu matching are deliberately separate. */
    val filterCategories: List<FranchiseCategoryFilter> = listOf(
        FranchiseCategoryFilter("치킨", setOf("치킨")),
        FranchiseCategoryFilter("피자", setOf("피자")),
        FranchiseCategoryFilter("햄버거", setOf("햄버거")),
        FranchiseCategoryFilter("분식", setOf("분식", "김밥")),
        FranchiseCategoryFilter("한식", setOf("한식", "덮밥", "돈가스", "국밥")),
        FranchiseCategoryFilter("중식", setOf("중식", "마라탕")),
        FranchiseCategoryFilter("일식", setOf("일식", "라멘")),
        FranchiseCategoryFilter("죽", setOf("죽")),
        FranchiseCategoryFilter("도시락", setOf("도시락")),
        FranchiseCategoryFilter("족발·보쌈", setOf("족발·보쌈")),
        FranchiseCategoryFilter("찜·탕", setOf("찜닭", "부대찌개", "삼계탕", "샤브샤브")),
        FranchiseCategoryFilter("샌드위치·토스트", setOf("샌드위치", "토스트")),
        FranchiseCategoryFilter("카페·음료", setOf("카페", "음료")),
        FranchiseCategoryFilter("베이커리", setOf("베이커리")),
        FranchiseCategoryFilter("디저트·아이스크림", setOf("디저트", "아이스크림")),
        FranchiseCategoryFilter("샐러드·포케", setOf("샐러드·포케")),
        FranchiseCategoryFilter("국수·우동", setOf("국수·우동", "파스타")),
        FranchiseCategoryFilter("사이드", setOf("사이드"))
    )
    fun categoriesForFilter(label: String): Set<String> =
        filterCategories.firstOrNull { it.label == label }?.menuCategories ?: setOf(label)
    fun matchesBrandFilter(brand: String, label: String): Boolean =
        entries.firstOrNull { it.name == brand }?.representativeCategories
            ?.any { it in categoriesForFilter(label) } == true
    val brandFilterCategories: List<FranchiseCategoryFilter>
        get() = filterCategories.filter { filter -> entries.any { matchesBrandFilter(it.name, filter.label) } }
    fun matchesFilter(food: FoodItem, label: String): Boolean =
        FoodMenuCategoryPolicy.categoryOf(food) in categoriesForFilter(label)
    fun matchesFilter(menu: FranchiseMenu, label: String): Boolean =
        FoodMenuCategoryPolicy.categoryOf(menu) in categoriesForFilter(label)
    private val normalizedBrands = entries.associateBy { FoodSearchPolicy.normalize(it.name) }
    private val genericBrandAliases = setOf("포케", "우동", "샌드위치", "토스트", "떡볶이", "보쌈", "육개장", "짬뽕",
        "마라탕", "돈가스", "돈까스", "카츠", "덮밥", "샐러드", "샤브", "샤브샤브", "전골", "국수", "순대국", "콩나물국밥",
        "부대찌개", "능이삼계탕", "에그샌드위치")
    private val aliases = entries.flatMap { brand ->
        brand.aliases.filterNot { FoodSearchPolicy.normalize(it) in genericBrandAliases }
            .map { FoodSearchPolicy.normalize(it) to brand.name }
    }.toMap()

    fun isFranchise(food: FoodItem): Boolean =
        food.sourceType in franchiseSourceTypes && food.brand in brands

    fun canonicalBrand(query: String): String? {
        val normalized = FoodSearchPolicy.normalize(query)
        return normalizedBrands[normalized]?.name ?: aliases[normalized]
    }

    private fun brandPrefix(query: String): Pair<String,String>? {
        val normalized = FoodSearchPolicy.normalize(query)
        val prefixes = normalizedBrands.keys.map { it to normalizedBrands.getValue(it).name } + aliases.entries.map { it.key to it.value }
        return prefixes.asSequence().filter { normalized.startsWith(it.first) }
            .maxByOrNull { it.first.length }?.let { it.second to normalized.removePrefix(it.first) }
    }
    fun canonicalBrandForQuery(query: String): String? = canonicalBrand(query) ?: brandPrefix(query)?.first
    fun canonicalSearchQuery(query: String): String? = brandPrefix(query)?.let { (brand, menu) ->
        FoodSearchPolicy.normalize(brand) + menu
    }
    fun menuQuery(brand: String, query: String): String {
        val prefix = brandPrefix(query)
        return if (prefix?.first == brand) prefix.second else FoodSearchPolicy.normalize(query)
    }

    fun categoryOf(brand: String): String? = entries.firstOrNull { it.name == brand }?.category

    fun officialMenus(brand: String, query: String = ""): List<FranchiseMenu> {
        val normalizedQuery = menuQuery(brand, query)
        val entry = entries.firstOrNull { it.name == brand } ?: return emptyList()
        val legacyReferences = entry.officialMenuNames.map { name ->
            FranchiseMenu("official-menu-${FoodSearchPolicy.normalize(brand)}-${FoodSearchPolicy.normalize(name)}",
                brand, name, entry.officialUrl, "2026-09-27")
        }
        return (OfficialFranchiseMenus.entries.filter { it.brand == brand } + legacyReferences)
            .filter { FoodMenuCategoryPolicy.isFoodMenuReference(it.name) }
            .distinctBy { menu ->
                val scope = menu.saleState.takeIf { it == "PUBLIC_TOURISM_STORE_MENU" ||
                    it == "OFFICIAL_ORDER_BRAND_SALES_UNVERIFIED" }.orEmpty()
                FoodSearchPolicy.normalize(menu.name) to scope
            }
            .filter { normalizedQuery.isBlank() || FoodSearchPolicy.normalize(it.name).contains(normalizedQuery) }
    }

    fun officialMenuNames(brand: String, query: String = ""): List<String> = officialMenus(brand, query).map(FranchiseMenu::name)

    fun menuCount(brand: String, nutritionMenuCount: Int): Int = nutritionMenuCount + officialMenus(brand).size

    fun matchesBrand(brand: String, query: String): Boolean {
        val normalizedQuery = FoodSearchPolicy.normalize(query)
        if (normalizedQuery.isBlank()) return true
        val entry = entries.firstOrNull { it.name == brand } ?: return false
        val searchable = listOf(entry.name, entry.category) + entry.aliases + officialMenuNames(brand)
        return searchable.any { FoodSearchPolicy.normalize(it).contains(normalizedQuery) } || canonicalBrand(query) == brand ||
            (canonicalBrandForQuery(query)==brand && officialMenus(brand,query).isNotEmpty())
    }

    private val franchiseSourceTypes = setOf("K-FIND", "OFFICIAL-BRAND-NUTRITION")
}

data class FranchiseCategoryFilter(val label: String, val menuCategories: Set<String>)
