package com.example.healthcare.domain

/** Conservative aliases for the allergen causes exposed by the settings screen. */
object FoodAllergenPolicy {
    val supportedCauses: List<String> = listOf(
        "우유", "달걀", "땅콩", "견과류", "밀", "대두", "새우", "게", "생선", "조개류"
    )

    private val aliases: Map<String, Set<String>> = mapOf(
        "우유" to setOf("우유", "유제품"),
        "달걀" to setOf("달걀", "계란", "난류", "알류"),
        "땅콩" to setOf("땅콩"),
        "견과류" to setOf("견과류", "견과", "호두", "잣", "아몬드", "캐슈넛", "피스타치오", "마카다미아"),
        "밀" to setOf("밀", "밀가루"),
        "대두" to setOf("대두"),
        "새우" to setOf("새우"),
        "게" to setOf("게", "꽃게", "대게", "홍게"),
        "생선" to setOf("생선", "고등어", "참치", "연어", "명태", "대구"),
        "조개류" to setOf("조개류", "조개", "홍합", "굴", "전복")
    )

    fun canonicalize(value: String): String? {
        val normalized = MealRecommendationEngine.normalizeFoodName(value)
        return aliases.entries.firstOrNull { (_, values) ->
            values.any { MealRecommendationEngine.normalizeFoodName(it) == normalized }
        }?.key
    }

    fun matches(value: String, canonicalCause: String): Boolean {
        val normalizedValues = (value.split(Regex("[|,@\\s()\\[\\]·/]+")) + value)
            .map(MealRecommendationEngine::normalizeFoodName).filter(String::isNotBlank).toSet()
        val aliasesForCause = aliases[canonicalCause] ?: setOf(canonicalCause)
        return aliasesForCause.any { alias ->
            MealRecommendationEngine.normalizeFoodName(alias) in normalizedValues
        }
    }
}
