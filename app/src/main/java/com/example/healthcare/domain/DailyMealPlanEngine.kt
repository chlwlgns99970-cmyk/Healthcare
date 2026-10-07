package com.example.healthcare.domain

import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.entity.UserMealPreference
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.TargetMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

enum class DailyRecommendationTheme(val label: String, val description: String) {
    LIGHT("가볍게", "같은 끼니에서 상대적으로 가벼운 메뉴 중심"),
    BALANCED("균형 있게", "앱의 탄수화물·단백질·지방 균형 기준으로 구성"),
    HEARTY("든든하게", "현재 목표에 가까운 든든한 메뉴 중심"),
    DIET("다이어트", "현재 목표 안에서 가벼운 메뉴와 영양정보를 고려"),
    BULK("벌크업", "현재 목표 안에서 열량과 단백질 정보를 고려"),
    HEALTHY("건강하게", "영양정보가 충분하고 균형 기준에 가까운 메뉴 중심"),
    CHEAT("치팅데이", "현재 목표를 유지하며 좋아하는 메뉴를 더 우선"),
    SLOW_AGING_STYLE("저속노화식 스타일", "확인 가능한 곡물·콩·채소 식품군 기준으로 구성")
}

data class DailyPlanMeal(
    val mealType: MealType,
    val templateId: String? = null,
    val name: String,
    val kcal: Int,
    val nutrition: Macronutrients,
    val portion: Double = 1.0,
    val recorded: Boolean = false,
    val recordIds: List<Long> = emptyList(),
    val matchedAllergens: Set<String> = emptySet(),
    val ingredientInfoComplete: Boolean = true,
    val allergenInfoComplete: Boolean = true,
    val foodGroups: Set<String> = emptySet(),
    val preferenceMatched: Boolean = false,
    val nutritionComplete: Boolean = true,
    val amountLabels: List<String> = emptyList()
)

data class TodayMealPlan(
    val date: String,
    val theme: DailyRecommendationTheme,
    val targetKcal: Int,
    val meals: List<DailyPlanMeal>,
    val reasons: List<String> = emptyList(),
    val generationMillis: Long = 0
) {
    val totalKcal: Int get() = meals.sumOf { it.kcal }
    val recordedKcal: Int get() = meals.filter { it.recorded }.sumOf { it.kcal }
    val remainingBudgetKcal: Int get() = (targetKcal - recordedKcal).coerceAtLeast(0)
    val nutrition: Macronutrients get() = Macronutrients.knownSum(meals.map { it.nutrition })
    val macroComplete: Boolean get() = meals.all { it.nutritionComplete && DailyMealThemePolicy.complete(it.nutrition) }
    val signature: String get() = theme.name + ":" + meals.joinToString("|") {
        "${it.mealType.name}:${it.templateId ?: "record-${it.recordIds.joinToString(",")}"}:${it.portion}"
    }
    val differenceText: String get() {
        val difference = totalKcal - targetKcal
        return when {
            difference == 0 -> "현재 하루 목표와 같은 칼로리의 조합이에요."
            abs(difference).toDouble() / targetKcal <= 0.05 -> "현재 하루 목표의 ±5% 이내 조합이에요."
            abs(difference).toDouble() / targetKcal <= 0.10 -> "현재 하루 목표의 ±10% 이내 조합이에요."
            else -> "현재 메뉴 데이터에서는 목표보다 약 ${abs(difference)} kcal ${if (difference < 0) "낮은" else "높은"} 조합이에요."
        }
    }
}

/** No default target: a manual goal must exist, or a configured BMR/maintenance mode must resolve. */
object DailyCalorieTarget {
    fun resolve(goal: CalorieGoal?, profile: EnergyProfileHistory?): Int? = runCatching {
        when (profile?.targetMode) {
            TargetMode.BMR -> profile.basalMetabolicRateKcal
            TargetMode.MAINTENANCE -> EnergyBalanceCalculator.roundKcal(
                EnergyBalanceCalculator.calculateMaintenanceKcal(profile.basalMetabolicRateKcal.toDouble(), profile.palMultiplier))
            else -> goal?.targetCalories
        }?.takeIf { it > 0 }
    }.getOrNull()
}

/** Bounded deterministic search. All calories/macros use the existing standard ingredient portion. */
object DailyMealPlanEngine {
    val slots = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)
    private val shares = mapOf(MealType.BREAKFAST to 0.25, MealType.LUNCH to 0.30, MealType.DINNER to 0.35, MealType.SNACK to 0.10)
    private val ranges = mapOf(MealType.BREAKFAST to (0.20..0.30), MealType.LUNCH to (0.25..0.35),
        MealType.DINNER to (0.25..0.35), MealType.SNACK to (0.05..0.15))
    private const val TOP_K = 40
    private const val BEAM = 256

    /** Shares exactly the same remaining-meal allocation as plan generation. */
    fun nextMealRange(remainingKcal: Int, unrecorded: Set<MealType>, meal: MealType): IntRange? {
        if (remainingKcal <= 0 || meal !in unrecorded || meal !in shares) return null
        val totalShare = unrecorded.sumOf { shares[it] ?: 0.0 }
        if (totalShare <= 0) return null
        val range = ranges.getValue(meal)
        return floor(remainingKcal * range.start / totalShare).toInt().coerceAtLeast(0)..
            ceil(remainingKcal * range.endInclusive / totalShare).toInt().coerceAtLeast(0)
    }

    fun recordedMeals(records: List<MealRecord>): Map<MealType, DailyPlanMeal> = records.groupBy { it.mealType }
        .filterKeys { it in slots }.mapValues { (meal, rows) ->
            DailyPlanMeal(mealType = meal, name = rows.joinToString(" + ") { it.foodName },
                kcal = rows.sumOf { it.calories }, recorded = true, recordIds = rows.map { it.id }.sorted(),
                nutrition = Macronutrients.knownSum(rows.map { Macronutrients(it.carbohydrateGrams, it.proteinGrams, it.fatGrams) }),
                ingredientInfoComplete = false, allergenInfoComplete = false,
                nutritionComplete = rows.all { DailyMealThemePolicy.complete(Macronutrients(it.carbohydrateGrams, it.proteinGrams, it.fatGrams)) })
        }

    fun candidates(
        seeds: List<RecommendationSeed>, theme: DailyRecommendationTheme, preference: UserMealPreference,
        dislikes: Set<String>, allergies: Set<String>
    ): Map<MealType, List<RecommendationSeed>> {
        val hard = seeds.filter { seed ->
            MealRecommendationEngine.passesHardFilters(seed, dislikes, allergies, preference.dietType) &&
                (preference.cookingMode == "ANY" || "|${preference.cookingMode}|" in seed.template.tags) &&
                seed.template.preparationMinutes <= preference.maxPreparationMinutes &&
                (preference.budgetLevel == "ANY" || (mapOf("LOW" to 1, "MEDIUM" to 2, "HIGH" to 3)[seed.template.costLevel] ?: 4) <=
                    (mapOf("LOW" to 1, "MEDIUM" to 2, "HIGH" to 3)[preference.budgetLevel] ?: 3))
        }.distinctBy { it.template.id }
        return slots.associateWith { DailyMealThemePolicy.eligible(hard, theme, it) }
    }

    fun generate(
        date: String, target: Int?, theme: DailyRecommendationTheme, seeds: List<RecommendationSeed>,
        preference: UserMealPreference, dislikes: Set<String> = emptySet(), allergies: Set<String> = emptySet(),
        records: List<MealRecord> = emptyList(), fixed: Map<MealType, DailyPlanMeal> = emptyMap(),
        seen: Map<MealType, Set<String>> = emptyMap(), excludedSignatures: Set<String> = emptySet(),
        excludedIds: Set<String> = emptySet(), favorites: Set<String> = emptySet(),
        learnedScores: Map<String, Double> = emptyMap()
    ): TodayMealPlan? {
        if (target == null || target <= 0) return null
        val recorded = recordedMeals(records)
        val locked = fixed + recorded
        val pool = candidates(seeds, theme, preference, dislikes, allergies)
        val unlocked = slots.filterNot(locked::containsKey)
        if (unlocked.isNotEmpty() && target <= recorded.values.sumOf { it.kcal }) return null
        if (unlocked.any { pool[it].isNullOrEmpty() }) return null
        val remaining = (target - locked.values.sumOf { it.kcal }).coerceAtLeast(0)
        val shareTotal = unlocked.sumOf { shares.getValue(it) }
        val preferred = preference.preferredFoods.split('|').filter(String::isNotBlank).toSet()
        data class Option(val meal: DailyPlanMeal, val score: Double, val allocationPenalty: Double)
        val options = unlocked.associateWith { meal ->
            val budget = remaining * shares.getValue(meal) / shareTotal
            val minimumBudget = remaining * ranges.getValue(meal).start / shareTotal
            val maximumBudget = remaining * ranges.getValue(meal).endInclusive / shareTotal
            val allowed = pool.getValue(meal).filterNot { it.template.id in excludedIds ||
                locked.values.any { fixedMeal -> fixedMeal.templateId == it.template.id ||
                    MealRecommendationEngine.normalizeFoodName(fixedMeal.name) == MealRecommendationEngine.normalizeFoodName(it.template.name) } }
            val unseen = allowed.filterNot { it.template.id in seen[meal].orEmpty() }
            val cyclePool = unseen
            val all = cyclePool.flatMap { seed ->
                RecommendationServingPolicy.available(seed, meal).filter { serving ->
                    meal != MealType.SNACK || serving.kcal <= minOf(RecommendationServingPolicy.MAX_SNACK_KCAL, maximumBudget.toInt())
                }.map { serving ->
                val matched = FoodPreferencePolicy.matches(seed, preferred)
                val learned = RecommendationLearningPolicy.bounded(learnedScores[seed.template.id]
                    ?: if (FoodPreferencePolicy.matches(seed, favorites)) 3.0 else 0.0)
                val score = (if (matched) if (theme == DailyRecommendationTheme.CHEAT) 45.0 else 20.0 else 0.0) +
                    learned +
                    DailyMealThemePolicy.styleScore(seed, theme, pool.getValue(meal)) -
                    abs(serving.kcal - budget) / target * 15.0 -
                    (if (seed.template.id in seen[meal].orEmpty()) 100.0 else 0.0)
                Option(DailyPlanMeal(meal, seed.template.id, seed.template.name, serving.kcal,
                    serving.nutrition, portion = serving.portion, matchedAllergens = MealRecommendationEngine.matchedAllergens(seed, allergies),
                    ingredientInfoComplete = seed.ingredientInfoComplete,
                    allergenInfoComplete = "UNKNOWN" !in seed.allergenTags,
                    foodGroups = if (theme == DailyRecommendationTheme.SLOW_AGING_STYLE) SlowAgingStylePolicy.groups(seed) else seed.ingredientCategories,
                    preferenceMatched = matched, amountLabels = serving.labels), score,
                    when {
                        serving.kcal < minimumBudget -> minimumBudget - serving.kcal
                        serving.kcal > maximumBudget -> serving.kcal - maximumBudget
                        else -> 0.0
                    })
                }
            }
            // Keep calorically diverse options as well as the best-fit options.
            val best = all.sortedWith(compareByDescending<Option> { it.meal.preferenceMatched }
                .thenByDescending { it.score }.thenBy { abs(it.meal.kcal - budget) }.thenBy { it.meal.templateId }).take(TOP_K - 8)
            (best + all.sortedBy { it.meal.kcal }.take(4) + all.sortedByDescending { it.meal.kcal }.take(4))
                .distinctBy { it.meal.templateId to it.meal.portion }
        }
        if (options.values.any { it.isEmpty() }) return null
        data class Partial(val meals: List<DailyPlanMeal>, val score: Double, val allocationPenalty: Double) {
            val kcal get() = meals.sumOf { it.kcal }
            val explicitMatches get() = meals.count { it.preferenceMatched }
        }
        fun tier(kcal: Int): Int = when {
            abs(kcal - target).toDouble() / target <= 0.05 -> 0
            abs(kcal - target).toDouble() / target <= 0.10 -> 1
            else -> 2
        }
        var beam = listOf(Partial(locked.values.toList(), 0.0, 0.0))
        for ((index, meal) in unlocked.withIndex()) {
            val future = unlocked.drop(index + 1)
            val minimum = future.sumOf { options.getValue(it).minOf { o -> o.meal.kcal } }
            val maximum = future.sumOf { options.getValue(it).maxOf { o -> o.meal.kcal } }
            val expanded = beam.flatMap { partial -> options.getValue(meal).mapNotNull { option ->
                if (partial.meals.any { it.templateId == option.meal.templateId ||
                        MealRecommendationEngine.normalizeFoodName(it.name) == MealRecommendationEngine.normalizeFoodName(option.meal.name) }) null
                else Partial(partial.meals + option.meal, partial.score + option.score, partial.allocationPenalty + option.allocationPenalty)
            } }
            fun projectedError(partial: Partial): Int = when {
                target < partial.kcal + minimum -> partial.kcal + minimum - target
                target > partial.kcal + maximum -> target - partial.kcal - maximum
                else -> 0
            }
            val comparison = if (future.isEmpty()) compareByDescending<Partial> { it.explicitMatches }
                .thenByDescending { if (theme == DailyRecommendationTheme.SLOW_AGING_STYLE) SlowAgingStylePolicy.dailyScore(it.meals) else 0.0 }
                .thenBy { tier(it.kcal) }
                .thenBy { abs(it.kcal - target) }
            else compareByDescending<Partial> { it.explicitMatches }.thenBy { projectedError(it) }
            beam = expanded.sortedWith(comparison.thenBy { it.allocationPenalty }
                .thenByDescending { it.explicitMatches }.thenByDescending { it.score }.thenBy { abs(it.kcal + future.sumOf { next -> remaining * shares.getValue(next) / shareTotal } - target) }
                .thenBy { it.meals.joinToString { m -> m.templateId.orEmpty() } }).take(BEAM)
            if (beam.isEmpty()) return null
        }
        return beam.map { partial ->
            TodayMealPlan(date, theme, target, slots.map { slot -> partial.meals.first { it.mealType == slot } }, reasons = buildList {
                add("현재 하루 목표 ${target} kcal 기준")
                add("${theme.label} 테마")
                if (dislikes.isNotEmpty()) add("피하고 싶은 음식·재료 ${dislikes.size}개 제외")
                if (partial.meals.any { it.preferenceMatched }) add("좋아하는 음식 취향을 우선했어요.")
                if (partial.meals.any { (learnedScores[it.templateId] ?: 0.0) > 0 }) add("실제 기록과 즐겨찾기를 약하게 반영했어요.")
                if (recorded.isNotEmpty()) add("이미 기록한 ${recorded.size}끼의 실제 칼로리를 반영했어요.")
                if (theme == DailyRecommendationTheme.SLOW_AGING_STYLE) {
                    add(SlowAgingStylePolicy.DESCRIPTION)
                    add(SlowAgingStylePolicy.VARIATION_NOTE)
                }
                add("음식별 정해진 양 후보에서 골랐어요. 목표 차이는 다른 메뉴 조합으로 줄여요.")
                if (theme == DailyRecommendationTheme.HEALTHY) add("식품군 정보는 요리 분류이므로 원재료 다양성을 판단하지 않았어요.")
            }) to partial
        }.filterNot { it.first.signature in excludedSignatures }
            .sortedWith(compareByDescending<Pair<TodayMealPlan, Partial>> { it.second.explicitMatches }
                .thenByDescending { if (theme == DailyRecommendationTheme.SLOW_AGING_STYLE) SlowAgingStylePolicy.dailyScore(it.first.meals) else 0.0 }
                .thenBy { tier(it.first.totalKcal) }
                .thenBy { abs(it.first.totalKcal - target) }
                .thenBy { it.second.allocationPenalty }.thenByDescending { it.second.explicitMatches }.thenByDescending { it.second.score }
                .thenBy { abs(it.first.totalKcal - target) }.thenBy { it.first.signature })
            .firstOrNull()?.first
    }
}
