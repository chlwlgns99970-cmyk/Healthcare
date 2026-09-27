package com.example.healthcare.domain

import com.example.healthcare.data.entity.FoodItem

/**
 * 공식 브랜드 사이트에서 현재 운영을 확인한 프랜차이즈 탐색 레지스트리입니다.
 * 영양 수치가 없는 브랜드는 FoodItem을 만들지 않으며, 0개 메뉴 상태로만 노출합니다.
 */
data class FranchiseBrand(
    val name: String,
    val category: String,
    val officialUrl: String,
    val aliases: Set<String> = emptySet(),
    val machineReadableNutrition: Boolean = false,
    val officialMenuNames: List<String> = emptyList()
)

object FranchiseCatalog {
    val entries: List<FranchiseBrand> = listOf(
        FranchiseBrand("맥도날드", "햄버거", "https://www.mcdonalds.co.kr/", setOf("mcdonalds"), true),
        FranchiseBrand("롯데리아", "햄버거", "https://www.lotteeatz.com/", setOf("lotteria"), true),
        FranchiseBrand("버거킹", "햄버거", "https://www.burgerking.co.kr/", setOf("burgerking"), true),
        FranchiseBrand("KFC", "햄버거", "https://www.kfckorea.com/", setOf("kfc"), true),
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
        FranchiseBrand("봉추찜닭", "찜닭", "https://www.bongchu.com/"),
        FranchiseBrand("일미리금계찜닭", "찜닭", "https://goldjjimdak.com/main.php"),
        FranchiseBrand("육수당", "국밥", "https://yuksudang.com/", setOf("순대국", "육개장")),
        FranchiseBrand("현대옥", "국밥", "https://hyundaiok.com/", setOf("콩나물국밥")),
        FranchiseBrand("이화수전통육개장", "국밥", "https://ihwasoo.com/", setOf("이화수", "육개장")),
        FranchiseBrand("신전떡볶이", "분식", "https://sinjeon.co.kr/", setOf("신전", "떡볶이")),
        FranchiseBrand("죠스떡볶이", "분식", "https://www.jawsfood.com/", setOf("죠스", "떡볶이")),
        FranchiseBrand("청년다방", "분식", "https://youngdabang.com/", setOf("떡볶이")),
        FranchiseBrand("두끼", "분식", "https://www.dookki.co.kr/", setOf("떡볶이")),
        FranchiseBrand("스쿨푸드", "분식", "https://www.schoolfood.co.kr/"),
        FranchiseBrand("김가네", "김밥", "https://www.gimgane.co.kr/"),
        FranchiseBrand("고봉민김밥인", "김밥", "https://www.kobongmin.com/renewal/main/main.php", setOf("고봉민")),
        FranchiseBrand("바르다김선생", "김밥", "https://www.teacherkim.co.kr/", setOf("김선생")),
        FranchiseBrand("홍콩반점0410", "중식", "https://www.theborn.co.kr/theborn_brand/홍콩반점0410/", setOf("홍콩반점", "짬뽕")),
        FranchiseBrand("이비가짬뽕", "중식", "https://www.ebiga.co.kr/", setOf("이비가", "짬뽕")),
        FranchiseBrand("탕화쿵푸", "마라탕", "https://tanghuokungfu.co.kr/default/brand/Introduction.php", setOf("마라탕")),
        FranchiseBrand("춘리마라탕", "마라탕", "https://chunlimala.com/", setOf("마라탕")),
        FranchiseBrand("본죽", "죽", "https://www.bonif.co.kr/brand/store?brdCd=BF101"),
        FranchiseBrand("죽이야기", "죽", "https://www.jukstory.com/"),
        FranchiseBrand("국수나무", "국수·우동", "https://www.noodletree.co.kr/", setOf("국수", "우동")),
        FranchiseBrand("역전우동0410", "국수·우동", "https://www.theborn.co.kr/theborn_brand/역전우동0410/", setOf("역전우동", "우동")),
        FranchiseBrand("백소정", "돈가스", "https://www.baeksojeong.com/", setOf("돈가스", "돈까스", "카츠"),
            officialMenuNames = listOf("돈카츠", "마제소바+돈카츠", "냉소바+돈카츠", "스페셜가츠동", "모짜렐라치즈카츠")),
        FranchiseBrand("홍익돈까스", "돈가스", "https://www.hongikdonkatsu.com/", setOf("홍익돈가스", "돈가스")),
        FranchiseBrand("원할머니보쌈", "족발·보쌈", "https://wonandone.co.kr/bossam/", setOf("원할머니", "보쌈")),
        FranchiseBrand("놀부부대찌개", "부대찌개", "https://nolboo.co.kr/", setOf("놀부", "부대찌개")),
        FranchiseBrand("한솥", "도시락", "https://www.hsd.co.kr/menu/menu_list", setOf("한솥도시락"),
            officialMenuNames = listOf("치킨마요", "동백", "돈까스도련님")),
        FranchiseBrand("본도시락", "도시락", "https://www.bonif.co.kr/brand/store?brdCd=BF102"),
        FranchiseBrand("핵밥", "덮밥", "https://hecbob.com/storelocation", setOf("덮밥")),
        FranchiseBrand("채선당", "샤브샤브", "https://www.chaesundang.co.kr/", setOf("샤브", "전골")),
        FranchiseBrand("소담촌", "샤브샤브", "https://www.sodamchon.com/", setOf("샤브", "전골")),
        FranchiseBrand("샐러디", "샐러드·포케", "https://salady.com/?language=kor", setOf("샐러드", "포케")),
        FranchiseBrand("포케올데이", "샐러드·포케", "https://pokeallday.co.kr/", setOf("포케")),
        FranchiseBrand("슬로우캘리", "샐러드·포케", "https://slowcali.co.kr/", setOf("포케")),
        FranchiseBrand("써브웨이", "샌드위치", "https://www.subway.co.kr/", setOf("서브웨이", "subway", "샌드위치"), true),
        FranchiseBrand("이삭토스트", "토스트", "https://isaac-toast.co.kr/", setOf("이삭", "토스트"),
            officialMenuNames = listOf("햄치즈 토스트", "햄 스페셜 토스트", "베이컨 베스트 토스트", "그릴드 불갈비", "새우 스페셜 토스트")),
        FranchiseBrand("에그드랍", "토스트", "https://www.eggdrop.co.kr/", setOf("eggdrop", "에그샌드위치", "토스트")),
        FranchiseBrand("캠토토스트", "토스트", "https://camtotoast.com/", setOf("캠토", "토스트"),
            officialMenuNames = listOf("골드피자 토스트", "콘베이컨 에그마요 토스트"))
    )

    val brands: List<String> = entries.map(FranchiseBrand::name)
    val categories: List<String> = entries.map(FranchiseBrand::category).distinct()
    private val normalizedBrands = entries.associateBy { FoodSearchPolicy.normalize(it.name) }
    private val aliases = entries.flatMap { brand -> brand.aliases.map { FoodSearchPolicy.normalize(it) to brand.name } }.toMap()

    fun isFranchise(food: FoodItem): Boolean =
        food.sourceType in franchiseSourceTypes && food.brand in brands

    fun canonicalBrand(query: String): String? {
        val normalized = FoodSearchPolicy.normalize(query)
        return normalizedBrands[normalized]?.name ?: aliases[normalized]
    }

    fun categoryOf(brand: String): String? = entries.firstOrNull { it.name == brand }?.category

    fun officialMenuNames(brand: String, query: String = ""): List<String> {
        val normalizedQuery = FoodSearchPolicy.normalize(query)
        return entries.firstOrNull { it.name == brand }?.officialMenuNames.orEmpty().filter {
            normalizedQuery.isBlank() || FoodSearchPolicy.normalize(it).contains(normalizedQuery)
        }
    }

    fun matchesBrand(brand: String, query: String): Boolean {
        val normalizedQuery = FoodSearchPolicy.normalize(query)
        if (normalizedQuery.isBlank()) return true
        val entry = entries.firstOrNull { it.name == brand } ?: return false
        val searchable = listOf(entry.name, entry.category) + entry.aliases + entry.officialMenuNames
        return searchable.any { FoodSearchPolicy.normalize(it).contains(normalizedQuery) } || canonicalBrand(query) == brand
    }

    private val franchiseSourceTypes = setOf("K-FIND", "OFFICIAL-BRAND-NUTRITION")
}
