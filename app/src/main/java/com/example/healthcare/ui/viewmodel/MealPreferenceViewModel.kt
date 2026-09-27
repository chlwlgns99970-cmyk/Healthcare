package com.example.healthcare.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.data.entity.UserExcludedFood
import com.example.healthcare.data.entity.UserMealPreference
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.domain.MealCoachCalculator
import com.example.healthcare.domain.FoodAllergenPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MealPreferenceUiState(
    val preference: UserMealPreference = MealCoachRepository.defaultPreference(),
    val excludedFoods: List<UserExcludedFood> = emptyList(),
    val allergyInput: String = "",
    val dislikeInput: String = "",
    val preferredInput: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    val enabledRatioTotal: Int get() = MealCoachCalculator.enabledRatios(preference).values.sum()
    val broadDislikeWarning: Boolean get() = dislikeInput.trim() in setOf("국", "밥", "면", "탕", "죽", "빵")
}

class MealPreferenceViewModel(private val repository: MealCoachRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MealPreferenceUiState())
    val uiState: StateFlow<MealPreferenceUiState> = _uiState.asStateFlow()
    private var preferredInputEdited = false

    init {
        viewModelScope.launch { repository.ensureDefaultPreference() }
        viewModelScope.launch {
            repository.preference.collect { preference ->
                _uiState.update {
                    it.copy(
                        preference = preference,
                        preferredInput = if (preferredInputEdited) it.preferredInput else preference.preferredFoods
                            .split('|')
                            .filter(String::isNotBlank)
                            .joinToString(", ")
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.excludedFoods.collect { foods -> _uiState.update { it.copy(excludedFoods = foods) } }
        }
    }

    fun setMealEnabled(type: MealType, enabled: Boolean) = updatePreference { preference ->
        when (type) {
            MealType.BREAKFAST -> preference.copy(breakfastEnabled = enabled)
            MealType.LUNCH -> preference.copy(lunchEnabled = enabled)
            MealType.DINNER -> preference.copy(dinnerEnabled = enabled)
            MealType.SNACK -> preference.copy(snackEnabled = enabled)
            MealType.OTHER -> preference
        }
    }

    fun setRatio(type: MealType, value: String) {
        val ratio = value.filter(Char::isDigit).toIntOrNull()?.coerceIn(0, 100) ?: 0
        updatePreference { preference ->
            when (type) {
                MealType.BREAKFAST -> preference.copy(breakfastRatio = ratio)
                MealType.LUNCH -> preference.copy(lunchRatio = ratio)
                MealType.DINNER -> preference.copy(dinnerRatio = ratio)
                MealType.SNACK -> preference.copy(snackRatio = ratio)
                MealType.OTHER -> preference
            }
        }
    }

    fun setDietType(value: String) = updatePreference { it.copy(dietType = value) }
    fun setCookingMode(value: String) = updatePreference { it.copy(cookingMode = value) }
    fun setBudgetLevel(value: String) = updatePreference { it.copy(budgetLevel = value) }
    fun setDiversity(value: String) = updatePreference { it.copy(recommendationDiversity = value) }

    fun onAllergyInput(value: String) = _uiState.update { it.copy(allergyInput = value, error = null) }
    fun onDislikeInput(value: String) = _uiState.update { it.copy(dislikeInput = value, error = null) }
    fun onPreferredInput(value: String) {
        preferredInputEdited = true
        _uiState.update { it.copy(preferredInput = value, error = null) }
    }

    fun addAllergy() {
        val value = _uiState.value.allergyInput
        if (FoodAllergenPolicy.canonicalize(value) == null) {
            _uiState.update { it.copy(error = "목록에서 알레르기 원인을 선택하거나 지원되는 원인명을 입력해 주세요.") }
            return
        }
        addExcluded(value, "ALLERGY") { it.copy(allergyInput = "", message = "알레르기 원인을 등록했습니다.") }
    }
    fun addDislike() = addExcluded(_uiState.value.dislikeInput, "DISLIKE") { it.copy(dislikeInput = "") }

    fun removeExcluded(food: UserExcludedFood) {
        viewModelScope.launch { repository.deleteExcludedFood(food) }
    }

    fun save() {
        val state = _uiState.value
        val preferred = state.preferredInput.split(',', '，').map(String::trim).filter(String::isNotBlank)
        val preference = state.preference.copy(
            preferredFoods = if (preferred.isEmpty()) "" else "|${preferred.joinToString("|")}|"
        )
        if (!MealCoachCalculator.validateRatios(preference)) {
            _uiState.update { it.copy(error = "활성 식사의 배분 합계가 100%가 되도록 조정해 주세요.", message = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, message = null) }
            runCatching { repository.savePreference(preference) }
                .onSuccess { _uiState.update { it.copy(isSaving = false, message = "식사 추천 설정을 저장했습니다.") } }
                .onFailure { _uiState.update { it.copy(isSaving = false, error = "설정을 저장하지 못했습니다.") } }
        }
    }

    private fun updatePreference(transform: (UserMealPreference) -> UserMealPreference) {
        _uiState.update { it.copy(preference = transform(it.preference), message = null, error = null) }
    }

    private fun addExcluded(value: String, type: String, clear: (MealPreferenceUiState) -> MealPreferenceUiState) {
        if (value.isBlank()) {
            _uiState.update { it.copy(error = "제외할 항목을 입력해 주세요.") }
            return
        }
        viewModelScope.launch {
            runCatching { repository.addExcludedFood(value, type) }
                .onSuccess { _uiState.update(clear) }
                .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "제외 항목을 저장하지 못했습니다.") } }
        }
    }
}
