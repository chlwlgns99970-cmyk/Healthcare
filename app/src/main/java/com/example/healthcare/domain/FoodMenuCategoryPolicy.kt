package com.example.healthcare.domain

import com.example.healthcare.data.entity.FoodItem

/** Classifies the food itself. Brand industry and search aliases are never ingredient/category evidence. */
object FoodMenuCategoryPolicy {
    private fun contains(value: String, vararg terms: String) = terms.any(value::contains)
    private fun source(food: FoodItem): String = FoodMetadataPolicy.lookup(food.id)?.rawClassification
        ?.takeIf(String::isNotBlank) ?: food.category.orEmpty().takeIf {
            !FranchiseCatalog.isFranchise(food) || (food.sourceType == "OFFICIAL-BRAND-NUTRITION" &&
                FoodSearchPolicy.normalize(it) in setOf("sandwich","salad","grainsalad","morning","랩","샐러드","곡물볼","누들볼","프로틴박스","치킨류"))
        }.orEmpty()
    private fun linkedMenuCategory(reference: String): String? {
        // These reviewed official routes identify the individual dish group; a
        // generic brand home/menu URL supplies no classification.
        val route = Regex("https?://(?:www\\.)?subway\\.co\\.kr/menuview/(sandwich|salad|grain_salad|wrap|morning)(?:[?/#|]|$)", RegexOption.IGNORE_CASE)
            .find(reference)?.groupValues?.get(1)?.lowercase() ?: return null
        return if (route in setOf("salad","grain_salad")) "샐러드·포케" else "샌드위치"
    }
    private fun name(food: FoodItem): String {
        val normalized=FoodSearchPolicy.normalize(food.name)
        val brand=FoodSearchPolicy.normalize(food.brand.orEmpty())
        return if (brand.isNotBlank()) normalized.removePrefix(brand).removeSuffix(brand) else normalized
    }
    fun categoryOf(food: FoodItem): String? = ReviewedMenuCategorySources.categoryOf(food.id) ?: FoodMetadataPolicy.lookup(food.id)?.let { metadata ->
        metadata.menuCategory.takeIf(String::isNotBlank) ?: linkedMenuCategory(metadata.sourceReference)
    } ?: classify(name(food),source(food))
    fun categoryOf(menu: FranchiseMenu): String? = ReviewedMenuCategorySources.categoryOf(menu.id) ?: menu.category.takeIf(String::isNotBlank)
        ?: FoodMetadataPolicy.lookup(menu.id)?.menuCategory?.takeIf(String::isNotBlank)
        ?: linkedMenuCategory(menu.sourceUrl)
        ?: classify(FoodSearchPolicy.normalize(menu.name), FoodMetadataPolicy.lookup(menu.id)?.rawClassification.orEmpty())
    fun matches(food: FoodItem, category: String): Boolean = categoryMatches(categoryOf(food),category,name(food))
    fun matches(menu: FranchiseMenu, category: String): Boolean = categoryMatches(categoryOf(menu),category,FoodSearchPolicy.normalize(menu.name))
    private fun categoryMatches(actual: String?, selected: String, name: String): Boolean = when (selected) {
        "한식·분식" -> actual in setOf("한식","분식","김밥","덮밥","국밥","죽","찜닭","부대찌개","삼계탕")
        "분식" -> actual in setOf("분식","김밥")
        "떡볶이" -> actual == "분식" && contains(name,"떡볶이","라볶이")
        "죽·비빔밥" -> actual == "죽" || (actual == "덮밥" && contains(name,"비빔밥"))
        "중식" -> actual in setOf("중식","마라탕")
        "국수·우동" -> actual in setOf("국수·우동","라멘")
        "샐러드·포케" -> actual=="샐러드·포케"
        else -> actual==selected
    }
    fun isFoodMenuReference(name: String): Boolean {
        val n = FoodSearchPolicy.normalize(name)
        return n !in setOf("신선합니다","맛있습니다","건강합니다","메뉴안내","메뉴소개","자세히보기") &&
            !listOf("가맹문의","창업문의","전화문의","대표전화","고객센터").any(n::startsWith)
    }
    private fun classify(rawName: String, rawSource: String): String? {
        val n=FoodSearchPolicy.normalize(rawName);val s=FoodSearchPolicy.normalize(rawSource)
        // The source's individual representative dish outranks a topping's name (e.g. cheeseburger pizza).
        if (contains(s,"피자") && !contains(s,"피자소스","피자치즈","피자도우")) return "피자"
        if (s in setOf("sandwich","morning","랩") || contains(s,"샌드위치")) return "샌드위치"
        if (s in setOf("salad","grainsalad","샐러드","곡물볼","프로틴박스")) return "샐러드·포케"
        if (s=="누들볼") return "국수·우동"
        if (listOf("소스","드레싱","케첩","케찹","시즈닝","치킨무","피클","토핑","사리","잼").any(n::endsWith) ||
            contains(n,"소스추가","라면사리","당면사리") || contains(s,"소스류","드레싱류")) return "사이드"
        // Recognizable menu forms precede toppings, flavors and the brand's industry.
        if (contains(n,"토스트")) return "토스트"
        if (contains(n,"아이스크림","빙과","아이스바","아이스밀크","모나카") || contains(s,"아이스크림","빙과")) return "아이스크림"
        if (contains(n,"케이크","케익","도넛","와플","마카롱","쿠키","푸딩","초콜릿","젤리","아이스샌드","브라우니","츄러스","약과","꽈배기","빙수","샤베트","아이스슈")) return "디저트"
        if (contains(n,"빵","브레드","브래드","크루아상","크로와상","크라상","브리오슈","베이글","머핀","스콘","페이스트리")) return "베이커리"
        if (contains(n,"샌드위치","샌드윗","샌드위치","햄버거","버거","핫도그")) {
            return if (contains(n,"버거","핫도그")) "햄버거" else "샌드위치"
        }
        if (contains(n,"스파게티","파스타","까르보나라","알리오올리오","뇨끼")) return "파스타"
        if (contains(n,"라멘","탄탄멘") || contains(s,"라멘")) return "라멘"
        if (contains(n,"우동","국수","국시","냉면","소바","모밀","메밀면","냉메밀","비빔면","라면","수제비","짜장면","자장면","짬뽕","쫄면","쌀국수","초계면","냉멘","팟타이"))
            return if (contains(n,"짜장면","자장면","짬뽕")) "중식" else "국수·우동"
        if (contains(n,"마라탕") || contains(s,"마라탕")) return "마라탕"
        if (contains(n,"찜닭")) return "찜닭"
        if (contains(n,"국밥","해장국","설렁탕","곰탕","순댓국","순대국","육개장")) return "국밥"
        if (contains(n,"부대찌개")) return "부대찌개"
        if (contains(n,"삼계탕","반계탕","백숙")) return "삼계탕"
        if (contains(n,"김밥") || contains(s,"김밥")) return "김밥"
        if (contains(n,"떡볶이","라볶이","주먹밥","김말이","순대","만두","교자","어묵","오뎅","소떡소떡","떡꼬치")) return "분식"
        if (contains(n,"덮밥","볶음밥","비빔밥","치킨마요","참치마요","오므라이스","솥밥","쌈밥","알밥","잡채밥","컵밥","필라프","리조또","리소토","라이스")) return "덮밥"
        if (contains(n,"도시락","정식","반상","한상","백반")) return "도시락"
        if (contains(n,"샐러드","샐러디","포케")) return "샐러드·포케"
        if (n.endsWith("랩") || n.endsWith("브리또")) return "샌드위치"
        if (contains(n,"피자") && (!contains(n,"피자도우","피자소스","피자치즈","피자맛") || contains(s,"피자"))) return "피자"
        if (contains(n,"아메리카노","라떼","커피","콜드브루","에스프레소","카푸치노") && !contains(n,"빵","케이크","과자","초콜릿")) return "카페"
        if (contains(n,"콜라","사이다","스프라이트","에이드","주스","쥬스","스무디","음료","생수","밀크티","소다","식혜","콤부차","쉐이크","즙","매실골드") ||
            contains(s,"음료류","음료및차류","탄산음료","과채주스")) return "음료"
        if (contains(n,"감자튀김","후렌치후라이","프렌치프라이","치즈볼","치즈스틱","치킨무","피클","디핑","시즈닝","도우","튀김옷","웨지감자","웨지포테이토","해쉬브라운","해시브라운","해쉬포테이토","어니언링","어니언후레이크","콘너겟")) return "사이드"
        if (contains(n,"국밥","해장국","설렁탕","곰탕","순댓국","순대국")) return "국밥"
        if (contains(n,"부대찌개")) return "부대찌개"
        if (contains(n,"삼계탕","백숙")) return "삼계탕"
        if (contains(n,"전골","샤브샤브","샤브")) return "샤브샤브"
        if (contains(n,"죽") && !contains(n,"반죽")) return "죽"
        if (contains(n,"족발","보쌈")) return "족발·보쌈"
        if (contains(n,"돈까스","돈가스","돈카츠","카츠","까스")) return "돈가스"
        if (contains(n,"초밥","스시","사시미","규동","가츠동","오코노미야키","타코야끼","타코야키")) return "일식"
        if (contains(n,"탕수육","꿔바로우","멘보샤","마파두부","깐풍","짜장","자장","마라샹궈","유린기")) return "중식"
        if (contains(n,"치킨","통닭","닭강정","닭깡정","닭다리","닭날개","닭봉","닭튀김","닭껍질튀김","닭껍질","윙","후라이드","가라아게","너겟","안심텐더","텐더킹") || contains(s,"치킨메뉴","치킨류","닭튀김")) return "치킨"
        if (contains(n,"튀김","새우링","오징어링","통새우링","그라탕","포테이토","콘치즈","나쵸","나초","감자스틱","고구마스틱","고로케","크로켓")) return "사이드"
        if (contains(n,"장조림","초무침","닭볶음탕","닭매운탕","파전","감자전","김치전","녹두전","깻잎전","메밀전병")) return "한식"
        if (contains(n,"계란밥","치즈밥","명란밥","영양밥","미니밥","진밥")) return "덮밥"
        if (contains(n,"우유") && !contains(n,"크림","빵","쿠키","케이크")) return "음료"
        if (contains(n,"불고기","제육","수육","삼겹살","목살","갈비구이","육전","찌개","닭발","오리주물럭","육회","감자탕","흑염소탕","김치찜") ||
            n.endsWith("공기밥") || n.endsWith("흑미밥")) return "한식"
        if (contains(s,"피자")) return "피자"
        if (contains(s,"샌드위치")) return "샌드위치"
        if (contains(s,"햄버거")) return "햄버거"
        if (s in setOf("치킨","한식","분식","덮밥","도시락","죽","중식","일식","국밥","삼계탕","부대찌개","샤브샤브","족발보쌈","돈가스","카페","음료","디저트","아이스크림"))
            return if (s=="족발보쌈") "족발·보쌈" else rawSource
        if (contains(s,"샐러드","포케")) return "샐러드·포케"
        if (contains(s,"토스트")) return "토스트"
        if (contains(s,"베이커리","빵류","빵및과자류")) return "베이커리"
        if (contains(s,"사이드")) return "사이드"
        return null
    }
    fun matchesBrowse(food: FoodItem, selected: FoodBrowseCategory): Boolean {
        if (selected==FoodBrowseCategory.ALL) return true
        val n=name(food);val s=FoodSearchPolicy.normalize(source(food));val kind=categoryOf(food)
        return when (selected) {
            FoodBrowseCategory.ALL -> true
            FoodBrowseCategory.RICE_NOODLE -> kind in setOf("덮밥","국수·우동","라멘","파스타","김밥","도시락") || contains(s,"밥류","면류","면및만두류") ||
                (kind in setOf("분식","중식") && contains(n,"면","김밥","주먹밥","떡볶이","만두"))
            FoodBrowseCategory.SOUP_STEW -> kind in setOf("국밥","부대찌개","삼계탕","샤브샤브","죽","마라탕") || contains(s,"국및탕류","찌개및전골류","죽및스프류") ||
                contains(n,"수프","찌개","어묵탕","오뎅탕") || n.endsWith("스프")
            FoodBrowseCategory.MEAT -> kind in setOf("치킨","족발·보쌈","돈가스") || contains(s,"육류","식육가공","햄류","소시지류","베이컨류") ||
                (kind==null && contains(n,"고기","갈비","삼겹살","닭","오리","스테이크","불고기"))
            FoodBrowseCategory.FRUIT -> contains(s,"과실류","과일류") && !contains(s,"음료","주스","가공품")
            FoodBrowseCategory.VEGETABLE -> contains(s,"채소류","야채류") && !contains(s,"음료","주스")
            FoodBrowseCategory.SNACK -> kind in setOf("아이스크림","디저트","베이커리","토스트","샌드위치") || contains(s,"과자류","과자및빵류","떡류","시리얼","초콜릿","사탕류")
            FoodBrowseCategory.BEVERAGE -> kind in setOf("음료","카페") ||
                (kind !in setOf("아이스크림","디저트","베이커리","샌드위치") && contains(s,"우유류","두유류","유가공","발효유류"))
        }
    }
}
