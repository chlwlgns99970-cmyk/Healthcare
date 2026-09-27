package com.example.healthcare.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.EnergyProfileRepository
import com.example.healthcare.data.repository.GoalRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.data.entity.UserMealPreference
import com.example.healthcare.domain.EnergyBalanceCalculator
import com.example.healthcare.domain.DailyIntakeTimeline
import com.example.healthcare.domain.MealCoachCalculator
import com.example.healthcare.domain.Macronutrients
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardEnergyUiState(
    val profile: EnergyProfileHistory? = null,
    val manualTargetCalories: Int = 2000,
    val effectiveTargetCalories: Int = 2000,
    val intakeCalories: Int = 0,
    val maintenanceCalories: Int? = null,
    val dailySurplusCalories: Int = 0,
    val sevenDayEquivalentKg: Double? = null,
    val thirtyDayEquivalentKg: Double? = null
) {
    val isConfigured: Boolean get() = profile != null
    val targetMode: TargetMode get() = profile?.targetMode ?: TargetMode.MANUAL
}

data class TodayCoachUiState(
    val targetCalories: Int = 2000,
    val intakeCalories: Int = 0,
    val remainingCalories: Int = 2000,
    val remainingMeals: List<MealType> = emptyList(),
    val nextMealType: MealType? = null,
    val nextMealBudgetKcal: Int? = null,
    val targetExceeded: Boolean = false,
    val previewRecommendationTemplateId: String? = null,
    val previewRecommendationName: String? = null,
    val previewRecommendationKcal: Int? = null,
    val previewRecommendationCarbohydrateGrams: Double? = null,
    val previewRecommendationProteinGrams: Double? = null,
    val previewRecommendationFatGrams: Double? = null,
    val isRecommendationLoading: Boolean = false,
    val recommendationError: Boolean = false
)

internal fun recommendationMealsForDate(
    selectedDate: LocalDate,
    today: LocalDate,
    now: LocalTime,
    preference: UserMealPreference
): List<MealType> {
    if (selectedDate != today) return emptyList()
    val upcoming = MealCoachCalculator.remainingMeals(now, preference)
    if (upcoming.isNotEmpty()) return upcoming
    val enabled = MealCoachCalculator.enabledRatios(preference).keys
    return listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
        .lastOrNull { it in enabled }
        ?.let(::listOf)
        .orEmpty()
}

/** 대시보드 화면의 비즈니스 로직을 담당하는 ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val mealRepository: MealRepository,
    private val goalRepository: GoalRepository,
    private val energyProfileRepository: EnergyProfileRepository,
    private val mealCoachRepository: MealCoachRepository? = null,
    private val nowProvider: () -> LocalTime = LocalTime::now
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val intakeTimeline: StateFlow<DailyIntakeTimeline> = combine(
        mealRepository.allMeals,
        goalRepository.allGoals,
        energyProfileRepository.allProfiles
    ) { meals, goals, profiles -> DailyIntakeTimeline.build(meals, goals, profiles) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyIntakeTimeline.Empty)

    val meals: StateFlow<List<MealRecord>> = _selectedDate
        .flatMapLatest { date -> mealRepository.getMealsByDate(date.format(dateFormatter)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCalories: StateFlow<Int> = _selectedDate
        .flatMapLatest { date -> mealRepository.getTotalCaloriesByDate(date.format(dateFormatter)) }
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dailyNutrition: StateFlow<Macronutrients> = _selectedDate
        .flatMapLatest { date -> mealRepository.getNutritionByDate(date.format(dateFormatter)) }
        .map { rows -> Macronutrients.strictSum(rows.map { it.asMacronutrients() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Macronutrients.Unknown)

    private val manualTargetCalories = _selectedDate
        .flatMapLatest { date -> goalRepository.getGoalForDate(date.format(dateFormatter)) }
        .map { it?.targetCalories ?: 2000 }

    private val energyProfile = _selectedDate
        .flatMapLatest { date -> energyProfileRepository.getProfileForDate(date.format(dateFormatter)) }

    val energyUiState: StateFlow<DashboardEnergyUiState> = combine(
        totalCalories,
        manualTargetCalories,
        energyProfile
    ) { intake, manualTarget, profile ->
        createEnergyUiState(intake, manualTarget, profile)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardEnergyUiState()
    )

    val targetCalories: StateFlow<Int> = energyUiState
        .map { it.effectiveTargetCalories }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000)

    val calorieStatusText: StateFlow<String> = combine(totalCalories, targetCalories) { total, target ->
        com.example.healthcare.util.CalorieUtils.getCalorieStatusText(total, target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "계산 중...")

    private val mealPreference = mealCoachRepository?.preference
        ?: MutableStateFlow(MealCoachRepository.defaultPreference())
    private val templateCount = mealCoachRepository?.templateCount ?: MutableStateFlow(0)

    val todayCoachUiState: StateFlow<TodayCoachUiState> = combine(
        energyUiState,
        mealPreference,
        _selectedDate,
        templateCount
    ) { energy, preference, date, availableTemplates ->
        val state = createTodayCoachState(energy, preference, date)
        if (
            date == LocalDate.now() && state.nextMealType != null && state.nextMealBudgetKcal != null
        ) {
            if (availableTemplates == 0) {
                return@combine state.copy(isRecommendationLoading = true)
            }
            val result = runCatching {
                mealCoachRepository?.getOrCreateTodayRecommendation(
                    localDate = date,
                    targetCalories = state.targetCalories,
                    preference = preference,
                    mealType = state.nextMealType,
                    budgetKcal = state.nextMealBudgetKcal
                )
            }
            val template = result.getOrNull()
            state.copy(
                previewRecommendationTemplateId = template?.id,
                previewRecommendationName = template?.name,
                previewRecommendationKcal = template?.totalKcal,
                previewRecommendationCarbohydrateGrams = template?.carbohydrateGrams,
                previewRecommendationProteinGrams = template?.proteinGrams,
                previewRecommendationFatGrams = template?.fatGrams,
                recommendationError = result.isFailure || template == null
            )
        } else {
            state
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TodayCoachUiState()
    )

    init {
        mealCoachRepository?.let { repository ->
            viewModelScope.launch { repository.ensureDefaultPreference() }
        }
    }

    fun moveToPreviousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun moveToNextDay() {
        _selectedDate.value = _selectedDate.value.plusDays(1)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun deleteMeal(meal: MealRecord) {
        viewModelScope.launch { mealRepository.deleteMeal(meal) }
    }

    private fun createEnergyUiState(
        intake: Int,
        manualTarget: Int,
        profile: EnergyProfileHistory?
    ): DashboardEnergyUiState {
        if (profile == null) {
            return DashboardEnergyUiState(
                manualTargetCalories = manualTarget,
                effectiveTargetCalories = manualTarget,
                intakeCalories = intake
            )
        }

        return runCatching {
            val maintenanceExact = EnergyBalanceCalculator.calculateMaintenanceKcal(
                profile.basalMetabolicRateKcal.toDouble(),
                profile.palMultiplier
            )
            val maintenanceRounded = EnergyBalanceCalculator.roundKcal(maintenanceExact)
            val surplusExact = EnergyBalanceCalculator.calculateDailySurplus(
                intake.toDouble(),
                maintenanceExact
            )
            DashboardEnergyUiState(
                profile = profile,
                manualTargetCalories = manualTarget,
                effectiveTargetCalories = EnergyBalanceCalculator.resolveDailyTargetKcal(
                    manualTarget,
                    profile.basalMetabolicRateKcal,
                    profile.palMultiplier,
                    profile.targetMode
                ),
                intakeCalories = intake,
                maintenanceCalories = maintenanceRounded,
                dailySurplusCalories = EnergyBalanceCalculator.roundKcal(surplusExact),
                sevenDayEquivalentKg = if (surplusExact > 0.0) {
                    EnergyBalanceCalculator.calculateSimpleWeightEquivalentKg(surplusExact, 7)
                } else null,
                thirtyDayEquivalentKg = if (surplusExact > 0.0) {
                    EnergyBalanceCalculator.calculateSimpleWeightEquivalentKg(surplusExact, 30)
                } else null
            )
        }.getOrElse {
            DashboardEnergyUiState(
                manualTargetCalories = manualTarget,
                effectiveTargetCalories = manualTarget,
                intakeCalories = intake
            )
        }
    }

    private fun createTodayCoachState(
        energy: DashboardEnergyUiState,
        preference: UserMealPreference,
        date: LocalDate
    ): TodayCoachUiState {
        val remainingMeals = recommendationMealsForDate(
            selectedDate = date,
            today = LocalDate.now(),
            now = nowProvider(),
            preference = preference
        )
        val redistribution = MealCoachCalculator.redistribute(
            targetCalories = energy.effectiveTargetCalories,
            currentIntakeCalories = energy.intakeCalories,
            remainingMeals = remainingMeals,
            preference = preference
        )
        val nextMealType = remainingMeals.firstOrNull()
        val nextMealBudget = nextMealType?.let {
            MealCoachCalculator.nextMealBudget(
                energy.effectiveTargetCalories, energy.intakeCalories, it, preference
            )
        }
        return TodayCoachUiState(
            targetCalories = energy.effectiveTargetCalories,
            intakeCalories = energy.intakeCalories,
            remainingCalories = redistribution.remainingCalories,
            remainingMeals = remainingMeals,
            nextMealType = nextMealType,
            nextMealBudgetKcal = nextMealBudget,
            targetExceeded = redistribution.targetExceeded
        )
    }
}
