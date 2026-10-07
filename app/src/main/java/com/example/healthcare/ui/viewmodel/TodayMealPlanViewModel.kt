package com.example.healthcare.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.TodayMealPlanRepository
import com.example.healthcare.domain.DailyRecommendationTheme
import com.example.healthcare.domain.DailyMealThemePolicy
import com.example.healthcare.domain.TodayMealPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayMealPlanUiState(
    val selectedTheme: DailyRecommendationTheme? = null,
    val plan: TodayMealPlan? = null,
    val target: Int? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
    val configuredAllergies: Set<String> = emptySet()
)

class TodayMealPlanViewModel(private val repository: TodayMealPlanRepository) : ViewModel() {
    private val state = MutableStateFlow(TodayMealPlanUiState())
    val uiState: StateFlow<TodayMealPlanUiState> = state.asStateFlow()
    private var pendingRefresh = false
    init {
        viewModelScope.launch {
            repository.exclusions.collect { exclusions ->
                state.update { it.copy(configuredAllergies = exclusions
                    .filter { food -> food.exclusionType == "ALLERGY" }
                    .map { food -> food.normalizedFoodName }.toSet()) }
            }
        }
        viewModelScope.launch {
            var previous: List<Any?>? = null
            combine(repository.changes, repository.exclusions, repository.goalChanges, repository.energyChanges,
                combine(repository.recordChanges, repository.templateChanges) { records, count -> records to count }) { preference, exclusions, goals, profiles, records ->
                listOf(preference, exclusions, goals, profiles, records)
            }.collect { inputs ->
                val first = previous == null
                val changed = previous != null && previous != inputs
                previous = inputs
                if ((changed || first) && (state.value.selectedTheme != null ||
                        repository.store.state.value?.date == java.time.LocalDate.now().toString())) {
                    if (state.value.loading) pendingRefresh = true else generate()
                } else state.update { it.copy(target = repository.currentTarget()) }
            }
        }
    }
    fun showThemes() { state.update { it.copy(selectedTheme = null, plan = null, message = null, error = false) } }
    fun selectTheme(theme: DailyRecommendationTheme) {
        state.update { it.copy(selectedTheme = theme, plan = null, message = null, error = false) }
        if (state.value.loading) pendingRefresh = true else generate()
    }
    fun showSavedPlan() {
        val stored = repository.store.state.value
        if (stored?.date == java.time.LocalDate.now().toString()) selectTheme(stored.theme)
    }
    fun alternate() = generate(alternate = true)
    fun replace(meal: MealType) = generate(replace = meal)
    fun retry() = generate()
    private fun generate(alternate: Boolean = false, replace: MealType? = null) {
        val theme = state.value.selectedTheme ?: repository.store.state.value
            ?.takeIf { it.date == java.time.LocalDate.now().toString() }?.theme ?: return
        if (state.value.loading) return
        state.update { it.copy(loading = true, message = null, error = false) }
        viewModelScope.launch {
            val result = runCatching { repository.create(theme, alternate, replace) }
            val target = runCatching { repository.currentTarget() }.getOrNull()
            state.update { current ->
                // A visible selection can change while the previous plan is being validated.
                if (current.selectedTheme != theme) current.copy(loading = false, target = target)
                else current.copy(loading = false, target = target,
                plan = result.getOrNull() ?: if ((alternate || replace != null) && target != null &&
                    current.plan?.targetKcal == target) current.plan else null,
                error = result.isFailure,
                message = when {
                    result.isFailure -> "추천 식단을 만들지 못했어요. 다시 시도해주세요."
                    target == null -> "하루 목표 칼로리를 먼저 설정해주세요."
                    theme == DailyRecommendationTheme.SLOW_AGING_STYLE && result.getOrNull() == null -> DailyMealThemePolicy.SLOW_STYLE_LIMITATION
                    result.getOrNull() == null && replace != null -> "조건에 맞는 다른 메뉴가 없어요."
                    result.getOrNull() == null && alternate -> "조건에 맞는 다른 조합이 없어요."
                    result.getOrNull() == null -> "현재 조건으로 하루 식단을 구성하기 어려워요."
                    else -> null
                }) }
            if (pendingRefresh) { pendingRefresh = false; generate() }
        }
    }
}
