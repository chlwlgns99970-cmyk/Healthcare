package com.example.healthcare.domain

import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.model.MealType
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class RecommendationSeed(
    val template: MealTemplate,
    val ingredientNames: Set<String>,
    val allergenTags: Set<String> = emptySet(),
    val ingredientInfoComplete: Boolean = true,
    val recentUseCount: Int = 0,
    val dataCompleteness: Double = 1.0,
    val ingredientCategories: Set<String> = emptySet(),
    val servingsByMeal: Map<MealType, List<RecommendationServing>> = emptyMap()
)

data class ScoredMealRecommendation(
    val template: MealTemplate,
    val score: Double,
    val calorieDifference: Int,
    val toleranceKcal: Int,
    val reason: String,
    val appliedReasons: List<String> = emptyList(),
    val preferenceMatched: Boolean = false,
    val learnedScore: Double = 0.0,
    val portion: Double = 1.0,
    val amountLabels: List<String> = emptyList()
)

enum class RecommendationStage { EXACT, WIDER_CALORIES, EXPANDED_MODE, CLOSEST_VERIFIED }

object MealRecommendationEngine {
    fun passesHardFilters(
        seed: RecommendationSeed,
        excludedNames: Set<String>,
        allergyNames: Set<String>,
        dietType: String
    ): Boolean {
        if (seed.template.totalKcal <= 0 || !seed.dataCompleteness.isFinite() || seed.dataCompleteness < 1.0 ||
            seed.ingredientNames.isEmpty() || seed.template.source.isBlank()) return false
        val dislikes = excludedNames.map(::normalizeFoodName).filter(String::isNotBlank).toSet()
        val searchable = (seed.ingredientNames + SlowAgingStylePolicy.evidence(seed.template.id)?.ingredients.orEmpty() + seed.template.name)
            .map(::normalizeFoodName).filter(String::isNotBlank)
        if (searchable.any { candidate -> dislikes.any(candidate::contains) }) return false

        return !containsToken(seed.template.excludedDietTypes, dietType)
    }

    /** 알레르기는 추천을 숨기지 않고, 확인된 정보에 한해 UI 주의로 전달합니다. */
    fun matchedAllergens(seed: RecommendationSeed, allergyNames: Set<String>): Set<String> {
        // 음식명은 원재료표가 아니므로 알레르기 근거로 사용하지 않습니다.
        // 공식/공공 자료로 확인해 저장한 allergenTags만 사용자 설정과 대조합니다.
        val searchable = seed.allergenTags
        return allergyNames.mapNotNull(FoodAllergenPolicy::canonicalize)
            .filterTo(linkedSetOf()) { cause ->
                searchable.any { FoodAllergenPolicy.matches(it, cause) }
            }
    }

    fun recommend(
        candidates: List<RecommendationSeed>,
        budgetKcal: Int,
        excludedNames: Set<String>,
        allergyNames: Set<String>,
        preferredNames: Set<String>,
        dietType: String,
        cookingMode: String = "ANY",
        budgetLevel: String = "ANY",
        recommendationDiversity: String = "BALANCED",
        limit: Int = 3,
        stage: RecommendationStage = RecommendationStage.EXACT,
        allowedCookingModes: Set<String> = setOf(cookingMode),
        excludedTemplateIds: Set<String> = emptySet(),
        mealType: MealType? = null,
        theme: MealRecommendationTheme? = null,
        learnedScores: Map<String, Double> = emptyMap()
    ): List<ScoredMealRecommendation> {
        if (budgetKcal <= 0 || limit <= 0) return emptyList()
        val preferred = preferredNames.filter { normalizeFoodName(it).isNotBlank() }.toSet()
        val filtered = candidates.filter { seed ->
            (mealType == null || supportsMeal(seed.template, mealType)) &&
                passesHardFilters(seed, excludedNames, allergyNames, dietType) &&
                ("ANY" in allowedCookingModes || allowedCookingModes.any { containsToken(seed.template.tags, it) }) &&
                costWithinBudget(seed.template.costLevel, budgetLevel)
        }.distinctBy { it.template.id }
        val themed = if (theme == null) filtered else {
            // Relative themes require a concrete meal; never compare breakfast to lunch or snacks.
            if (mealType == null) return emptyList()
            MealRecommendationThemePolicy.select(filtered, theme, mealType)
        }

        val baseTolerance = max((budgetKcal * 0.10).toInt(), 80)
        val tolerance = when (stage) {
            RecommendationStage.EXACT -> baseTolerance
            RecommendationStage.CLOSEST_VERIFIED -> Int.MAX_VALUE
            else -> max((budgetKcal * 0.20).toInt(), 150)
        }
        val inRange = themed.flatMap { seed ->
            val servings = mealType?.let { RecommendationServingPolicy.available(seed, it) }
                ?: listOf(RecommendationServing(1.0, seed.template.totalKcal, DailyMealThemePolicy.nutrition(seed)))
            servings.filter { seed.template.id !in excludedTemplateIds && abs(it.kcal - budgetKcal) <= tolerance &&
                (mealType != MealType.SNACK || it.kcal <= RecommendationServingPolicy.MAX_SNACK_KCAL) }
                .map { seed to it }
        }

        return inRange.map { (originalSeed, serving) ->
            val seed = originalSeed.copy(template = originalSeed.template.copy(totalKcal = serving.kcal,
                carbohydrateGrams = serving.nutrition.carbohydrateGrams, proteinGrams = serving.nutrition.proteinGrams,
                fatGrams = serving.nutrition.fatGrams))
            val calorieFit = (1.0 - abs(seed.template.totalKcal - budgetKcal).toDouble() / max(budgetKcal, 1)).coerceIn(0.0, 1.0)
            val preferenceMatched = FoodPreferencePolicy.matches(seed, preferred)
            val preferenceFit = if (preferred.isEmpty()) 0.5 else if (preferenceMatched) 1.0 else 0.0
            val variety = when (recommendationDiversity) {
                "FAMILIAR" -> if (seed.recentUseCount > 0) 1.0 else 0.5
                "VARIED" -> (1.0 - seed.recentUseCount * 0.4).coerceIn(0.0, 1.0)
                else -> (1.0 - seed.recentUseCount * 0.25).coerceIn(0.0, 1.0)
            }
            val score = calorieFit * 55.0 + preferenceFit * 20.0 + variety * 15.0 +
                seed.dataCompleteness.coerceIn(0.0, 1.0) * 10.0
            val learned = RecommendationLearningPolicy.bounded(learnedScores[seed.template.id] ?: 0.0)
            val appliedReasons = buildList {
                if (preferenceMatched) add("좋아하는 음식 취향을 반영했어요.")
                if (learned > 0) add("실제 기록과 즐겨찾기를 약하게 반영했어요.")
                if (theme != null) add(when (theme) {
                    MealRecommendationTheme.LIGHT -> "같은 식사 후보 중 칼로리가 낮은 쪽에서 골랐어요."
                    MealRecommendationTheme.BALANCED -> "표시된 탄단지 열량비 기준에 맞는 메뉴예요."
                    MealRecommendationTheme.FILLING -> "같은 식사 후보 중 칼로리가 높은 쪽에서 골랐어요."
                })
                if (stage != RecommendationStage.CLOSEST_VERIFIED) {
                    add("현재 식사의 칼로리 참고 범위에 맞는 후보예요.")
                }
                if (excludedNames.any { normalizeFoodName(it).isNotBlank() }) {
                    add("피하고 싶은 음식·재료를 제외한 후보예요.")
                }
                if (excludedTemplateIds.isNotEmpty()) add("다른 추천과 겹치지 않게 골랐어요.")
            }
            val reason = when {
                preferenceMatched -> appliedReasons.first()
                theme != null -> appliedReasons.first { it != "좋아하는 음식 취향을 반영했어요." }
                calorieFit >= 0.95 -> "다음 한 끼의 참고 범위에 가까워요."
                recommendationDiversity == "FAMILIAR" && seed.recentUseCount > 0 ->
                    "최근 선택했던 익숙한 식단이에요."
                seed.recentUseCount == 0 && variety >= 0.75 -> "최근 식단과 겹치지 않는 선택이에요."
                excludedNames.any { normalizeFoodName(it).isNotBlank() } -> "피하고 싶은 음식·재료를 제외한 후보예요."
                stage == RecommendationStage.CLOSEST_VERIFIED -> "현재 식사 참고 칼로리와 가까운 후보부터 골랐어요."
                else -> "현재 식사의 칼로리 참고 범위에 맞는 후보예요."
            }
            ScoredMealRecommendation(
                template = seed.template,
                score = score + learned,
                calorieDifference = seed.template.totalKcal - budgetKcal,
                toleranceKcal = tolerance,
                reason = reason,
                appliedReasons = (listOf(reason) + appliedReasons).distinct(),
                preferenceMatched = preferenceMatched,
                learnedScore = learned,
                portion = serving.portion,
                amountLabels = serving.labels
            )
        }.sortedWith((if (stage == RecommendationStage.CLOSEST_VERIFIED) {
            compareByDescending<ScoredMealRecommendation> { it.preferenceMatched }
                .thenBy { abs(it.calorieDifference) }.thenByDescending { it.score }
        } else {
            // Eligible calorie range and hard theme have already been checked above.
            compareByDescending<ScoredMealRecommendation> { it.preferenceMatched }
                .thenByDescending { it.score }.thenBy { abs(it.calorieDifference) }
        }).thenBy { it.template.id })
            .distinctBy { it.template.id }
            .distinctBy { normalizeFoodName(it.template.name) }
            .take(limit)
    }

    fun normalizeFoodName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^0-9a-z가-힣]"), "")

    fun supportsMeal(template: MealTemplate, mealType: MealType): Boolean =
        containsToken(template.supportedMealTypes, mealType.name)

    fun hasCompleteIngredientInfo(tags: String): Boolean = containsToken(tags, "INGREDIENTS_COMPLETE")

    private fun containsToken(tokens: String, value: String): Boolean {
        val normalized = value.trim().uppercase()
        return normalized.isNotBlank() && tokens.uppercase().split('|').any { it == normalized }
    }

    private fun costWithinBudget(costLevel: String, budgetLevel: String): Boolean {
        if (budgetLevel == "ANY") return true
        val ranks = mapOf("LOW" to 1, "MEDIUM" to 2, "HIGH" to 3)
        val candidateRank = ranks[costLevel.uppercase()] ?: return false
        val budgetRank = ranks[budgetLevel.uppercase()] ?: return true
        return candidateRank <= budgetRank
    }
}
