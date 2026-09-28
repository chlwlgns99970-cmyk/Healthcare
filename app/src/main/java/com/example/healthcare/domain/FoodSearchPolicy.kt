package com.example.healthcare.domain

import com.example.healthcare.data.entity.FoodItem
import java.text.Normalizer
import java.util.Locale
import kotlin.math.round

enum class FoodBrowseCategory(val label: String, val seedQuery: String) {
    ALL("전체", ""),
    RICE_NOODLE("밥·면", "밥"),
    SOUP_STEW("국·찌개", "국"),
    MEAT("고기", "고기"),
    FRUIT("과일", "과일"),
    VEGETABLE("채소·야채", "채소"),
    SNACK("간식·과자", "과자"),
    BEVERAGE("음료", "음료")
}

/** Search-only aliases. Source names and nutrition units remain untouched. */
object FoodSearchPolicy {
    private val nonNameCharacters = Regex("[^0-9a-z가-힣]")
    private val alternateNames = mapOf(
        "참치김밥" to "김밥참치",
        "계란" to "달걀",
        "달걀" to "계란",
        "흰밥" to "쌀밥",
        "흰우유" to "우유",
        "과일류" to "과일",
        "야채" to "채소",
        "채소류" to "채소",
        "간식" to "과자",
        "mcdonalds" to "맥도날드",
        "lotteria" to "롯데리아",
        "burgerking" to "버거킹",
        "momstouch" to "맘스터치",
        "dominos" to "도미노피자",
        "pizzahut" to "피자헛",
        "mrpizza" to "미스터피자",
        "starbucks" to "스타벅스",
        "twosomeplace" to "투썸플레이스",
        "composecoffee" to "컴포즈커피",
        "ediya" to "이디야",
        "kyochon" to "교촌치킨",
        "goobne" to "굽네치킨"
    )
    private val fluidOrMixedCategories = setOf(
        "음료 및 차류", "국 및 탕류", "찌개 및 전골류", "죽 및 스프류",
        "장류, 양념류", "유제품류 및 빙과류"
    )
    private val broaderSearchTerms = mapOf(
        "참치김밥" to "김밥",
        "흰밥" to "밥",
        "닭가슴살" to "닭"
    )
    private val detailFirstCategories = setOf("국 및 탕류", "찌개 및 전골류", "죽 및 스프류")
    private val detailFirstDishSuffixes = listOf("찌개", "전골", "스프", "수프", "국", "탕", "죽")
    private val simpleKoreanDetail = Regex("^[가-힣]{1,8}$")

    fun normalize(query: String): String = nonNameCharacters.replace(
        Normalizer.normalize(query, Normalizer.Form.NFKC).lowercase(Locale.ROOT), ""
    )

    fun queries(query: String): List<String> {
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptyList()
        return listOfNotNull(normalized, alternateNames[normalized]).distinct()
    }

    fun broaderSuggestion(query: String): String? = broaderSearchTerms[normalize(query)]

    fun displayName(food: FoodItem): String {
        val parts = food.name.split('_').map(String::trim).filter(String::isNotBlank)
        if (parts.size == 2 && shouldPlaceDetailFirst(food, parts[0], parts[1])) {
            return parts[1] + parts[0]
        }
        return parts.joinToString(" · ").ifBlank { food.name }
    }

    /** K-FIND's food-code prefix identifies the official row's origin. */
    fun sourceVariantLabel(food: FoodItem): String? {
        if (!food.sourceType.equals("K-FIND", ignoreCase = true)) return null
        return when (food.sourceFoodCode.take(2).uppercase(Locale.ROOT)) {
            "D1" -> "가정식 · 분석값"
            "D2" -> "외식업체 제공 영양정보"
            "D3" -> "외식 · 분석값"
            "D4" -> "외식 · 재료량 기반 산출"
            "D5" -> "초등학교 급식 · 재료량 기반 산출"
            "D6" -> "중·고등학교 급식 · 재료량 기반 산출"
            "D7" -> "산업체 급식 · 재료량 기반 산출"
            else -> null
        }
    }

    fun isOfficialKfind(food: FoodItem): Boolean = food.sourceType.startsWith("K-FIND", ignoreCase = true)

    fun isProduct(food: FoodItem): Boolean = food.sourceType.equals("K-FIND-PRODUCT", ignoreCase = true)

    fun matchesCategory(food: FoodItem, category: FoodBrowseCategory): Boolean {
        if (category == FoodBrowseCategory.ALL) return true
        val searchable = normalize(
            listOf(food.name, food.category.orEmpty(), food.aliases).joinToString("|")
        )
        val terms = when (category) {
            FoodBrowseCategory.ALL -> emptyList()
            FoodBrowseCategory.RICE_NOODLE -> listOf("밥", "면", "국수", "파스타", "만두")
            FoodBrowseCategory.SOUP_STEW -> listOf("국", "탕", "찌개", "전골", "죽", "스프", "수프")
            FoodBrowseCategory.MEAT -> listOf("고기", "육류", "소고기", "쇠고기", "돼지고기", "닭", "오리")
            FoodBrowseCategory.FRUIT -> listOf("과일", "사과", "바나나", "딸기", "포도", "오렌지", "수박", "복숭아", "배")
            FoodBrowseCategory.VEGETABLE -> listOf("채소", "야채", "양배추", "상추", "토마토", "오이")
            FoodBrowseCategory.SNACK -> listOf("간식", "과자", "스낵", "쿠키", "초콜릿", "빙과")
            FoodBrowseCategory.BEVERAGE -> listOf("음료", "차류", "커피", "주스", "우유", "탄산")
        }
        return terms.any { searchable.contains(normalize(it)) }
    }

    /**
     * 검색 소스가 합쳐질 때 생기는 실질적으로 동일한 행만 접는다.
     * 브랜드·출처 종류·기준량·단위·영양값 중 하나라도 다르면 별도 결과로 보존한다.
     */
    fun deduplicationKey(food: FoodItem): String = listOf(
        normalize(displayName(food)),
        normalize(food.brand.orEmpty()),
        food.sourceType.uppercase(Locale.ROOT),
        decimalKey(food.referenceAmount),
        food.unit.lowercase(Locale.ROOT),
        decimalKey(food.energyKcal),
        decimalKey(food.carbohydrateGrams),
        decimalKey(food.proteinGrams),
        decimalKey(food.fatGrams)
    ).joinToString("|")

    fun resultGroup(food: FoodItem): String = when {
        isProduct(food) -> "제품"
        FranchiseCatalog.isFranchise(food) -> "프랜차이즈"
        else -> "기본·종류"
    }

    fun searchRank(food: FoodItem, query: String): Int {
        val normalized = normalize(query)
        val name = food.normalizedName
        val brand = normalize(food.brand.orEmpty())
        val productOffset = if (isProduct(food) || FranchiseCatalog.isFranchise(food)) 0 else 3
        return productOffset + when {
            name == normalized -> 0
            name.startsWith(normalized) -> 1
            name.contains(normalized) || brand.contains(normalized) || food.aliases.contains(normalized) -> 2
            else -> 3
        }
    }

    fun quickCompanionQueries(food: FoodItem?): List<Pair<String, String>> = when {
        food == null -> emptyList()
        food.name.contains("라면") -> listOf("밥 추가" to "쌀밥", "달걀 추가" to "달걀")
        food.name.contains("햄버거") || food.name.contains("버거") ->
            listOf("감자튀김 추가" to "감자튀김", "음료 추가" to "탄산음료")
        food.name.contains("피자") ->
            listOf("음료 추가" to "탄산음료", "샐러드 추가" to "샐러드")
        else -> emptyList()
    }

    /** Volume for a solid category is not a verified user portion or a gram serving. */
    fun needsBasisReview(food: FoodItem): Boolean =
        food.sourceType.equals("K-FIND", ignoreCase = true) &&
            food.unit.equals("ml", ignoreCase = true) &&
            food.category !in fluidOrMixedCategories

    private fun shouldPlaceDetailFirst(food: FoodItem, base: String, detail: String): Boolean =
        food.sourceType.equals("K-FIND", ignoreCase = true) &&
            !food.sourceFoodCode.startsWith("D2", ignoreCase = true) &&
            food.category in detailFirstCategories &&
            detailFirstDishSuffixes.any(base::endsWith) &&
            simpleKoreanDetail.matches(detail) &&
            !detail.endsWith("만") &&
            !detail.contains("제외") &&
            !base.contains(detail)

    private fun decimalKey(value: Double?): String = value?.let { round(it * 1000.0).toString() } ?: "missing"
}
