package com.example.healthcare.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.RecordSource
import com.example.healthcare.data.repository.EnergyProfileRepository
import com.example.healthcare.data.repository.GoalRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.PortionEstimationType
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.domain.PortionPreset
import com.example.healthcare.domain.EnergyBalanceCalculator
import com.example.healthcare.domain.DailyIntakeTimeline
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.domain.FoodAmountPolicy
import com.example.healthcare.domain.FoodAmountUnit
import com.example.healthcare.domain.FoodAmountNutrition
import com.example.healthcare.domain.PortionQuality
import com.example.healthcare.domain.RecordedAmountSnapshot
import com.example.healthcare.data.model.MealNutritionRow
import com.example.healthcare.util.CalorieUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class MealEditUiState(
    val original: MealRecord? = null,
    val foodName: String = "",
    val calories: String = "",
    val mealType: MealType = MealType.BREAKFAST,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val servingAmount: String = "",
    val servingUnit: String = "",
    val quantity: String = "",
    val quantityUnit: String = "",
    val caloriesManuallyEdited: Boolean = false,
    val portionPresetId: String? = null,
    val portionDisplayLabel: String? = null,
    val portionEstimationType: String? = null,
    val portionSourceReference: String? = null,
    val selectedFood: FoodItem? = null,
    val preciseAmountOpen: Boolean = false,
    val memo: String = "",
    val foodNameError: String? = null,
    val caloriesError: String? = null,
    val servingAmountError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null
) {
    val isEditing: Boolean get() = original != null
    val nutritionPreview: Macronutrients get() {
        val record = original ?: return Macronutrients.Unknown
        val nutrition = Macronutrients(record.carbohydrateGrams, record.proteinGrams, record.fatGrams)
        if (caloriesManuallyEdited) {
            val energy = CalorieUtils.parseAndRoundCalories(calories) ?: return Macronutrients.Unknown
            return if (record.calories > 0) nutrition.scaled(energy.toDouble() / record.calories) else Macronutrients.Unknown
        }
        val amount = FoodAmountPolicy.parseAmount(servingAmount) ?: return nutrition
        val originalAmount = record.servingAmount ?: return nutrition
        return FoodAmountPolicy.calculateSnapshot(originalAmount, record.servingUnit.orEmpty(), record.calories,
            nutrition, amount)?.let { Macronutrients(it.carbohydrateGrams, it.proteinGrams, it.fatGrams) } ?: nutrition
    }
    val amountChoices: List<FoodAmountUnit> get() {
        val record = original ?: return emptyList()
        val basisUnit = record.servingUnit?.takeIf(String::isNotBlank) ?: return emptyList()
        val amount = record.servingAmount?.takeIf { it.isFinite() && it > 0 } ?: return emptyList()
        val snapshot = RecordedAmountSnapshot.from(record)
        return buildList {
            snapshot?.let { add(FoodAmountUnit(it.unit, it.basisPerUnit, it.basisUnit,
                record.portionSourceReference ?: "저장 당시의 섭취 단위 기준", PortionQuality.OFFICIAL_SERVING)) }
            add(FoodAmountUnit(basisUnit, 1.0, basisUnit,
                "저장 당시 ${RecordedAmountSnapshot.format(amount)}${basisUnit}의 영양정보를 기준으로 계산해요.",
                when (basisUnit) { "g" -> PortionQuality.WEIGHT_ONLY; "ml" -> PortionQuality.VOLUME_ONLY; else -> PortionQuality.OFFICIAL_SERVING }))
        }.distinctBy(FoodAmountUnit::unit)
    }
}

/**
 * 기록 조회(히스토리) 화면의 비즈니스 로직을 담당하는 ViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val mealRepository: MealRepository,
    private val goalRepository: GoalRepository,
    private val energyProfileRepository: EnergyProfileRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val nutritionRepository: NutritionRepository? = null
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val _selectedDate = MutableStateFlow(todayProvider())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    val allMeals: StateFlow<List<MealRecord>> = mealRepository.allMeals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val intakeTimeline: StateFlow<DailyIntakeTimeline> = combine(
        mealRepository.allMeals,
        goalRepository.allGoals,
        energyProfileRepository.allProfiles
    ) { meals, goals, profiles -> DailyIntakeTimeline.build(meals, goals, profiles) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyIntakeTimeline.Empty)

    private val _editState = MutableStateFlow(MealEditUiState())
    val editState: StateFlow<MealEditUiState> = _editState.asStateFlow()

    // 해당 날짜의 식사 기록
    val dailyMeals: StateFlow<List<MealRecord>> = _selectedDate
        .flatMapLatest { date ->
            mealRepository.getMealsByDate(date.format(dateFormatter))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 해당 날짜의 총 칼로리
    val dailyTotalCalories: StateFlow<Int> = _selectedDate
        .flatMapLatest { date ->
            mealRepository.getTotalCaloriesByDate(date.format(dateFormatter))
        }
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dailyNutritionRows: StateFlow<List<MealNutritionRow>> = _selectedDate
        .flatMapLatest { date -> mealRepository.getNutritionByDate(date.format(dateFormatter)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyNutrition: StateFlow<Macronutrients> = dailyNutritionRows
        .map { rows -> Macronutrients.knownSum(rows.map { it.asMacronutrients() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Macronutrients.Unknown)

    // 해당 날짜의 목표 칼로리
    val dailyTargetCalories: StateFlow<Int> = _selectedDate
        .flatMapLatest { date ->
            val formattedDate = date.format(dateFormatter)
            combine(
                goalRepository.getGoalForDate(formattedDate),
                energyProfileRepository.getProfileForDate(formattedDate)
            ) { goal, profile ->
                val manualTarget = goal?.targetCalories ?: 2000
                if (profile == null) manualTarget else runCatching {
                    EnergyBalanceCalculator.resolveDailyTargetKcal(
                        manualTarget,
                        profile.basalMetabolicRateKcal,
                        profile.palMultiplier,
                        profile.targetMode
                    )
                }.getOrDefault(manualTarget)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000)

    // 칼로리 상태 메시지 (중립적 표현)
    val dailyCalorieStatus: StateFlow<String> = combine(dailyTotalCalories, dailyTargetCalories) { total, target ->
        com.example.healthcare.util.CalorieUtils.getCalorieStatusText(total, target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
    }

    fun deleteMeal(meal: MealRecord) {
        viewModelScope.launch {
            mealRepository.deleteMeal(meal)
        }
    }

    fun startEditing(meal: MealRecord) {
        val snapshot = RecordedAmountSnapshot.from(meal)
        _editState.value = MealEditUiState(
            original = meal,
            foodName = meal.foodName,
            calories = meal.calories.toString(),
            mealType = meal.mealType,
            date = LocalDate.parse(meal.date, dateFormatter),
            time = LocalTime.parse(meal.time, timeFormatter),
            servingAmount = meal.servingAmount?.let(::formatServing).orEmpty(),
            servingUnit = meal.servingUnit.orEmpty(),
            quantity = snapshot?.quantity?.let(::formatServing) ?: meal.servingAmount?.let(::formatServing).orEmpty(),
            quantityUnit = snapshot?.unit ?: meal.servingUnit.orEmpty(),
            portionPresetId = meal.portionPresetId,
            portionDisplayLabel = meal.portionDisplayLabel,
            portionEstimationType = meal.portionEstimationType,
            portionSourceReference = meal.portionSourceReference,
            memo = meal.memo.orEmpty()
        )
        meal.foodItemId?.let { foodId ->
            viewModelScope.launch {
                val food = nutritionRepository?.findById(foodId)
                _editState.update { state ->
                    if (state.original?.id == meal.id) state.copy(selectedFood = food) else state
                }
            }
        }
    }

    fun cancelEditing() {
        if (!_editState.value.isSaving) _editState.value = MealEditUiState()
    }

    fun onEditFoodNameChange(value: String) {
        _editState.update { it.copy(foodName = value, foodNameError = null, saveError = null) }
    }

    fun onEditCaloriesChange(value: String) {
        _editState.update { it.copy(calories = value, caloriesManuallyEdited = true, caloriesError = null, saveError = null) }
    }

    fun onEditMealTypeChange(value: MealType) {
        _editState.update { it.copy(mealType = value, saveError = null) }
    }

    fun onEditDateChange(value: LocalDate) {
        _editState.update { it.copy(date = value, saveError = null) }
    }

    fun onEditTimeChange(value: LocalTime) {
        _editState.update { it.copy(time = value, saveError = null) }
    }

    fun onEditServingAmountChange(value: String) {
        _editState.update { state ->
            val amount = FoodAmountPolicy.parseAmount(value)
            val choice = state.amountChoices.firstOrNull { it.unit == state.quantityUnit }
            val quantity = amount?.let { it / (choice?.basisAmountPerUnit ?: 1.0) }
            val changed = state.copy(servingAmount = value,
                quantity = quantity?.let(::formatServing) ?: value,
                servingAmountError = null, saveError = null)
            updateAmountSnapshot(changed, amount, quantity)
        }
    }

    fun onEditServingUnitChange(value: String) {
        val state = _editState.value
        if (state.amountChoices.any { it.unit == value }) { onEditQuantityUnitChange(value); return }
        // A manual label can be edited, but a stored mass/volume basis cannot be silently converted.
        val originalUnit = state.original?.servingUnit
        if (RecordedAmountSnapshot.from(state.original ?: return) != null ||
            originalUnit in setOf("g", "ml") || value.trim() in setOf("g", "ml") && !originalUnit.isNullOrBlank()) return
        _editState.update { it.copy(servingUnit = value, quantityUnit = value, saveError = null,
            portionPresetId = null, portionDisplayLabel = null,
            portionEstimationType = PortionEstimationType.MANUAL_AMOUNT.name, portionSourceReference = null) }
    }

    fun onEditQuantityChange(value: String) {
        _editState.update { state ->
            val quantity = FoodAmountPolicy.parseAmount(value)
            val choice = state.amountChoices.firstOrNull { it.unit == state.quantityUnit }
            val amount = quantity?.let { it * (choice?.basisAmountPerUnit ?: 1.0) }
            val changed = state.copy(quantity = value, servingAmount = amount?.let(::formatServing) ?: value,
                servingAmountError = null, saveError = null)
            updateAmountSnapshot(changed, amount, quantity)
        }
    }

    fun onEditQuantityUnitChange(unit: String) {
        _editState.update { state ->
            val choice = state.amountChoices.firstOrNull { it.unit == unit } ?: return@update state
            val amount = FoodAmountPolicy.parseAmount(state.servingAmount)
            val quantity = amount?.let { it / choice.basisAmountPerUnit }
            updateAmountSnapshot(state.copy(quantityUnit = unit, quantity = quantity?.let(::formatServing).orEmpty(),
                servingAmountError = null, saveError = null), amount, quantity)
        }
    }

    private fun snapshotNutrition(state: MealEditUiState, amount: Double?): FoodAmountNutrition? {
        val original = state.original ?: return null
        val referenceAmount = original.servingAmount ?: return null
        if (amount == null || original.servingUnit != state.servingUnit) return null
        return FoodAmountPolicy.calculateSnapshot(referenceAmount, original.servingUnit.orEmpty(), original.calories,
            Macronutrients(original.carbohydrateGrams, original.proteinGrams, original.fatGrams), amount)
    }

    private fun updateAmountSnapshot(state: MealEditUiState, amount: Double?, quantity: Double?): MealEditUiState {
        val nutrition = snapshotNutrition(state, amount)
        val original = state.original
        val portable = RecordedAmountSnapshot.from(original ?: return state)
        val choice = state.amountChoices.firstOrNull { it.unit == state.quantityUnit }
        val snapshot = if (portable != null && quantity != null && choice != null && nutrition != null)
            RecordedAmountSnapshot(quantity, choice.unit, choice.basisAmountPerUnit, choice.basisUnit) else null
        return state.copy(calories = if (!state.caloriesManuallyEdited) nutrition?.calories?.toString() ?: state.calories else state.calories,
            portionPresetId = snapshot?.presetId,
            portionDisplayLabel = snapshot?.label,
            portionEstimationType = if (snapshot != null) original.portionEstimationType else PortionEstimationType.MANUAL_AMOUNT.name,
            portionSourceReference = if (snapshot != null) original.portionSourceReference else null)
    }

    fun showPreciseEdit() {
        _editState.update { it.copy(preciseAmountOpen = true) }
    }

    fun selectEditPortion(preset: PortionPreset) {
        val food = _editState.value.selectedFood ?: return
        if (PortionGuide.presets(food).none { it.id == preset.id }) return
        if (preset.unit != _editState.value.original?.servingUnit) return
        val estimate = snapshotNutrition(_editState.value, preset.amount) ?: return
        _editState.update {
            it.copy(servingAmount = formatServing(estimate.basisAmount), servingUnit = estimate.basisUnit,
                quantity = formatServing(estimate.basisAmount), quantityUnit = estimate.basisUnit,
                calories = estimate.calories.toString(), caloriesManuallyEdited = false, portionPresetId = preset.id,
                portionDisplayLabel = preset.label, portionEstimationType = preset.estimationType.name,
                portionSourceReference = preset.sourceReference, servingAmountError = null, caloriesError = null)
        }
    }

    fun selectEditRatio(ratio: Double, label: String) {
        val original = _editState.value.original ?: return
        if (ratio !in 0.1..2.0) return
        val originalAmount = original.servingAmount?.takeIf { it > 0.0 } ?: return
        val snapshot = RecordedAmountSnapshot.from(original)
        if (snapshot != null) {
            _editState.update { it.copy(caloriesManuallyEdited = false) }
            onEditQuantityUnitChange(snapshot.unit)
            onEditQuantityChange(formatServing(snapshot.quantity * ratio))
            return
        }
        val nutrition = snapshotNutrition(_editState.value, originalAmount * ratio) ?: return
        _editState.update {
            it.copy(
                servingAmount = formatServing(originalAmount * ratio),
                quantity = formatServing(originalAmount * ratio), quantityUnit = original.servingUnit.orEmpty(),
                calories = nutrition.calories.toString(), caloriesManuallyEdited = false,
                portionPresetId = "edit-ratio-$ratio",
                portionDisplayLabel = "${original.portionDisplayLabel ?: "저장한 양"} · $label",
                portionEstimationType = PortionEstimationType.VISUAL_ESTIMATE.name,
                portionSourceReference = original.portionSourceReference ?: "기존 기록의 양",
                servingAmountError = null, caloriesError = null
            )
        }
    }

    fun onEditMemoChange(value: String) {
        _editState.update { it.copy(memo = value, saveError = null) }
    }

    fun saveMealEdit(onSuccess: (MealRecord) -> Unit = {}) {
        val state = _editState.value
        if (!state.isEditing || state.isSaving) return

        val calories = CalorieUtils.parseAndRoundCalories(state.calories)
        val servingAmount = FoodAmountPolicy.parseAmount(state.servingAmount)
        val foodNameError = if (state.foodName.isBlank()) "음식 이름을 입력해주세요." else null
        val caloriesError = if (calories == null || calories <= 0) "올바른 칼로리를 입력해주세요." else null
        val servingAmountError = if (
            state.servingAmount.isNotBlank() && (servingAmount == null || servingAmount <= 0.0)
        ) {
            "섭취량은 0보다 큰 숫자로 입력해주세요."
        } else null
        val validated = state.copy(
            foodNameError = foodNameError,
            caloriesError = caloriesError,
            servingAmountError = servingAmountError,
            saveError = null
        )
        _editState.value = validated
        if (foodNameError != null || caloriesError != null || servingAmountError != null) return

        val original = requireNotNull(validated.original)
        val trimmedName = validated.foodName.trim()
        val trimmedUnit = validated.servingUnit.trim().takeIf(String::isNotBlank)
        val trimmedMemo = validated.memo.trim().takeIf(String::isNotBlank)
        val aiResultWasEdited = original.source == RecordSource.PHOTO_AI && (
            original.foodName != trimmedName ||
                original.calories != calories ||
                original.servingAmount != servingAmount ||
                original.servingUnit != trimmedUnit
            )
        val nutrition = updatedNutrition(original, validated, requireNotNull(calories), servingAmount)
        val updatedMeal = original.copy(
            date = validated.date.format(dateFormatter),
            time = validated.time.format(timeFormatter),
            mealType = validated.mealType,
            foodName = trimmedName,
            calories = requireNotNull(calories),
            carbohydrateGrams = nutrition.carbohydrateGrams,
            proteinGrams = nutrition.proteinGrams,
            fatGrams = nutrition.fatGrams,
            memo = trimmedMemo,
            servingAmount = servingAmount,
            servingUnit = trimmedUnit,
            portionPresetId = validated.portionPresetId,
            portionDisplayLabel = validated.portionDisplayLabel,
            portionEstimationType = validated.portionEstimationType,
            portionSourceReference = validated.portionSourceReference,
            wasAiResultEdited = original.wasAiResultEdited || aiResultWasEdited,
            updatedAtEpochMillis = nowProvider()
        )
        _editState.value = validated.copy(isSaving = true)

        viewModelScope.launch {
            try {
                mealRepository.updateMeal(updatedMeal)
                _selectedDate.value = validated.date
                _editState.value = MealEditUiState()
                onSuccess(updatedMeal)
            } catch (_: Exception) {
                _editState.value = validated.copy(
                    isSaving = false,
                    saveError = "기록 수정에 실패했어요. 다시 시도해주세요."
                )
            }
        }
    }

    private fun updatedNutrition(original: MealRecord, state: MealEditUiState, calories: Int, amount: Double?): Macronutrients {
        if (!state.caloriesManuallyEdited) snapshotNutrition(state, amount)?.let {
            return Macronutrients(it.carbohydrateGrams, it.proteinGrams, it.fatGrams)
        }
        if (calories == original.calories && amount == original.servingAmount) return Macronutrients(
            original.carbohydrateGrams, original.proteinGrams, original.fatGrams)
        if (original.calories <= 0) return Macronutrients.Unknown
        return Macronutrients(
            original.carbohydrateGrams,
            original.proteinGrams,
            original.fatGrams
        ).scaled(calories.toDouble() / original.calories)
    }

    private fun formatServing(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

internal fun MealEditUiState.draftValues(): List<Any?> = listOf(foodName, calories, mealType, date, time, servingAmount, servingUnit, quantity, quantityUnit, memo)
