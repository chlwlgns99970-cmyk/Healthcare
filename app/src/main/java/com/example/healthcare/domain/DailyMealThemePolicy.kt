package com.example.healthcare.domain

import com.example.healthcare.data.model.MealType

object DailyMealThemePolicy {
    const val SLOW_STYLE_LIMITATION = "현재 취향·제외 조건으로 확인된 식품군의 하루 식단을 구성하기 어려워요."
    fun complete(nutrition: Macronutrients): Boolean = listOf(
        nutrition.carbohydrateGrams, nutrition.proteinGrams, nutrition.fatGrams
    ).all { it != null && it.isFinite() && it >= 0.0 }

    fun nutrition(seed: RecommendationSeed) = Macronutrients(seed.template.carbohydrateGrams,
        seed.template.proteinGrams, seed.template.fatGrams)

    fun nearBalanced(seed: RecommendationSeed): Boolean {
        val n = nutrition(seed)
        if (!complete(n)) return false
        val c = requireNotNull(n.carbohydrateGrams) * 4
        val p = requireNotNull(n.proteinGrams) * 4
        val f = requireNotNull(n.fatGrams) * 9
        val sum = c + p + f
        return sum > 0 && c / sum in 0.40..0.70 && p / sum in 0.08..0.40 && f / sum in 0.15..0.40
    }

    fun eligible(pool: List<RecommendationSeed>, theme: DailyRecommendationTheme, meal: MealType): List<RecommendationSeed> {
        val valid = pool.filter { MealRecommendationEngine.supportsMeal(it.template, meal) && it.template.totalKcal > 0 }
        return when (theme) {
            DailyRecommendationTheme.LIGHT -> MealRecommendationThemePolicy.select(valid, MealRecommendationTheme.LIGHT, meal)
            DailyRecommendationTheme.HEARTY -> MealRecommendationThemePolicy.select(valid, MealRecommendationTheme.FILLING, meal)
            DailyRecommendationTheme.BALANCED -> valid.filter { MealRecommendationThemePolicy.matchesBalanced(it.template) }
            DailyRecommendationTheme.HEALTHY -> valid.filter(::nearBalanced)
            DailyRecommendationTheme.SLOW_AGING_STYLE -> valid.filter(SlowAgingStylePolicy::eligible)
            // Diet/bulk are preferences within the actual target; neither imposes a deficit/surplus.
            DailyRecommendationTheme.DIET, DailyRecommendationTheme.BULK, DailyRecommendationTheme.CHEAT -> valid
        }
    }

    fun styleScore(seed: RecommendationSeed, theme: DailyRecommendationTheme, mealPool: List<RecommendationSeed>): Double {
        val low = mealPool.minOfOrNull { it.template.totalKcal } ?: seed.template.totalKcal
        val high = mealPool.maxOfOrNull { it.template.totalKcal } ?: low
        val relative = if (high == low) 0.5 else (seed.template.totalKcal - low).toDouble() / (high - low)
        val protein = seed.template.proteinGrams?.takeIf { it.isFinite() && it >= 0.0 }
        return when (theme) {
            DailyRecommendationTheme.DIET -> (1 - relative) * 12 + (if (complete(nutrition(seed))) 6 else 0) + (if (protein != null) 2 else 0)
            DailyRecommendationTheme.BULK -> relative * 12 + (protein ?: 0.0).coerceAtMost(50.0) * 0.2
            DailyRecommendationTheme.SLOW_AGING_STYLE -> SlowAgingStylePolicy.score(seed)
            else -> 0.0
        }
    }
}

