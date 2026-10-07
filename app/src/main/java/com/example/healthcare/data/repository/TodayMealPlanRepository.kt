package com.example.healthcare.data.repository

import com.example.healthcare.data.TodayMealPlanPersistence
import com.example.healthcare.data.StoredTodayPlan
import com.example.healthcare.data.StoredDailyMeal
import com.example.healthcare.data.RecommendationCycleSnapshot
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TodayMealPlanRepository(
    val store: TodayMealPlanPersistence,
    private val coach: MealCoachRepository,
    private val goals: GoalRepository,
    private val energy: EnergyProfileRepository,
    private val records: MealRepository,
    private val favorites: FoodRepository? = null
) {
    val changes = coach.preference
    val exclusions = coach.excludedFoods
    val goalChanges = goals.allGoals
    val energyChanges = energy.allProfiles
    val recordChanges = records.allMeals
    val templateChanges = coach.templateCount
    private val validatedPlan = MutableStateFlow<TodayMealPlan?>(null)
    val currentPlan = validatedPlan.asStateFlow()
    private val mutex = Mutex()

    suspend fun create(theme: DailyRecommendationTheme, alternate: Boolean = false, replace: MealType? = null,
        date: LocalDate = LocalDate.now()): TodayMealPlan? = mutex.withLock {
        val start = System.nanoTime()
        val day = date.toString()
        val goal = goals.getGoalForDate(day).first()
        val profile = energy.getProfileForDate(day).first()
        val target = DailyCalorieTarget.resolve(goal, profile) ?: run { validatedPlan.value = null; return@withLock null }
        val preference = coach.preference.first()
        val excluded = coach.excludedFoods.first()
        val dislikes = excluded.filter { it.exclusionType != "ALLERGY" }.map { it.normalizedFoodName }.toSet()
        val allergies = excluded.filter { it.exclusionType == "ALLERGY" }.map { it.normalizedFoodName }.toSet()
        val todayRecords = records.getMealsByDate(day).first()
        val seeds = withContext(Dispatchers.IO) { coach.dailyPlanSeeds() }
        val stored = store.state.value?.takeIf { it.date == day && it.theme == theme }
        if (alternate || replace != null) {
            stored?.meals?.filter { replace == null || it.mealType == replace }
                ?.forEach { coach.recordRecommendationReplacement(it.templateId) }
        }
        val learnedScores = coach.learnedScores(seeds)
        val favoriteNames = favorites?.favoriteFoods?.first()?.map { it.foodName }?.toSet().orEmpty()
        val plan = withContext(Dispatchers.Default) {
            val pools = DailyMealPlanEngine.candidates(seeds, theme, preference, dislikes, allergies)
            val recorded = DailyMealPlanEngine.recordedMeals(todayRecords)
            val smallTargetChange = stored != null && kotlin.math.abs(stored.target - target).toDouble() / stored.target <= 0.05
            val fixed = if (!alternate && stored != null && smallTargetChange) stored.meals.mapNotNull { saved ->
                if (saved.mealType == replace || saved.mealType in recorded) return@mapNotNull null
                pools[saved.mealType]?.firstOrNull { it.template.id == saved.templateId }?.let { seed ->
                    val serving = RecommendationServingPolicy.selected(seed, saved.mealType, saved.portion) ?: return@mapNotNull null
                    saved.mealType to DailyPlanMeal(saved.mealType, seed.template.id, seed.template.name, serving.kcal,
                        serving.nutrition, saved.portion,
                        matchedAllergens = MealRecommendationEngine.matchedAllergens(seed, allergies),
                        ingredientInfoComplete = seed.ingredientInfoComplete, allergenInfoComplete = "UNKNOWN" !in seed.allergenTags,
                        foodGroups = if (theme == DailyRecommendationTheme.SLOW_AGING_STYLE) SlowAgingStylePolicy.groups(seed) else seed.ingredientCategories,
                        amountLabels = serving.labels, preferenceMatched = FoodPreferencePolicy.matches(seed,
                            preference.preferredFoods.split('|').filter(String::isNotBlank).toSet()))
                }
            }.orEmpty().toMap() else emptyMap()
            val seen = DailyMealPlanEngine.slots.associateWith { meal ->
                val snapshot = coach.dailyCycleSnapshot(scope(theme, meal))
                val eligible = pools[meal].orEmpty().map { it.template.id }.toSet()
                if ((eligible - snapshot.seenTemplateIds).isEmpty()) setOfNotNull(snapshot.lastShownTemplateId)
                else snapshot.seenTemplateIds
            }
            val priorIds = if (replace != null) stored?.meals?.filter { it.mealType == replace }?.map { it.templateId }?.toSet().orEmpty() else emptySet()
            DailyMealPlanEngine.generate(day, target, theme, seeds, preference, dislikes, allergies, todayRecords,
                fixed, seen, if (alternate) stored?.signatures.orEmpty().toSet() else emptySet(), priorIds, favoriteNames, learnedScores)
        } ?: run {
            if (!alternate && replace == null && store.state.value?.theme == theme) validatedPlan.value = null
            return@withLock null
        }
        withContext(Dispatchers.IO) {
            store.save(StoredTodayPlan(day, theme, target, plan.meals.filterNot { it.recorded }.map {
                StoredDailyMeal(it.mealType, requireNotNull(it.templateId), it.portion, it.kcal)
            }, (stored?.signatures.orEmpty() + plan.signature).distinct().takeLast(24)))
        }
        val previousIds = stored?.meals.orEmpty().map { it.templateId }.toSet()
        plan.meals.filter { !it.recorded && (alternate || it.templateId !in previousIds) }.forEach { meal ->
            val id = requireNotNull(meal.templateId)
            val scope = scope(theme, meal.mealType)
            val snapshot = coach.dailyCycleSnapshot(scope)
            val eligible = DailyMealPlanEngine.candidates(seeds, theme, preference, dislikes, allergies)[meal.mealType].orEmpty().map { it.template.id }.toSet()
            val seen = if ((eligible - snapshot.seenTemplateIds).isEmpty()) emptySet() else snapshot.seenTemplateIds
            coach.saveDailyCycleSnapshot(scope, RecommendationCycleSnapshot(seen + id, id))
        }
        plan.copy(generationMillis = (System.nanoTime() - start) / 1_000_000).also { validatedPlan.value = it }
    }

    suspend fun currentTarget(date: LocalDate = LocalDate.now()): Int? = DailyCalorieTarget.resolve(
        goals.getGoalForDate(date.toString()).first(), energy.getProfileForDate(date.toString()).first())

    private fun scope(theme: DailyRecommendationTheme, meal: MealType) = "daily-plan:${theme.name}:${meal.name}"
}
