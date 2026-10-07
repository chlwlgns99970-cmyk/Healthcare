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

data class FoodSearchResultGroup(
    val key: String,
    val representative: FoodItem,
    val alternatives: List<FoodItem>
) {
    val size: Int get() = alternatives.size + 1
}

/** Search-only aliases. Source names and nutrition units remain untouched. */
object FoodSearchPolicy {
    fun alternativesLabel(food: FoodItem, count: Int, expanded: Boolean = false): String = when {
        expanded -> "다른 결과 접기"
        !food.brand.isNullOrBlank() -> "다른 제품 ${count.coerceAtLeast(0)}건 보기"
        else -> "같은 이름의 다른 음식 ${count.coerceAtLeast(0)}건 보기"
    }
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
    private val compoundSearchTokens = mapOf(
        "참치김밥" to listOf("참치", "김밥")
    )
    private val detailFirstCategories = setOf("국 및 탕류", "찌개 및 전골류", "죽 및 스프류")
    private val detailFirstDishSuffixes = listOf("찌개", "전골", "스프", "수프", "국", "탕", "죽")
    private val simpleKoreanDetail = Regex("^[가-힣]{1,8}$")
    private val retailBrandPrefixes = listOf("세븐일레븐", "이마트24", "gs25", "씨유", "cu")

    fun normalize(query: String): String = nonNameCharacters.replace(
        Normalizer.normalize(query, Normalizer.Form.NFKC).lowercase(Locale.ROOT), ""
    )

    fun queries(query: String): List<String> {
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptyList()
        return listOfNotNull(normalized, alternateNames[normalized] ?: FranchiseCatalog.canonicalSearchQuery(query)).distinct()
    }

    fun broaderSuggestion(query: String): String? = broaderSearchTerms[normalize(query)]

    fun displayName(food: FoodItem): String {
        basicFruitName(food)?.let { return it }
        val parts = food.name.split('_').map(String::trim).filter(String::isNotBlank)
        if (parts.size == 2 && shouldPlaceDetailFirst(food, parts[0], parts[1])) {
            return parts[1] + parts[0]
        }
        return parts.joinToString(" · ").ifBlank { food.name }
    }

    /** 사용자 검색 화면에서 같은 음식 종류로 볼 수 있는 이름입니다. 원본 이름과 ID는 바꾸지 않습니다. */
    fun canonicalFoodKind(food: FoodItem): String {
        var name = normalize(displayName(food))
        val brand = normalize(food.brand.orEmpty())
        if (brand.isNotBlank()) {
            name = when {
                name.startsWith(brand) && name.length > brand.length -> name.removePrefix(brand)
                name.endsWith(brand) && name.length > brand.length -> name.removeSuffix(brand)
                else -> name
            }
        }
        retailBrandPrefixes.firstOrNull { prefix -> name.startsWith(prefix) && name.length > prefix.length }
            ?.let { name = name.removePrefix(it) }
        if (name.startsWith("김밥") && name.length > "김밥".length) {
            name = name.removePrefix("김밥") + "김밥"
        }
        return name
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

    fun isProduct(food: FoodItem): Boolean = food.sourceType.uppercase(Locale.ROOT) in
        setOf("K-FIND-PRODUCT", "OFFICIAL-RETAIL-PRODUCT")

    fun matchesCategory(food: FoodItem, category: FoodBrowseCategory): Boolean {
        return FoodMenuCategoryPolicy.matchesBrowse(food, category)
    }

    /**
     * 검색 소스가 합쳐질 때 생기는 실질적으로 동일한 행만 접는다.
     * 브랜드·출처 종류·기준량·단위·영양값 중 하나라도 다르면 별도 결과로 보존한다.
     */
    fun deduplicationKey(food: FoodItem): String = listOf(
        normalize(displayName(food)),
        normalize(food.brand.orEmpty()),
        food.sourceType.uppercase(Locale.ROOT),
        // Product reports may describe different packages with equal nutrition.
        if (isProduct(food)) food.sourceFoodCode else "",
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

    /**
     * 화면에서만 같은 표시 이름을 한 묶음으로 접습니다. 원본 FoodItem은 모두 그대로 보존됩니다.
     * 같은 이름의 대표는 일치도, 검증된 제공량, 영양·원재료·알레르기 정보, 출처 순으로 고릅니다.
     */
    fun groupSearchResults(foods: List<FoodItem>, query: String): List<FoodSearchResultGroup> {
        val representativeOrder = representativeComparator(query)
        return foods
            .groupBy(::canonicalFoodKind)
            .map { (key, members) ->
                val ordered = members.sortedWith(representativeOrder)
                FoodSearchResultGroup(key, ordered.first(), ordered.drop(1))
            }
            .sortedWith { a, b ->
                representativeOrder.compare(a.representative,b.representative)
                    .takeIf { it != 0 } ?: a.key.compareTo(b.key)
            }
    }

    /** The lazy result list renders visible cards; matching products stay reachable by scrolling. */
    fun rankedSearchResults(foods: List<FoodItem>, query: String): List<FoodItem> = foods
        .distinctBy(FoodItem::id)
        .sortedWith(representativeComparator(query))
        .distinctBy(::deduplicationKey)

    fun searchRank(food: FoodItem, query: String): Int {
        val normalized = normalize(query)
        val queryVariants = queries(query)
        val name = normalize(food.normalizedName.ifBlank { displayName(food) })
        val aliases = normalize(food.aliases)
        val brand = normalize(food.brand.orEmpty())
        val tokens = compoundSearchTokens[normalized]
            ?: query.trim().split(Regex("[^0-9A-Za-z가-힣]+"))
                .map(::normalize)
                .filter(String::isNotBlank)
                .takeIf { it.size > 1 }
                .orEmpty()
        return when {
            normalized.isNotBlank() && brand.isNotBlank() &&
                (queryVariants.any { it == brand } || FranchiseCatalog.canonicalBrand(query) == food.brand) -> -10
            normalized.isNotBlank() && canonicalFoodKind(food) == normalized -> 0
            normalized.isNotBlank() && brand.isNotBlank() && brand + name == normalized -> 0
            queryVariants.any { name == it } -> 0
            queryVariants.any { hasAlias(food, it) } && food.sourceType == "USDA-SR-LEGACY" -> 5
            tokens.isNotEmpty() && tokens.all(name::contains) -> 10
            queryVariants.any { hasAlias(food, it) } && !isProduct(food) -> 15
            queryVariants.any { name.contains(it) } -> 20
            tokens.any(name::contains) -> 30
            queryVariants.any { aliases.contains(it) } -> 40
            brand.contains(normalized) -> 50
            else -> 60
        }
    }

    /** Relevance is primary; source/brand is only a tie-breaker. */
    fun sourceTieBreakRank(food: FoodItem): Int = foodKindRank(food)

    fun representativeComparator(query: String): Comparator<FoodItem> {
        val normalizedQuery = normalize(query)
        val ranks = mutableMapOf<FoodItem, Int>()
        val servings = mutableMapOf<FoodItem, Int>()
        fun rank(food: FoodItem) = ranks.getOrPut(food) { searchRank(food, query) }
        fun serving(food: FoodItem) = servings.getOrPut(food) { servingCompleteness(food) }
        return compareBy<FoodItem>(
            { representativeBrandRank(it, normalizedQuery) },
            { if (basicFruitName(it)?.let { name -> normalize(name) == normalizedQuery ||
                    normalizedQuery in setOf("과일", "과일류") } == true ||
                    (normalizedQuery in setOf("두부", "달걀", "계란", "삶은달걀", "삶은계란") &&
                        !isProduct(it) && !FranchiseCatalog.isFranchise(it))) 0 else 1 },
            // Keep basic food intent ahead of same-name products; other queries
            // retain exact identity before serving and nutrition evidence quality.
            { rank(it).let { value -> if (value <= 0) value else 1 } },
            { -serving(it) },
            { -nutritionCompleteness(it) },
            { -ingredientCompleteness(it) },
            { -allergenCompleteness(it) },
            { rank(it) },
            { foodKindRank(it) },
            { it.sourceType.uppercase(Locale.ROOT) },
            { it.sourceFoodCode },
            { it.id }
        )
    }

    fun visibleBrands(brands: List<com.example.healthcare.data.entity.FoodBrandSummary>): List<com.example.healthcare.data.entity.FoodBrandSummary> =
        brands.filter { it.productCount > 0 }

    private fun servingCompleteness(food: FoodItem): Int {
        val choice = FoodAmountPolicy.defaultChoice(food)
        return when (choice?.quality) {
            PortionQuality.OFFICIAL_SERVING -> if (choice.unit == "제품 전체") 3 else 4
            PortionQuality.VERIFIED_CONVERSION -> 2
            PortionQuality.WEIGHT_ONLY, PortionQuality.VOLUME_ONLY -> 1
            PortionQuality.UNRESOLVED, null -> 0
        }
    }

    private fun ingredientCompleteness(food: FoodItem): Int = FoodMetadataPolicy.lookup(food.id)?.let {
        if (it.ingredientInfoComplete) 2 else if (it.ingredients.isNotEmpty()) 1 else 0
    } ?: 0
    private fun allergenCompleteness(food: FoodItem): Int = FoodMetadataPolicy.lookup(food.id)?.let {
        if (it.allergenInfoComplete) 2 else if (it.allergens.isNotEmpty()) 1 else 0
    } ?: 0

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
            ((food.category in detailFirstCategories && detailFirstDishSuffixes.any(base::endsWith)) ||
                base == "김밥") &&
            simpleKoreanDetail.matches(detail) &&
            !detail.endsWith("만") &&
            !detail.contains("제외") &&
            !base.contains(detail)

    private fun nutritionCompleteness(food: FoodItem): Int = listOf(
        food.energyKcal,
        food.carbohydrateGrams,
        food.proteinGrams,
        food.fatGrams
    ).count { it != null && it.isFinite() && it >= 0.0 }

    private fun representativeBrandRank(food: FoodItem, normalizedQuery: String): Int {
        if (normalizedQuery.isBlank()) return 1
        val brand = normalize(food.brand.orEmpty())
        val name = canonicalFoodKind(food)
        return if (brand.isNotBlank() && (normalizedQuery == brand || normalizedQuery == brand + name ||
            normalizedQuery == name + brand || queries(normalizedQuery).any { it == brand } ||
            FranchiseCatalog.canonicalBrand(normalizedQuery) == food.brand)) 0 else 1
    }

    private fun hasAlias(food: FoodItem, normalized: String): Boolean =
        food.aliases.split('|').any { normalize(it) == normalized }

    /** Raw reference fruits use their source alias as the food kind; juice/snacks keep their own names. */
    private fun basicFruitName(food: FoodItem): String? =
        if (food.sourceType == "USDA-SR-LEGACY" && food.category == "과일류") {
            food.aliases.split('|').firstOrNull {
                it.isNotBlank() && it != "과일" && it != "과일류"
            }
        } else null

    private fun foodKindRank(food: FoodItem): Int = when {
        food.sourceType == "USDA-SR-LEGACY" -> 0
        !isProduct(food) && !FranchiseCatalog.isFranchise(food) -> 1
        FranchiseCatalog.isFranchise(food) -> 2
        else -> 3
    }

    private fun decimalKey(value: Double?): String = value?.let { round(it * 1000.0).toString() } ?: "missing"
}
