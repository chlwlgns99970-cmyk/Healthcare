package com.example.healthcare.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.data.repository.MealRecommendationWithIngredients
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.RecommendationStage
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class RecommendedIngredientUi(
    val ingredientId: Long,
    val foodItem: FoodItem,
    val baseAmount: Double,
    val amount: Double,
    val unit: String,
    val calories: Int,
    val included: Boolean = true,
    val adjustable: Boolean,
    val minimumAmount: Double?,
    val maximumAmount: Double?,
    val adjustmentStep: Double?,
    val replacements: List<FoodItem> = emptyList(),
    val showReplacements: Boolean = false,
    val consumedRatio: Double = 1.0
) {
    val nutrition: Macronutrients
        get() = Macronutrients.forFood(foodItem, amount).scaled(consumedRatio)
}

data class SelectedMealUi(
    val templateId: String,
    val templateName: String,
    val reason: String,
    val ingredients: List<RecommendedIngredientUi>,
    val totalCalories: Int,
    val declaredAllergens: Set<String> = emptySet(),
    val matchedAllergens: Set<String> = emptySet(),
    val ingredientInfoComplete: Boolean = true,
    val allergenInfoComplete: Boolean = true,
    val portionRatio: Double = 1.0
) {
    val consumedCalories: Int get() = ingredients.filter(RecommendedIngredientUi::included)
        .sumOf { (it.calories * it.consumedRatio).roundToInt() }
    val consumedNutrition: Macronutrients
        get() = Macronutrients.strictSum(
            ingredients.filter(RecommendedIngredientUi::included).map(RecommendedIngredientUi::nutrition)
        )
}

data class MealPlanUiState(
    val mealType: MealType = MealType.LUNCH,
    val budgetKcal: Int = 0,
    val targetKcal: Int = 2000,
    val dailyRemainingKcal: Int = 0,
    val stage: RecommendationStage = RecommendationStage.EXACT,
    val extraCookingMode: String? = null,
    val cookingMode: String = "ANY",
    val singleMealDay: Boolean = false,
    val templateCount: Int = 0,
    val verifiedCandidateCount: Int = 0,
    val hasLoaded: Boolean = false,
    val hasMore: Boolean = false,
    val isExhausted: Boolean = false,
    val loadError: Boolean = false,
    val recommendations: List<MealRecommendationWithIngredients> = emptyList(),
    val recommendationPortionLabels: Map<String, List<String>> = emptyMap(),
    val selectedMeal: SelectedMealUi? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val showConsumptionConfirm: Boolean = false,
    val foodSpecificConsumption: Boolean = false,
    val consumedRatio: Double = 1.0,
    val message: String? = null
)

class MealPlanViewModel(
    private val mealCoachRepository: MealCoachRepository,
    private val nutritionRepository: NutritionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MealPlanUiState())
    val uiState: StateFlow<MealPlanUiState> = _uiState.asStateFlow()
    private val saveCompleted = Channel<Unit>(Channel.BUFFERED)
    val saveCompletedEvents = saveCompleted.receiveAsFlow()

    fun load(mealType: MealType, budgetKcal: Int, targetKcal: Int, dailyRemainingKcal: Int = targetKcal) {
        val current = _uiState.value
        if (current.isLoading) return
        if (current.hasLoaded && current.mealType == mealType && current.budgetKcal == budgetKcal &&
            current.targetKcal == targetKcal) {
            _uiState.update { it.copy(dailyRemainingKcal = dailyRemainingKcal) }
            return
        }
        _uiState.value = MealPlanUiState(mealType = mealType, budgetKcal = budgetKcal,
            targetKcal = targetKcal, dailyRemainingKcal = dailyRemainingKcal)
        if (budgetKcal <= 0 || targetKcal <= 0) {
            _uiState.update { it.copy(hasLoaded = true, loadError = true,
                message = "유효한 식사 예산이 없습니다. 목표와 식사 배분 설정을 확인해 주세요.") }
            return
        }
        loadStage()
    }

    private fun loadStage(keepCurrentWhenEmpty: Boolean = false) {
        val state = _uiState.value
        if (state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = false, message = null) }
            val results = runCatching {
                mealCoachRepository.searchRecommendations(
                    mealType = state.mealType,
                    budgetKcal = state.budgetKcal,
                    stage = state.stage,
                    extraCookingMode = state.extraCookingMode,
                    limit = RECOMMENDATION_PAGE_SIZE
                )
            }
            val portionLabels = mutableMapOf<String, List<String>>()
            val page = results.getOrNull()?.recommendations.orEmpty()
            val keepCurrent = results.isSuccess && keepCurrentWhenEmpty &&
                page.isEmpty() && state.recommendations.isNotEmpty()
            page.forEach { bundle ->
                portionLabels[bundle.recommendation.template.id] = bundle.ingredients.mapNotNull { ingredient ->
                    nutritionRepository.findById(ingredient.foodItemId)?.let { food ->
                        "${FoodSearchPolicy.displayName(food)} · ${PortionGuide.recommendationLabel(food, ingredient.amount)}"
                    }
                }
            }
            _uiState.update {
                it.copy(
                    recommendations = if (keepCurrent) state.recommendations else page,
                    recommendationPortionLabels = if (keepCurrent) {
                        state.recommendationPortionLabels
                    } else {
                        portionLabels
                    },
                    isLoading = false,
                    hasLoaded = true,
                    hasMore = !keepCurrent && results.getOrNull()?.hasMoreCandidates == true,
                    isExhausted = keepCurrent || page.isEmpty(),
                    loadError = results.isFailure,
                    templateCount = results.getOrNull()?.templateCount ?: it.templateCount,
                    verifiedCandidateCount = results.getOrNull()?.verifiedCandidateCount ?: it.verifiedCandidateCount,
                    cookingMode = results.getOrNull()?.cookingMode ?: it.cookingMode,
                    singleMealDay = results.getOrNull()?.singleMealDay ?: it.singleMealDay,
                    message = when {
                        results.isFailure -> "추천 식단을 불러오지 못했습니다. 다시 시도해 주세요."
                        keepCurrent -> "현재 조건에서 다른 추천이 없어요"
                        else -> null
                    }
                )
            }
        }
    }

    fun refresh() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.loadError) {
            if (state.budgetKcal > 0 && state.targetKcal > 0) loadStage()
            return
        }
        _uiState.update { it.copy(selectedMeal = null, showConsumptionConfirm = false, foodSpecificConsumption = false) }
        loadStage(keepCurrentWhenEmpty = !state.hasMore)
    }

    fun advanceFallback() {
        val state = _uiState.value
        if (state.isLoading || state.loadError) return
        val next = when (state.stage) {
            RecommendationStage.EXACT -> RecommendationStage.WIDER_CALORIES
            RecommendationStage.WIDER_CALORIES, RecommendationStage.EXPANDED_MODE -> RecommendationStage.CLOSEST_VERIFIED
            RecommendationStage.CLOSEST_VERIFIED -> return
        }
        _uiState.update { it.copy(stage = next, recommendations = emptyList(), isExhausted = false,
            selectedMeal = null, showConsumptionConfirm = false, foodSpecificConsumption = false) }
        loadStage()
    }

    fun expandCookingMode(mode: String) {
        val state = _uiState.value
        if (state.isLoading || state.cookingMode == "ANY" || mode == state.cookingMode ||
            mode !in setOf("COOK", "DINING_OUT", "CONVENIENCE")) return
        _uiState.update { it.copy(stage = RecommendationStage.EXPANDED_MODE, extraCookingMode = mode,
            recommendations = emptyList(), isExhausted = false, selectedMeal = null) }
        loadStage()
    }

    fun selectRecommendation(index: Int) {
        val bundle = _uiState.value.recommendations.getOrNull(index) ?: return
        viewModelScope.launch {
            val foods = bundle.ingredients.mapNotNull { ingredient ->
                nutritionRepository.findById(ingredient.foodItemId)?.let { food ->
                    RecommendedIngredientUi(
                        ingredientId = ingredient.id,
                        foodItem = food,
                        baseAmount = ingredient.amount,
                        amount = ingredient.amount,
                        unit = ingredient.unit,
                        calories = NutritionRepository.calculateCalories(food, ingredient.amount),
                        adjustable = ingredient.adjustable,
                        minimumAmount = ingredient.minimumAmount,
                        maximumAmount = ingredient.maximumAmount,
                        adjustmentStep = ingredient.adjustmentStep
                    )
                }
            }
            val total = foods.sumOf { it.calories }.takeIf { it > 0 }
                ?: bundle.recommendation.template.totalKcal
            _uiState.update {
                it.copy(
                    selectedMeal = SelectedMealUi(
                        templateId = bundle.recommendation.template.id,
                        templateName = bundle.recommendation.template.name,
                        reason = bundle.recommendation.reason,
                        ingredients = foods,
                        totalCalories = total,
                        declaredAllergens = bundle.declaredAllergens,
                        matchedAllergens = bundle.matchedAllergens,
                        ingredientInfoComplete = bundle.ingredientInfoComplete,
                        allergenInfoComplete = bundle.allergenInfoComplete
                    ),
                    message = null,
                    showConsumptionConfirm = false,
                    foodSpecificConsumption = false,
                    consumedRatio = 1.0
                )
            }
        }
    }

    fun adjustPortion(ratio: Double) {
        if (ratio !in 0.5..1.5) return
        _uiState.update { state ->
            val selected = state.selectedMeal ?: return@update state
            val adjusted = selected.ingredients.map { ingredient ->
                if (!ingredient.adjustable) ingredient else {
                    val raw = ingredient.baseAmount * ratio
                    val stepped = ingredient.adjustmentStep?.takeIf { it > 0.0 }?.let { step ->
                        (raw / step).roundToInt() * step
                    } ?: raw
                    val amount = stepped
                        .coerceAtLeast(ingredient.minimumAmount ?: stepped)
                        .coerceAtMost(ingredient.maximumAmount ?: stepped)
                    ingredient.copy(
                        amount = amount,
                        calories = NutritionRepository.calculateCalories(ingredient.foodItem, amount)
                    )
                }
            }
            state.copy(
                selectedMeal = selected.copy(
                    ingredients = adjusted,
                    totalCalories = adjusted.filter(RecommendedIngredientUi::included).sumOf { it.calories },
                    portionRatio = ratio
                )
            )
        }
    }

    fun setIngredientIncluded(ingredientId: Long, included: Boolean) {
        _uiState.update { state ->
            val selected = state.selectedMeal ?: return@update state
            val changed = selected.ingredients.map { ingredient ->
                if (ingredient.ingredientId == ingredientId) ingredient.copy(included = included) else ingredient
            }
            state.copy(
                selectedMeal = selected.copy(
                    ingredients = changed,
                    totalCalories = changed.filter(RecommendedIngredientUi::included).sumOf { it.calories }
                ),
                message = null
            )
        }
    }

    fun showReplacements(ingredientId: Long) {
        val selected = _uiState.value.selectedMeal ?: return
        val ingredient = selected.ingredients.firstOrNull { it.ingredientId == ingredientId } ?: return
        viewModelScope.launch {
            val replacements = nutritionRepository.findReplacements(ingredient.foodItem)
            _uiState.update { state ->
                val meal = state.selectedMeal ?: return@update state
                state.copy(
                    selectedMeal = meal.copy(
                        ingredients = meal.ingredients.map {
                            if (it.ingredientId == ingredientId) it.copy(replacements = replacements, showReplacements = true) else it
                        }
                    ),
                    message = if (replacements.isEmpty()) "같은 분류에서 비슷한 칼로리의 교체 후보를 찾지 못했습니다." else null
                )
            }
        }
    }

    fun replaceIngredient(ingredientId: Long, food: FoodItem) {
        _uiState.update { state ->
            val selected = state.selectedMeal ?: return@update state
            val changed = selected.ingredients.map { ingredient ->
                if (ingredient.ingredientId != ingredientId) ingredient else {
                    val sameUnit = ingredient.unit.equals(food.unit, ignoreCase = true)
                    val amount = if (sameUnit) {
                        ingredient.amount
                    } else food.referenceAmount
                    ingredient.copy(
                        foodItem = food,
                        baseAmount = amount,
                        amount = amount,
                        unit = food.unit,
                        calories = NutritionRepository.calculateCalories(food, amount),
                        adjustable = ingredient.adjustable && sameUnit,
                        minimumAmount = if (sameUnit) ingredient.minimumAmount else null,
                        maximumAmount = if (sameUnit) ingredient.maximumAmount else null,
                        adjustmentStep = if (sameUnit) ingredient.adjustmentStep else null,
                        replacements = emptyList(),
                        showReplacements = false
                    )
                }
            }
            state.copy(
                selectedMeal = selected.copy(
                    ingredients = changed,
                    totalCalories = changed.filter(RecommendedIngredientUi::included).sumOf { it.calories }
                )
            )
        }
    }

    fun requestConsumptionConfirmation() {
        if (_uiState.value.selectedMeal == null || _uiState.value.saved) return
        _uiState.update { it.copy(showConsumptionConfirm = true, foodSpecificConsumption = false, consumedRatio = 1.0) }
    }

    fun setConsumedRatio(ratio: Double) {
        if (ratio !in 0.1..1.0) return
        _uiState.update { state ->
            val meal = state.selectedMeal ?: return@update state
            state.copy(
                selectedMeal = meal.copy(ingredients = meal.ingredients.map { it.copy(consumedRatio = ratio) }),
                consumedRatio = ratio,
                foodSpecificConsumption = false
            )
        }
    }

    fun chooseFoodSpecificConsumption() {
        _uiState.update { it.copy(foodSpecificConsumption = true) }
    }

    fun setIngredientConsumedRatio(ingredientId: Long, ratio: Double) {
        if (ratio !in 0.0..1.0) return
        _uiState.update { state ->
            val meal = state.selectedMeal ?: return@update state
            state.copy(selectedMeal = meal.copy(ingredients = meal.ingredients.map {
                if (it.ingredientId == ingredientId) it.copy(consumedRatio = ratio) else it
            }))
        }
    }

    fun confirmConsumed() {
        val state = _uiState.value
        val selected = state.selectedMeal ?: return
        if (state.isSaving || state.saved) return
        if (!state.showConsumptionConfirm) {
            _uiState.update { it.copy(message = "먹은 양을 먼저 확인해 주세요.") }
            return
        }
        val includedIngredients = selected.ingredients.filter(RecommendedIngredientUi::included)
        if (includedIngredients.isEmpty() || selected.consumedCalories <= 0) {
            _uiState.update { it.copy(message = "실제로 먹은 항목을 하나 이상 선택해 주세요.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val preference = mealCoachRepository.ensureDefaultPreference()
                val (_, plannedMeals) = mealCoachRepository.ensureDailyPlan(LocalDate.now(), state.targetKcal, preference)
                val planned = requireNotNull(plannedMeals.firstOrNull { it.mealType == state.mealType.name })
                mealCoachRepository.selectTemplate(planned.id, selected.templateId)
                mealCoachRepository.confirmConsumed(
                    plannedMealId = planned.id,
                    date = LocalDate.now(),
                    time = LocalTime.now(),
                    finalCalories = selected.consumedCalories,
                    nutrition = selected.consumedNutrition,
                    confirmedName = includedIngredients.joinToString(" + ") { it.foodItem.name }.ifBlank { selected.templateName },
                    portionRatio = if (state.foodSpecificConsumption) 1.0 else state.consumedRatio,
                    portionLabel = if (state.foodSpecificConsumption) "음식별 실제 먹은 비율 확인" else when (state.consumedRatio) {
                        1.0 -> "추천한 양을 다 먹었어요"
                        0.9 -> "추천한 양을 거의 다 먹었어요"
                        0.5 -> "추천한 양의 절반 정도 먹었어요"
                        else -> "추천한 양을 조금 먹었어요"
                    }
                )
            }.onSuccess {
                _uiState.update { it.copy(
                    isSaving = false, saved = true, selectedMeal = null,
                    showConsumptionConfirm = false, foodSpecificConsumption = false,
                    consumedRatio = 1.0, message = "오늘 기록에 한 번만 저장했습니다."
                ) }
                saveCompleted.send(Unit)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = error.message?.takeIf(String::isNotBlank) ?: "추천 식단을 기록하지 못했습니다."
                    )
                }
            }
        }
    }

    private companion object {
        const val RECOMMENDATION_PAGE_SIZE = 3
    }
}
