package com.example.healthcare.data.repository

import androidx.room.withTransaction
import com.example.healthcare.data.dao.FoodItemDao
import com.example.healthcare.data.dao.MealCoachDao
import com.example.healthcare.data.dao.MealRecordDao
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.InMemoryRecommendationCycleStore
import com.example.healthcare.data.RecommendationCyclePolicy
import com.example.healthcare.data.RecommendationCycleStore
import com.example.healthcare.data.entity.DailyMealPlan
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.data.entity.MealTemplateIngredient
import com.example.healthcare.data.entity.PlannedMeal
import com.example.healthcare.data.entity.UserExcludedFood
import com.example.healthcare.data.entity.UserMealPreference
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.RecordSource
import com.example.healthcare.domain.MealCoachCalculator
import com.example.healthcare.domain.FoodAllergenPolicy
import com.example.healthcare.domain.MealRecommendationEngine
import com.example.healthcare.domain.RecommendationSeed
import com.example.healthcare.domain.RecommendationStage
import com.example.healthcare.domain.ScoredMealRecommendation
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MealRecommendationWithIngredients(
    val recommendation: ScoredMealRecommendation,
    val ingredients: List<MealTemplateIngredient>,
    val ingredientNames: List<String>,
    val declaredAllergens: Set<String> = emptySet(),
    val matchedAllergens: Set<String> = emptySet(),
    val ingredientInfoComplete: Boolean = true,
    val allergenInfoComplete: Boolean = true
)

data class MealRecommendationSearchResult(
    val recommendations: List<MealRecommendationWithIngredients>,
    val templateCount: Int,
    val verifiedCandidateCount: Int,
    val hasMoreCandidates: Boolean,
    val cookingMode: String,
    val singleMealDay: Boolean
)

open class MealCoachRepository(
    private val database: AppDatabase,
    private val coachDao: MealCoachDao,
    private val foodItemDao: FoodItemDao,
    private val mealRecordDao: MealRecordDao,
    private val recommendationCycleStore: RecommendationCycleStore = InMemoryRecommendationCycleStore()
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    open val preference: Flow<UserMealPreference> = coachDao.observePreference().map { it ?: defaultPreference() }
    open val excludedFoods: Flow<List<UserExcludedFood>> = coachDao.observeExcludedFoods()
    open val templateCount: Flow<Int> = coachDao.observeTemplateCount()

    open suspend fun ensureDefaultPreference(): UserMealPreference {
        val existing = coachDao.getPreference()
        if (existing != null) return existing
        val created = defaultPreference()
        coachDao.savePreference(created)
        return created
    }

    open suspend fun savePreference(preference: UserMealPreference) {
        require(MealCoachCalculator.validateRatios(preference)) { "활성 식사의 배분 합계는 100%여야 합니다." }
        coachDao.savePreference(preference.copy(updatedAt = System.currentTimeMillis()))
    }

    open suspend fun addExcludedFood(name: String, type: String) {
        val normalized = if (type == "ALLERGY") {
            requireNotNull(FoodAllergenPolicy.canonicalize(name)) {
                "지원하는 알레르기 원인을 선택해 주세요."
            }
        } else {
            MealRecommendationEngine.normalizeFoodName(name)
        }
        require(normalized.isNotBlank()) { "제외할 음식이나 재료 이름이 필요합니다." }
        coachDao.addExcludedFood(
            UserExcludedFood(
                normalizedFoodName = normalized,
                exclusionType = type,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    open suspend fun deleteExcludedFood(food: UserExcludedFood) = coachDao.deleteExcludedFood(food)

    open suspend fun ensureDailyPlan(
        localDate: LocalDate,
        targetCalories: Int,
        preference: UserMealPreference
    ): Pair<DailyMealPlan, List<PlannedMeal>> = database.withTransaction {
        val date = localDate.format(dateFormatter)
        var plan = coachDao.getDailyPlan(date)
        if (plan == null) {
            val now = System.currentTimeMillis()
            val newPlan = DailyMealPlan(
                localDate = date,
                targetKcalSnapshot = targetCalories,
                planStatus = "ACTIVE",
                createdAt = now,
                updatedAt = now
            )
            val id = coachDao.insertDailyPlan(newPlan)
            plan = if (id > 0) newPlan.copy(id = id) else requireNotNull(coachDao.getDailyPlan(date))
            val meals = MealCoachCalculator.allocate(targetCalories, preference).map { budget ->
                PlannedMeal(
                    dailyMealPlanId = plan.id,
                    mealType = budget.mealType.name,
                    plannedKcal = budget.calories,
                    status = "PLANNED",
                    createdAt = now,
                    updatedAt = now
                )
            }
            coachDao.savePlannedMeals(meals)
        }
        requireNotNull(plan) to coachDao.getPlannedMeals(requireNotNull(plan).id)
    }

    open suspend fun recommendations(
        mealType: MealType,
        budgetKcal: Int,
        limit: Int = 3
    ): List<MealRecommendationWithIngredients> = searchRecommendations(
        mealType, budgetKcal, RecommendationStage.EXACT, limit = limit
    ).recommendations

    /**
     * 홈 미리보기에 사용할 당일 추천을 가져옵니다. 이미 선택된 식단이 있으면 그대로
     * 재사용하고, 없을 때만 추천 엔진을 한 번 실행해 기존 planned_meals에 저장합니다.
     */
    open suspend fun getOrCreateTodayRecommendation(
        localDate: LocalDate,
        targetCalories: Int,
        preference: UserMealPreference,
        mealType: MealType,
        budgetKcal: Int
    ): com.example.healthcare.data.entity.MealTemplate? {
        val (_, plannedMeals) = ensureDailyPlan(localDate, targetCalories, preference)
        val planned = plannedMeals.firstOrNull { it.mealType == mealType.name } ?: return null
        planned.selectedTemplateId?.let { selectedId ->
            coachDao.getTemplate(selectedId)?.let { return it }
        }
        val template = listOf(
            RecommendationStage.EXACT,
            RecommendationStage.WIDER_CALORIES,
            RecommendationStage.CLOSEST_VERIFIED
        ).firstNotNullOfOrNull { stage ->
            searchRecommendations(mealType, budgetKcal, stage, limit = 1)
                .recommendations.firstOrNull()?.recommendation?.template
        } ?: return null
        selectTemplate(planned.id, template.id)
        return template
    }

    open suspend fun searchRecommendations(
        mealType: MealType,
        budgetKcal: Int,
        stage: RecommendationStage,
        extraCookingMode: String? = null,
        excludedTemplateIds: Set<String> = emptySet(),
        limit: Int = 3
    ): MealRecommendationSearchResult {
        val preference = ensureDefaultPreference()
        val excluded = coachDao.getExcludedFoods()
        val allergy = excluded.filter { it.exclusionType == "ALLERGY" }.map { it.normalizedFoodName }.toSet()
        val otherExcluded = excluded.filter { it.exclusionType != "ALLERGY" }.map { it.normalizedFoodName }.toSet()
        val preferred = preference.preferredFoods.split('|').filter(String::isNotBlank).toSet()
        val recentUseCounts = coachDao.getRecentConsumedTemplateIds()
            .groupingBy { it }
            .eachCount()

        val templates = coachDao.getTemplatesForMeal(mealType.name)
        val ingredientsByTemplate = templates.associate { template ->
            template.id to coachDao.getIngredients(template.id)
        }
        val foodsByTemplate = ingredientsByTemplate.mapValues { (_, ingredients) ->
            ingredients.map { foodItemDao.findById(it.foodItemId) }
        }
        val namesByTemplate = foodsByTemplate.mapValues { (_, foods) ->
            foods.mapNotNull { it?.name }
        }
        val seeds = templates.map { template ->
                val ingredients = ingredientsByTemplate[template.id].orEmpty()
                val foods = foodsByTemplate[template.id].orEmpty()
                val verified = ingredients.isNotEmpty() && ingredients.size == foods.size &&
                    ingredients.zip(foods).all { (ingredient, food) ->
                        food != null && ingredient.amount > 0 && food.referenceAmount > 0 &&
                            food.energyKcal > 0 && ingredient.unit.equals(food.unit, ignoreCase = true)
                }
                val verifiedCalories = if (verified) ingredients.zip(foods).sumOf { (ingredient, food) ->
                    NutritionRepository.calculateCalories(requireNotNull(food), ingredient.amount)
                } else 0
                RecommendationSeed(
                    template = template.copy(totalKcal = verifiedCalories),
                    ingredientNames = namesByTemplate[template.id].orEmpty().toSet(),
                    allergenTags = template.allergens.split('|').filter(String::isNotBlank).toSet(),
                    ingredientInfoComplete = MealRecommendationEngine.hasCompleteIngredientInfo(template.tags),
                    recentUseCount = recentUseCounts[template.id] ?: 0,
                    dataCompleteness = if (verified && verifiedCalories > 0) 1.0 else 0.0
                )
            }
        val allScored = MealRecommendationEngine.recommend(
            candidates = seeds,
            budgetKcal = budgetKcal,
            excludedNames = otherExcluded,
            allergyNames = allergy,
            preferredNames = preferred,
            dietType = preference.dietType,
            cookingMode = preference.cookingMode,
            budgetLevel = preference.budgetLevel,
            recommendationDiversity = preference.recommendationDiversity,
            limit = Int.MAX_VALUE,
            stage = stage,
            allowedCookingModes = if (extraCookingMode == null) setOf(preference.cookingMode)
                else setOf(preference.cookingMode, extraCookingMode),
            excludedTemplateIds = excludedTemplateIds
        )
        val scope = mealType.name
        val cycleSelection = RecommendationCyclePolicy.select(
            eligibleTemplateIds = allScored.map { it.template.id },
            snapshot = recommendationCycleStore.read(scope),
            limit = limit
        )
        if (allScored.isNotEmpty()) {
            recommendationCycleStore.write(scope, cycleSelection.updatedSnapshot)
        }
        val selectedIds = cycleSelection.selectedTemplateIds.toSet()
        val bundles = allScored.filter { it.template.id in selectedIds }.map { recommendation ->
            val seed = seeds.first { it.template.id == recommendation.template.id }
            val allergenTokens = seed.allergenTags.filter(String::isNotBlank)
            MealRecommendationWithIngredients(
                recommendation = recommendation,
                ingredients = ingredientsByTemplate[recommendation.template.id].orEmpty(),
                ingredientNames = namesByTemplate[recommendation.template.id].orEmpty(),
                declaredAllergens = allergenTokens.filterNot { it.equals("UNKNOWN", ignoreCase = true) }.toSet(),
                matchedAllergens = MealRecommendationEngine.matchedAllergens(seed, allergy),
                ingredientInfoComplete = seed.ingredientInfoComplete,
                allergenInfoComplete = allergenTokens.none { it.equals("UNKNOWN", ignoreCase = true) }
            )
        }
        return MealRecommendationSearchResult(
            recommendations = bundles,
            templateCount = coachDao.templateCount(),
            verifiedCandidateCount = seeds.count {
                MealRecommendationEngine.passesHardFilters(it, otherExcluded, allergy, preference.dietType)
            },
            hasMoreCandidates = cycleSelection.hasMore,
            cookingMode = preference.cookingMode,
            singleMealDay = MealCoachCalculator.enabledRatios(preference).size == 1
        )
    }

    open suspend fun selectTemplate(plannedMealId: Long, templateId: String) {
        val meal = requireNotNull(coachDao.getPlannedMeal(plannedMealId))
        check(meal.status != "CONSUMED") { "이미 기록한 추천 식사입니다." }
        coachDao.updatePlannedMeal(
            meal.copy(selectedTemplateId = templateId, status = "SELECTED", updatedAt = System.currentTimeMillis())
        )
    }

    /** 추천 식단을 한 건의 기존 MealRecord로 저장하며 전체 트랜잭션으로 중복을 방지합니다. */
    open suspend fun confirmConsumed(
        plannedMealId: Long,
        date: LocalDate,
        time: LocalTime,
        finalCalories: Int,
        nutrition: Macronutrients = Macronutrients.Unknown,
        confirmedName: String? = null,
        portionLabel: String? = null,
        portionRatio: Double? = null
    ): Long = database.withTransaction {
        require(finalCalories > 0) { "최종 칼로리는 0보다 커야 합니다." }
        val planned = requireNotNull(coachDao.getPlannedMeal(plannedMealId))
        check(planned.status != "CONSUMED") { "이미 기록한 추천 식사입니다." }
        val alreadyRecorded = database.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM meal_records WHERE plannedMealId = ?",
            arrayOf(plannedMealId)
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) > 0
        }
        check(!alreadyRecorded) { "이미 기록한 추천 식사입니다." }
        val template = requireNotNull(planned.selectedTemplateId?.let { coachDao.getTemplate(it) })
        val now = System.currentTimeMillis()
        mealRecordDao.insertMeal(
            MealRecord(
                date = date.format(dateFormatter),
                time = time.format(timeFormatter),
                mealType = MealType.valueOf(planned.mealType),
                foodName = confirmedName?.trim()?.takeIf(String::isNotBlank) ?: template.name,
                calories = finalCalories,
                carbohydrateGrams = nutrition.carbohydrateGrams,
                proteinGrams = nutrition.proteinGrams,
                fatGrams = nutrition.fatGrams,
                memo = "추천 식단에서 확인 후 기록",
                servingAmount = portionRatio,
                servingUnit = portionRatio?.let { "추천 분량" },
                portionDisplayLabel = portionLabel,
                portionEstimationType = portionLabel?.let { "VISUAL_ESTIMATE" },
                portionSourceReference = portionLabel?.let { "선택한 추천 식단의 사용자 확인 비율" },
                source = RecordSource.RECOMMENDATION,
                plannedMealId = plannedMealId,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        coachDao.updatePlannedMeal(planned.copy(status = "CONSUMED", consumedAt = now, updatedAt = now))
        plannedMealId
    }

    companion object {
        fun defaultPreference(now: Long = System.currentTimeMillis()) = UserMealPreference(
            createdAt = now,
            updatedAt = now
        )
    }
}
