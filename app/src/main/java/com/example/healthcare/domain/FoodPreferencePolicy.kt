package com.example.healthcare.domain

/** Each style is backed by existing cuisine or linked FoodItem category metadata. */
enum class FoodPreferenceStyle(val token: String, val label: String) {
    KOREAN("STYLE:KOREAN", "한식"),
    RICE("STYLE:RICE", "밥류"),
    NOODLE("STYLE:NOODLE", "면·만두")
}

/** Styles and existing food keywords share UserMealPreference.preferredFoods. */
object FoodPreferencePolicy {
    val styles: List<FoodPreferenceStyle> = FoodPreferenceStyle.entries.toList()

    fun stylesFromStored(value: String): Set<FoodPreferenceStyle> =
        value.split('|').mapNotNull(::styleForToken).toSet()

    fun keywordsFromStored(value: String): List<String> = value.split('|')
        .map(String::trim)
        .filter { it.isNotBlank() && styleForToken(it) == null }
        .distinctBy(MealRecommendationEngine::normalizeFoodName)

    fun serialize(styles: Set<FoodPreferenceStyle>, keywords: Collection<String>): String {
        val tokens = FoodPreferenceStyle.entries.filter { it in styles }.map { it.token } +
            keywords.map(MealRecommendationEngine::normalizeFoodName)
                .filter(String::isNotBlank).distinct()
        return if (tokens.isEmpty()) "" else "|${tokens.joinToString("|")}|"
    }

    fun matches(seed: RecommendationSeed, preferredNames: Set<String>): Boolean {
        val selectedStyles = preferredNames.mapNotNull(::styleForToken)
        if (selectedStyles.any { style ->
            when (style) {
                FoodPreferenceStyle.KOREAN -> seed.template.cuisineType.equals("KOREAN", true)
                FoodPreferenceStyle.RICE -> "밥류" in seed.ingredientCategories
                FoodPreferenceStyle.NOODLE -> "면 및 만두류" in seed.ingredientCategories
            }
        }) return true
        val keywords = preferredNames.filter { styleForToken(it) == null }
            .map(MealRecommendationEngine::normalizeFoodName).filter(String::isNotBlank)
        if (keywords.isEmpty()) return false
        val names = (seed.ingredientNames + seed.template.name)
            .map(MealRecommendationEngine::normalizeFoodName).filter(String::isNotBlank)
        return names.any { name -> keywords.any { keyword -> name.contains(keyword) || keyword.contains(name) } }
    }

    private fun styleForToken(value: String): FoodPreferenceStyle? {
        val normalized = MealRecommendationEngine.normalizeFoodName(value)
        return FoodPreferenceStyle.entries.firstOrNull {
            MealRecommendationEngine.normalizeFoodName(it.token) == normalized
        }
    }
}
