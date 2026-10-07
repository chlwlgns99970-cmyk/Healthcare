package com.example.healthcare.domain

/** Major food groups verified from a source recipe/definition, never from a menu-name heuristic.
 * A reference recipe identifies its defining ingredients; it does not certify every restaurant
 * preparation, ingredient quantity, nutrition value or allergen. Those existing unknowns remain.
 */
data class VerifiedIngredientGroups(
    val ingredients: Set<String>,
    val groups: Set<String>,
    val grainType: String,
    val proteinSources: Set<String>,
    val cookingStyle: String,
    val sourceUrl: String,
    val evidenceScope: String,
    val verifiedAt: String,
    val eligible: Boolean,
    val negativeSignals: Set<String> = emptySet()
)

enum class SlowStyleVerdict {
    ELIGIBLE, MISSING_VERIFIED_COMPOSITION, VERIFIED_DEEP_FRIED, VERIFIED_PROCESSED_MEAT,
    VERIFIED_SUGAR_OR_JUICE_MODERATION, VERIFIED_NO_PREFERRED_GROUP
}

object SlowAgingStylePolicy {
    const val DESCRIPTION = "공공 조리자료·공식 설명에서 확인한 잡곡·콩류·채소 등의 주요 식품군을 중심으로 골랐어요."
    const val VARIATION_NOTE = "실제 조리법과 재료 구성은 달라질 수 있어요."
    // WHO/NHLBI support food-group variety and moderation, rather than requiring every dish
    // to contain all groups or prohibiting all meat. These app weights are a composition
    // preference, not a clinical score, DASH compliance test or claim about aging.
    // Source recipes do not establish actual sodium, sugar/fat amounts or full ingredients.
    val ruleSources = listOf(
        "https://www.who.int/news-room/fact-sheets/detail/healthy-diet",
        "https://www.nhlbi.nih.gov/health/dash-eating-plan"
    )
    private val preferredGroupWeights = mapOf("WHOLE_GRAIN" to 4.0, "MIXED_GRAIN" to 3.0,
        "LEGUME_SOY" to 4.0, "VEGETABLE" to 3.0, "NUT_SEED" to 3.0, "FRUIT" to 3.0,
        "FISH" to 3.0, "SEAFOOD" to 2.0)

    fun evidence(templateId: String): VerifiedIngredientGroups? = GeneratedIngredientGroups.byTemplateId[templateId]
    fun groups(seed: RecommendationSeed): Set<String> = evidence(seed.template.id)?.groups.orEmpty()

    fun verdict(seed: RecommendationSeed): SlowStyleVerdict {
        val verified = evidence(seed.template.id) ?: return SlowStyleVerdict.MISSING_VERIFIED_COMPOSITION
        if (verified.ingredients.isEmpty() || verified.groups.isEmpty() ||
            !verified.sourceUrl.startsWith("https://") || verified.verifiedAt.isBlank()) {
            return SlowStyleVerdict.MISSING_VERIFIED_COMPOSITION
        }
        return when {
            verified.cookingStyle == "DEEP_FRIED" -> SlowStyleVerdict.VERIFIED_DEEP_FRIED
            "PROCESSED_MEAT" in verified.groups || "PROCESSED_MEAT" in verified.negativeSignals ->
                SlowStyleVerdict.VERIFIED_PROCESSED_MEAT
            verified.negativeSignals.any { it in setOf("SUGAR_HEAVY_DESSERT", "SWEETENED_BEVERAGE", "FRUIT_JUICE") } ->
                SlowStyleVerdict.VERIFIED_SUGAR_OR_JUICE_MODERATION
            !verified.eligible || compositionScore(verified) < 2.0 -> SlowStyleVerdict.VERIFIED_NO_PREFERRED_GROUP
            else -> SlowStyleVerdict.ELIGIBLE
        }
    }

    fun eligible(seed: RecommendationSeed): Boolean = verdict(seed) == SlowStyleVerdict.ELIGIBLE

    private fun compositionScore(evidence: VerifiedIngredientGroups): Double =
        evidence.groups.sumOf { preferredGroupWeights[it] ?: 0.0 } -
            (if ("RED_MEAT" in evidence.groups || "RED_MEAT_PRESENT" in evidence.negativeSignals) 2.0 else 0.0)

    fun score(seed: RecommendationSeed): Double = evidence(seed.template.id)?.let(::compositionScore) ?: 0.0

    /** Scores the whole combination without changing calories or increasing a food's quantity. */
    fun dailyScore(meals: List<DailyPlanMeal>): Double {
        val evidence = meals.mapNotNull { it.templateId?.let(::evidence) }
        val groups = evidence.flatMap { it.groups }.toSet()
        val proteins = evidence.flatMap { it.proteinSources }
            .filter { it in setOf("LEGUME_SOY", "POULTRY", "FISH", "SEAFOOD", "EGG", "DAIRY", "NUT_SEED") }.toSet()
        val grainTypes = evidence.map { it.grainType }.filter { it != "UNKNOWN" && it != "NONE" }.toSet()
        return (if (groups.any { it == "MIXED_GRAIN" || it == "WHOLE_GRAIN" }) 8.0 else 0.0) +
            (if ("LEGUME_SOY" in groups) 8.0 else 0.0) +
            (if ("VEGETABLE" in groups) 8.0 else 0.0) +
            proteins.size.coerceAtMost(3) * 3.0 + grainTypes.size.coerceAtMost(2) * 2.0 +
            groups.count { it in preferredGroupWeights }.coerceAtMost(5) * 2.0 -
            evidence.count { "RED_MEAT" in it.groups || "RED_MEAT_PRESENT" in it.negativeSignals } * 2.0
    }
}
