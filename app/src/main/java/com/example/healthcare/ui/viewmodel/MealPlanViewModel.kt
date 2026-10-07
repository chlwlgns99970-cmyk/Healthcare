package com.example.healthcare.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.data.repository.MealRecommendationWithIngredients
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.domain.RecommendationServingPolicy
import com.example.healthcare.domain.RecommendationServing
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.FoodAmountPolicy
import com.example.healthcare.domain.FoodAmountUnit
import com.example.healthcare.domain.FoodMetadataPolicy
import com.example.healthcare.domain.AllergyNoticePolicy
import com.example.healthcare.domain.RecordedAmountSnapshot
import com.example.healthcare.domain.PortionEstimationType
import com.example.healthcare.domain.PortionQuality
import com.example.healthcare.domain.RecommendationStage
import com.example.healthcare.domain.MealRecommendationTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
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
    val consumedRatio: Double = 1.0,
    val quantityInput: String? = null,
    val quantityUnit: String? = null,
    val quantityError: String? = null
) {
    val amountChoice: FoodAmountUnit? get() = FoodAmountPolicy.choices(foodItem)
        .firstOrNull { it.unit == quantityUnit } ?: FoodAmountPolicy.defaultChoice(foodItem)
    val displayedQuantity: String get() = quantityInput ?: amountChoice?.let {
        RecordedAmountSnapshot.format(kotlin.math.round(amount / it.basisAmountPerUnit * 1000000.0) / 1000000.0)
    }.orEmpty()
    val displayedUnit: String get() = amountChoice?.unit.orEmpty()
    val amountValid: Boolean get() = quantityError == null &&
        FoodAmountPolicy.canonicalUnit(unit) == FoodAmountPolicy.canonicalUnit(foodItem.unit) &&
        FoodAmountPolicy.parseAmount(displayedQuantity)?.let {
            FoodAmountPolicy.calculate(foodItem, it, displayedUnit)
        } != null
    val consumedAmount get() = if (amountValid && consumedRatio > 0.0) {
        FoodAmountPolicy.parseAmount(displayedQuantity)?.let {
            FoodAmountPolicy.calculate(foodItem, it * consumedRatio, displayedUnit)
        }
    } else null
    val nutrition: Macronutrients
        get() = consumedAmount?.let { Macronutrients(it.carbohydrateGrams, it.proteinGrams, it.fatGrams) }
            ?: Macronutrients.Unknown

    fun withQuantity(value: String): RecommendedIngredientUi {
        val result = FoodAmountPolicy.parseAmount(value)?.let { FoodAmountPolicy.calculate(foodItem, it, displayedUnit) }
        return if (result == null) copy(quantityInput = value,
            quantityError = "0보다 큰 유효한 먹은 양을 입력해 주세요.")
        else copy(quantityInput = value, quantityUnit = displayedUnit, quantityError = null,
            amount = result.basisAmount, unit = result.basisUnit, calories = result.calories)
    }

    fun withQuantityUnit(value: String): RecommendedIngredientUi {
        val choice = FoodAmountPolicy.choices(foodItem).firstOrNull { it.unit == value } ?: return this
        if (quantityError != null) return copy(quantityUnit = value).withQuantity(displayedQuantity)
        return copy(quantityUnit = value).withQuantity(RecordedAmountSnapshot.format(amount / choice.basisAmountPerUnit))
    }

    fun consumedSnapshot(): RecordedAmountSnapshot? = consumedAmount?.let { result ->
        amountChoice?.let { choice -> RecordedAmountSnapshot(result.basisAmount / choice.basisAmountPerUnit,
            choice.unit, choice.basisAmountPerUnit, result.basisUnit) }
    }
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
    val portionRatio: Double = 1.0,
    val servingOptions: List<RecommendationServing> = emptyList()
) {
    val consumedCalories: Int get() = ingredients.filter(RecommendedIngredientUi::included)
        .sumOf { it.consumedAmount?.calories ?: 0 }
    val consumedNutrition: Macronutrients
        get() = Macronutrients.knownSum(
            ingredients.filter(RecommendedIngredientUi::included).map(RecommendedIngredientUi::nutrition)
        )
}

/** A changed composition cannot retain the previous meal's complete label or allergy matches. */
internal fun SelectedMealUi.withIngredientEvidence(changed: List<RecommendedIngredientUi>,
    configuredAllergies: Set<String>): SelectedMealUi {
    val included = changed.filter(RecommendedIngredientUi::included)
    val tags = FoodMetadataPolicy.allergenTags(emptySet(), included.map { it.foodItem.id })
    val complete = "UNKNOWN" !in tags
    val declared = tags - "UNKNOWN"
    return copy(ingredients = changed, totalCalories = included.sumOf { it.calories },
        declaredAllergens = declared,
        matchedAllergens = AllergyNoticePolicy.resolve(configuredAllergies, declared, complete, false).matchedCauses,
        ingredientInfoComplete = included.isNotEmpty() && included.all {
            FoodMetadataPolicy.lookup(it.foodItem.id)?.ingredientInfoComplete == true },
        allergenInfoComplete = complete)
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
    val selectedTheme: MealRecommendationTheme? = null,
    val themeCounts: Map<MealRecommendationTheme, Int> = emptyMap(),
    val recommendationPortionLabels: Map<String, List<String>> = emptyMap(),
    val selectedMeal: SelectedMealUi? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val showConsumptionConfirm: Boolean = false,
    val foodSpecificConsumption: Boolean = false,
    val consumedRatio: Double = 1.0,
    val message: String? = null,
    val configuredAllergies: Set<String> = emptySet()
)

class MealPlanViewModel(
    private val mealCoachRepository: MealCoachRepository,
    private val nutritionRepository: NutritionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MealPlanUiState())
    val uiState: StateFlow<MealPlanUiState> = _uiState.asStateFlow()
    private val saveCompleted = Channel<MealRecord>(Channel.BUFFERED)
    val saveCompletedEvents = saveCompleted.receiveAsFlow()
    private var initialSelection: SelectedMealUi? = null
    val hasUnsavedInput: Boolean get() {
        val selected = _uiState.value.selectedMeal ?: return false
        val initial = initialSelection ?: return false
        return selected.ingredients.size != initial.ingredients.size ||
            (!_uiState.value.foodSpecificConsumption && _uiState.value.consumedRatio != 1.0) ||
            selected.ingredients.zip(initial.ingredients).any { (now, before) ->
            now.foodItem.id != before.foodItem.id || now.included != before.included ||
                now.displayedUnit != before.displayedUnit ||
                kotlin.math.abs(now.amount - before.amount) > 0.00001 || now.quantityError != null ||
                now.consumedRatio != before.consumedRatio
        }
    }

    fun resetToRoot() {
        if (_uiState.value.isSaving) return
        initialSelection = null
        dailyDetailRequest = null
        _uiState.update { it.copy(selectedMeal = null, saved = false, showConsumptionConfirm = false,
            foodSpecificConsumption = false, consumedRatio = 1.0, message = null) }
    }

    private var conditionsChangedDuringLoad = false
    private var pendingLoad: (() -> Unit)? = null
    private var dailyDetailRequest: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            var previous: Any? = null
            combine(mealCoachRepository.preference, mealCoachRepository.excludedFoods) { preference, excluded ->
                preference to excluded
            }.collect { conditions ->
                _uiState.update { it.copy(configuredAllergies = conditions.second
                    .filter { food -> food.exclusionType == "ALLERGY" }
                    .map { food -> food.normalizedFoodName }.toSet()) }
                val changed = previous != null && previous != conditions
                previous = conditions
                if (changed) {
                    dailyDetailRequest?.let { request ->
                        _uiState.update { it.copy(selectedMeal = null, showConsumptionConfirm = false) }
                        request()
                        return@collect
                    }
                    if (_uiState.value.isLoading) conditionsChangedDuringLoad = true
                    else if (_uiState.value.hasLoaded) {
                        _uiState.update { it.copy(selectedMeal = null, showConsumptionConfirm = false) }
                        loadStage(stableDate = LocalDate.now())
                    }
                }
            }
        }
    }

    fun load(
        mealType: MealType,
        budgetKcal: Int,
        targetKcal: Int,
        dailyRemainingKcal: Int = targetKcal,
        initialTemplateId: String? = null
    ) {
        dailyDetailRequest = null
        val current = _uiState.value
        if (current.isLoading) {
            // A Home card can be opened while a theme page is still loading. Keep the latest request.
            pendingLoad = { load(mealType, budgetKcal, targetKcal, dailyRemainingKcal, initialTemplateId) }
            return
        }
        val needsHomePage = initialTemplateId != null && (current.selectedTheme != null ||
            current.recommendations.none { it.recommendation.template.id == initialTemplateId })
        if (current.hasLoaded && current.mealType == mealType && current.budgetKcal == budgetKcal &&
            current.targetKcal == targetKcal && !needsHomePage) {
            _uiState.update { it.copy(dailyRemainingKcal = dailyRemainingKcal) }
            initialTemplateId?.let { id ->
                current.recommendations.indexOfFirst { it.recommendation.template.id == id }
                    .takeIf { it >= 0 }?.let(::selectRecommendation)
            }
            return
        }
        _uiState.value = MealPlanUiState(configuredAllergies = _uiState.value.configuredAllergies,
            mealType = mealType, budgetKcal = budgetKcal,
            targetKcal = targetKcal, dailyRemainingKcal = dailyRemainingKcal)
        if (budgetKcal <= 0 || targetKcal <= 0) {
            _uiState.update { it.copy(hasLoaded = true, loadError = true,
                message = "유효한 식사 예산이 없습니다. 목표와 식사 배분 설정을 확인해 주세요.") }
            return
        }
        loadStage(stableDate = LocalDate.now(), initialTemplateId = initialTemplateId)
    }

    /** Opens the exact standard-portion item selected by the day planner, without selecting a legacy page. */
    fun loadDailyDetail(templateId: String, mealType: MealType, kcal: Int, targetKcal: Int, portion: Double = 1.0) {
        dailyDetailRequest = { loadDailyDetail(templateId, mealType, kcal, targetKcal, portion) }
        _uiState.value = MealPlanUiState(configuredAllergies = _uiState.value.configuredAllergies,
            mealType = mealType, budgetKcal = kcal, targetKcal = targetKcal,
            dailyRemainingKcal = targetKcal, isLoading = true)
        viewModelScope.launch {
            val bundle = runCatching { mealCoachRepository.dailyPlanDetail(templateId, mealType, kcal)?.let {
                it.copy(recommendation = it.recommendation.copy(portion = portion))
            } }
            _uiState.update { it.copy(isLoading = false, hasLoaded = true,
                recommendations = listOfNotNull(bundle.getOrNull()), loadError = bundle.isFailure || bundle.getOrNull() == null,
                message = if (bundle.getOrNull() == null) "현재 조건에 맞는 메뉴가 없어요." else null) }
            if (bundle.getOrNull() != null) selectRecommendation(0)
        }
    }

    private fun loadStage(
        keepCurrentWhenEmpty: Boolean = false,
        stableDate: LocalDate? = null,
        initialTemplateId: String? = null
    ) {
        val state = _uiState.value
        if (state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = false, message = null) }
            val results = runCatching {
                if (state.selectedTheme != null) {
                    mealCoachRepository.getOrCreateThemeRecommendations(
                        stableDate ?: LocalDate.now(), state.mealType, state.budgetKcal,
                        state.selectedTheme, RECOMMENDATION_PAGE_SIZE
                    )
                } else if (stableDate != null) {
                    mealCoachRepository.getOrCreateTodayRecommendations(
                        localDate = stableDate,
                        mealType = state.mealType,
                        budgetKcal = state.budgetKcal,
                        limit = RECOMMENDATION_PAGE_SIZE
                    )
                } else {
                    mealCoachRepository.searchRecommendations(
                        mealType = state.mealType,
                        budgetKcal = state.budgetKcal,
                        stage = state.stage,
                        extraCookingMode = state.extraCookingMode,
                        limit = RECOMMENDATION_PAGE_SIZE
                    )
                }
            }
            val themeCounts = runCatching {
                mealCoachRepository.recommendationThemeCounts(state.mealType, state.budgetKcal)
            }.getOrDefault(emptyMap())
            val portionLabels = mutableMapOf<String, List<String>>()
            val page = results.getOrNull()?.recommendations.orEmpty()
            val keepCurrent = results.isSuccess && keepCurrentWhenEmpty &&
                page.isEmpty() && state.recommendations.isNotEmpty()
            page.forEach { bundle ->
                portionLabels[bundle.recommendation.template.id] = bundle.ingredients.mapNotNull { ingredient ->
                    nutritionRepository.findById(ingredient.foodItemId)?.let { food ->
                        "${FoodSearchPolicy.displayName(food)} · ${RecommendationServingPolicy.label(food, ingredient.amount * bundle.recommendation.portion)}"
                    }
                }
            }
            _uiState.update {
                it.copy(
                    recommendations = if (keepCurrent) state.recommendations else page,
                    themeCounts = themeCounts,
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
            val nextLoad = pendingLoad
            pendingLoad = null
            if (nextLoad != null) {
                if (conditionsChangedDuringLoad) {
                    // Do not let the cached page bypass a preference/dislike change during this load.
                    conditionsChangedDuringLoad = false
                    _uiState.update { it.copy(hasLoaded = false, selectedMeal = null, showConsumptionConfirm = false) }
                }
                nextLoad()
                return@launch
            }
            initialTemplateId?.let { templateId ->
                page.indexOfFirst { it.recommendation.template.id == templateId }
                    .takeIf { it >= 0 }
                    ?.let(::selectRecommendation)
            }
            if (conditionsChangedDuringLoad) {
                conditionsChangedDuringLoad = false
                _uiState.update { it.copy(selectedMeal = null, showConsumptionConfirm = false) }
                loadStage(stableDate = LocalDate.now())
            }
        }
    }

    fun selectTheme(theme: MealRecommendationTheme) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(selectedTheme = theme, stage = RecommendationStage.EXACT,
            selectedMeal = null, recommendations = emptyList(), showConsumptionConfirm = false) }
        loadStage(stableDate = LocalDate.now())
    }

    fun showTodayRecommendations() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(selectedTheme = null, stage = RecommendationStage.EXACT,
            selectedMeal = null, recommendations = emptyList(), showConsumptionConfirm = false) }
        loadStage(stableDate = LocalDate.now())
    }

    fun refresh() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.loadError) {
            if (state.budgetKcal > 0 && state.targetKcal > 0) loadStage()
            return
        }
        val replacedId = state.selectedMeal?.templateId
            ?: state.recommendations.firstOrNull()?.recommendation?.template?.id
        replacedId?.let(mealCoachRepository::recordRecommendationReplacement)
        _uiState.update { it.copy(selectedMeal = null, showConsumptionConfirm = false, foodSpecificConsumption = false) }
        loadStage(keepCurrentWhenEmpty = !state.hasMore)
    }

    fun advanceFallback() {
        val state = _uiState.value
        if (state.isLoading || state.loadError || state.selectedTheme != null) return
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
            val sourceFoods = bundle.ingredients.map { nutritionRepository.findById(it.foodItemId) }
            val options = RecommendationServingPolicy.options(bundle.ingredients, sourceFoods, _uiState.value.mealType)
            val serving = options.firstOrNull { kotlin.math.abs(it.portion - bundle.recommendation.portion) < 0.000001 }
                ?: options.minByOrNull { kotlin.math.abs(it.kcal - bundle.recommendation.template.totalKcal) }
                ?: return@launch
            val foods = bundle.ingredients.mapNotNull { ingredient ->
                nutritionRepository.findById(ingredient.foodItemId)?.let { food ->
                    RecommendedIngredientUi(
                        ingredientId = ingredient.id,
                        foodItem = food,
                        baseAmount = ingredient.amount,
                        amount = ingredient.amount * serving.portion,
                        unit = ingredient.unit,
                        calories = NutritionRepository.calculateCalories(food, ingredient.amount * serving.portion),
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
                        allergenInfoComplete = bundle.allergenInfoComplete,
                        portionRatio = serving.portion, servingOptions = options
                    ),
                    message = null,
                    showConsumptionConfirm = false,
                    foodSpecificConsumption = false,
                    consumedRatio = 1.0
                )
            }
            initialSelection = _uiState.value.selectedMeal
        }
    }

    fun adjustPortion(ratio: Double) {
        if (_uiState.value.isSaving || _uiState.value.saved) return
        _uiState.update { state ->
            val selected = state.selectedMeal ?: return@update state
            if (selected.servingOptions.none { kotlin.math.abs(it.portion - ratio) < 0.000001 }) return@update state
            val adjusted = selected.ingredients.map { ingredient ->
                val amount = ingredient.baseAmount * ratio
                ingredient.copy(amount = amount, calories = NutritionRepository.calculateCalories(ingredient.foodItem, amount),
                    quantityInput = null, quantityError = null)
            }
            state.copy(selectedMeal = selected.copy(ingredients = adjusted,
                totalCalories = adjusted.filter(RecommendedIngredientUi::included).sumOf { it.calories }, portionRatio = ratio))
        }
    }

    fun setIngredientIncluded(ingredientId: Long, included: Boolean) {
        _uiState.update { state ->
            val selected = state.selectedMeal ?: return@update state
            val changed = selected.ingredients.map { ingredient ->
                if (ingredient.ingredientId == ingredientId) ingredient.copy(included = included) else ingredient
            }
            state.copy(
                selectedMeal = selected.withIngredientEvidence(changed, state.configuredAllergies),
                message = null
            )
        }
    }

    fun setIngredientQuantity(ingredientId: Long, value: String) = changeIngredientAmount(ingredientId) { it.withQuantity(value) }

    fun setIngredientQuantityUnit(ingredientId: Long, value: String) = changeIngredientAmount(ingredientId) { it.withQuantityUnit(value) }

    private fun changeIngredientAmount(ingredientId: Long, change: (RecommendedIngredientUi) -> RecommendedIngredientUi) {
        if (_uiState.value.isSaving || _uiState.value.saved) return
        _uiState.update { state ->
            val meal = state.selectedMeal ?: return@update state
            val ingredients = meal.ingredients.map { if (it.ingredientId == ingredientId) change(it) else it }
            state.copy(selectedMeal = meal.copy(ingredients = ingredients,
                totalCalories = ingredients.filter(RecommendedIngredientUi::included).sumOf { it.calories }), message = null)
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
                        showReplacements = false,
                        quantityInput = null,
                        quantityUnit = null,
                        quantityError = null
                    )
                }
            }
            state.copy(
                selectedMeal = selected.withIngredientEvidence(changed, state.configuredAllergies)
            )
        }
    }

    fun requestConsumptionConfirmation() {
        if (_uiState.value.selectedMeal == null || _uiState.value.saved) return
        _uiState.update { it.copy(showConsumptionConfirm = true, foodSpecificConsumption = false, consumedRatio = 1.0) }
    }

    /** The default recommendation amount is already explicit, so it records in one action. */
    fun recordRecommendedAmount() {
        requestConsumptionConfirmation()
        confirmConsumed()
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
        if (includedIngredients.any { !it.amountValid }) {
            _uiState.update { it.copy(message = "선택한 음식의 먹은 양을 확인해 주세요.") }
            return
        }
        if (includedIngredients.isEmpty() || selected.consumedCalories <= 0) {
            _uiState.update { it.copy(message = "실제로 먹은 항목을 하나 이상 선택해 주세요.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val preference = mealCoachRepository.ensureDefaultPreference()
                val planned = if (dailyDetailRequest != null) {
                    mealCoachRepository.ensureDailyConsumptionSlot(LocalDate.now(), state.targetKcal, state.mealType, selected.totalCalories)
                } else {
                    val (_, plannedMeals) = mealCoachRepository.ensureDailyPlan(LocalDate.now(), state.targetKcal, preference)
                    requireNotNull(plannedMeals.firstOrNull { it.mealType == state.mealType.name })
                }
                mealCoachRepository.selectTemplate(planned.id, selected.templateId)
                val consumedIngredients = includedIngredients.filter { it.consumedRatio > 0 }
                val single = consumedIngredients.singleOrNull()
                val amountLabels = consumedIngredients.joinToString(" + ") { ingredient ->
                    "${FoodSearchPolicy.displayName(ingredient.foodItem)} ${ingredient.consumedSnapshot()?.label.orEmpty()}"
                }
                mealCoachRepository.confirmConsumedRecord(
                    plannedMealId = planned.id,
                    date = LocalDate.now(),
                    time = LocalTime.now(),
                    finalCalories = selected.consumedCalories,
                    nutrition = selected.consumedNutrition,
                    confirmedName = consumedIngredients.joinToString(" + ") { it.foodItem.name }.ifBlank { selected.templateName },
                    portionRatio = if (state.foodSpecificConsumption) 1.0 else state.consumedRatio,
                    portionLabel = amountLabels + " · " + if (state.foodSpecificConsumption) "음식별 실제 먹은 비율 확인" else when (state.consumedRatio) {
                        1.0 -> "추천한 양을 다 먹었어요"
                        0.9 -> "추천한 양을 거의 다 먹었어요"
                        0.5 -> "추천한 양의 절반 정도 먹었어요"
                        else -> "추천한 양을 조금 먹었어요"
                    },
                    foodItemId = single?.foodItem?.id,
                    amountSnapshot = single?.consumedSnapshot(),
                    amountSourceReference = single?.amountChoice?.evidence,
                    amountEstimationType = single?.amountChoice?.let { choice -> when (choice.quality) {
                        PortionQuality.OFFICIAL_SERVING -> PortionEstimationType.OFFICIAL_SERVING
                        PortionQuality.VERIFIED_CONVERSION -> PortionEstimationType.HOUSEHOLD_UNIT
                        else -> PortionEstimationType.MANUAL_AMOUNT
                    } }
                )
            }.onSuccess { record ->
                _uiState.update { it.copy(
                    isSaving = false, saved = true, selectedMeal = null,
                    showConsumptionConfirm = false, foodSpecificConsumption = false,
                    consumedRatio = 1.0, message = "오늘 기록에 한 번만 저장했습니다."
                ) }
                saveCompleted.send(record)
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
