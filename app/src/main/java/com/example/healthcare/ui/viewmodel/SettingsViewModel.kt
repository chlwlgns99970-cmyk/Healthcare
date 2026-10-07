package com.example.healthcare.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.data.BodyProfilePersistence
import com.example.healthcare.data.WeightGoalPersistence
import com.example.healthcare.data.WeightLossGoal
import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.data.repository.EnergyProfileRepository
import com.example.healthcare.data.repository.GoalRepository
import com.example.healthcare.data.update.FoodDataUpdateCoordinator
import com.example.healthcare.data.update.FoodDataUpdateState
import com.example.healthcare.domain.EnergyBalanceCalculator
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodyProfileCalculator
import com.example.healthcare.domain.BodySex
import com.example.healthcare.domain.IntakePaceCalculation
import com.example.healthcare.domain.WeightGoalCalculation
import com.example.healthcare.domain.WeightGoalCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EnergySettingsUiState(
    val bmrInput: String = "",
    val activityLevel: ActivityLevel? = null,
    val customPalInput: String = "",
    val targetMode: TargetMode = TargetMode.MANUAL,
    val maintenancePreviewKcal: Int? = null,
    val bmrError: String? = null,
    val activityError: String? = null,
    val customPalError: String? = null,
    val bmrReviewMessage: String? = null,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val saveError: String? = null
)

data class BodyProfileUiState(
    val sex: BodySex? = null,
    val ageInput: String = "",
    val heightInput: String = "",
    val weightInput: String = "",
    val estimatedBmrKcal: Int? = null,
    val sexError: String? = null,
    val ageError: String? = null,
    val heightError: String? = null,
    val weightError: String? = null,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val saveError: String? = null
)

data class WeightGoalUiState(
    val targetWeightInput: String = "",
    val durationWeeksInput: String = "",
    val calculation: WeightGoalCalculation? = null,
    val storedGoal: WeightLossGoal? = null,
    val targetWeightError: String? = null,
    val durationError: String? = null,
    val calculationError: String? = null,
    val isApplying: Boolean = false,
    val applyMessage: String? = null,
    val applyError: String? = null,
    val intakeTargetInput: String = "",
    val intakePaceCalculation: IntakePaceCalculation? = null,
    val intakeTargetError: String? = null,
    val isApplyingIntakeTarget: Boolean = false,
    val intakeApplyMessage: String? = null,
    val intakeApplyError: String? = null
)

/** 설정 화면의 목표 및 에너지 기준 상태를 관리합니다. */
class SettingsViewModel(
    private val goalRepository: GoalRepository,
    private val energyProfileRepository: EnergyProfileRepository,
    private val bodyProfileStore: BodyProfilePersistence? = null,
    private val weightGoalStore: WeightGoalPersistence? = null,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val foodDataUpdateCoordinator: FoodDataUpdateCoordinator? = null
) : ViewModel() {

    val saveAcknowledgement = SaveAcknowledgement<String>()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var hasUserEditedEnergyForm = false

    val currentGoal: StateFlow<CalorieGoal?> = goalRepository.latestGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _energyState = MutableStateFlow(EnergySettingsUiState())
    val energyState: StateFlow<EnergySettingsUiState> = _energyState.asStateFlow()

    private val _bodyProfileState = MutableStateFlow(initialBodyProfileState())
    val bodyProfileState: StateFlow<BodyProfileUiState> = _bodyProfileState.asStateFlow()

    private val _weightGoalState = MutableStateFlow(initialWeightGoalState())
    val weightGoalState: StateFlow<WeightGoalUiState> = _weightGoalState.asStateFlow()
    private var savedEnergyDraft = _energyState.value
    private var savedBodyDraft = _bodyProfileState.value
    private var savedWeightDraft = _weightGoalState.value
    val hasUnsavedInput: Boolean get() = _energyState.value.draftValues() != savedEnergyDraft.draftValues() ||
        _bodyProfileState.value.draftValues() != savedBodyDraft.draftValues() ||
        _weightGoalState.value.draftValues() != savedWeightDraft.draftValues()

    fun discardSettingsDraft() {
        if (_energyState.value.isSaving || _bodyProfileState.value.isSaving || _isSaving.value) return
        hasUserEditedEnergyForm = false
        _energyState.value = savedEnergyDraft
        _bodyProfileState.value = savedBodyDraft
        _weightGoalState.value = savedWeightDraft
    }

    val foodDataUpdateState: StateFlow<FoodDataUpdateState> =
        foodDataUpdateCoordinator?.state ?: MutableStateFlow(FoodDataUpdateState())

    init {
        val today = todayProvider().format(dateFormatter)
        viewModelScope.launch {
            energyProfileRepository.getProfileForDate(today)
                .filterNotNull()
                .collect { profile ->
                    if (!hasUserEditedEnergyForm) {
                        val current = _energyState.value
                        _energyState.value = EnergySettingsUiState(
                            bmrInput = profile.basalMetabolicRateKcal.toString(),
                            activityLevel = profile.activityLevelCode,
                            customPalInput = if (profile.activityLevelCode == ActivityLevel.CUSTOM) {
                                formatPal(profile.palMultiplier)
                            } else "",
                            targetMode = profile.targetMode,
                            maintenancePreviewKcal = EnergyBalanceCalculator.roundKcal(
                                EnergyBalanceCalculator.calculateMaintenanceKcal(
                                    profile.basalMetabolicRateKcal.toDouble(),
                                    profile.palMultiplier
                                )
                            ),
                            saveMessage = current.saveMessage,
                            saveError = current.saveError
                        )
                        savedEnergyDraft = _energyState.value
                        refreshWeightGoalCalculation(showErrors = false)
                        refreshIntakePaceCalculation(showErrors = false)
                    }
                }
        }
    }

    fun onBmrChange(value: String) {
        hasUserEditedEnergyForm = true
        val digits = value.filter(Char::isDigit).take(EnergyBalanceCalculator.MAX_BMR_DIGITS)
        _energyState.value = _energyState.value.copy(
            bmrInput = digits,
            bmrError = null,
            bmrReviewMessage = null,
            saveMessage = null,
            saveError = null
        ).withUpdatedPreview()
        refreshWeightGoalCalculation(showErrors = false)
        refreshIntakePaceCalculation(showErrors = false)
    }

    fun onActivityLevelSelected(level: ActivityLevel) {
        hasUserEditedEnergyForm = true
        _energyState.value = _energyState.value.copy(
            activityLevel = level,
            activityError = null,
            customPalError = null,
            saveMessage = null,
            saveError = null
        ).withUpdatedPreview()
        refreshWeightGoalCalculation(showErrors = false)
        refreshIntakePaceCalculation(showErrors = false)
    }

    fun onCustomPalChange(value: String) {
        hasUserEditedEnergyForm = true
        val normalized = value.replace(',', '.')
            .filter { it.isDigit() || it == '.' }
            .let { input ->
                val firstDot = input.indexOf('.')
                if (firstDot < 0) input.take(4) else {
                    input.substring(0, firstDot + 1) +
                        input.substring(firstDot + 1).replace(".", "").take(2)
                }
            }
        _energyState.value = _energyState.value.copy(
            customPalInput = normalized,
            customPalError = null,
            saveMessage = null,
            saveError = null
        ).withUpdatedPreview()
        refreshWeightGoalCalculation(showErrors = false)
        refreshIntakePaceCalculation(showErrors = false)
    }

    fun onTargetModeSelected(mode: TargetMode) {
        hasUserEditedEnergyForm = true
        _energyState.value = _energyState.value.copy(
            targetMode = mode,
            saveMessage = null,
            saveError = null
        )
    }

    fun onBodySexSelected(sex: BodySex) = updateBodyProfile { copy(sex = sex, sexError = null) }

    fun onBodyAgeChange(value: String) = updateBodyProfile {
        copy(ageInput = value.filter(Char::isDigit).take(3), ageError = null)
    }

    fun onBodyHeightChange(value: String) = updateBodyProfile {
        copy(heightInput = normalizedDecimalInput(value, 3), heightError = null)
    }

    fun onBodyWeightChange(value: String) = updateBodyProfile {
        copy(weightInput = normalizedDecimalInput(value, 3), weightError = null)
    }

    fun onWeightGoalTargetChange(value: String) {
        _weightGoalState.value = _weightGoalState.value.copy(
            targetWeightInput = normalizedDecimalInput(value, 3),
            targetWeightError = null,
            calculationError = null,
            applyMessage = null,
            applyError = null
        )
        refreshWeightGoalCalculation(showErrors = false)
    }

    fun onWeightGoalWeeksChange(value: String) {
        _weightGoalState.value = _weightGoalState.value.copy(
            durationWeeksInput = value.filter(Char::isDigit).take(3),
            durationError = null,
            calculationError = null,
            applyMessage = null,
            applyError = null
        )
        refreshWeightGoalCalculation(showErrors = false)
    }

    fun onIntakeTargetChange(value: String) {
        _weightGoalState.value = _weightGoalState.value.copy(
            intakeTargetInput = value.filter(Char::isDigit).take(5),
            intakeTargetError = null,
            intakeApplyMessage = null,
            intakeApplyError = null
        )
        refreshIntakePaceCalculation(showErrors = false)
    }

    fun calculateIntakePace() = refreshIntakePaceCalculation(showErrors = true)

    fun calculateWeightGoal() = refreshWeightGoalCalculation(showErrors = true)

    fun applyWeightGoal() {
        refreshWeightGoalCalculation(showErrors = true)
        val state = _weightGoalState.value
        val calculation = state.calculation ?: return
        if (!calculation.canApply || state.isApplying) return
        val energy = _energyState.value
        val bmr = energy.bmrInput.toIntOrNull() ?: return
        val level = energy.activityLevel ?: return
        val pal = selectedPal(energy, level) ?: return
        val store = weightGoalStore ?: run {
            _weightGoalState.value = state.copy(applyError = "체중 목표 저장소를 사용할 수 없습니다.")
            return
        }
        _weightGoalState.value = state.copy(isApplying = true, applyMessage = null, applyError = null)
        viewModelScope.launch {
            try {
                persistManualGoal(calculation.proposedIntakeKcal, bmr, level, pal)
                val saved = WeightLossGoal(
                    currentWeightKg = calculation.currentWeightKg,
                    targetWeightKg = calculation.targetWeightKg,
                    durationWeeks = calculation.durationWeeks,
                    dailyDeficitKcal = calculation.dailyDeficitKcal,
                    appliedTargetKcal = calculation.proposedIntakeKcal,
                    createdAtEpochMillis = nowProvider()
                )
                store.save(saved)
                hasUserEditedEnergyForm = false
                _energyState.value = energy.copy(targetMode = TargetMode.MANUAL)
                savedEnergyDraft = _energyState.value
                _weightGoalState.value = _weightGoalState.value.copy(
                    storedGoal = saved,
                    isApplying = false,
                    applyMessage = "감량 목표를 일일 섭취 목표에 적용했습니다.",
                    applyError = null
                )
                savedWeightDraft = _weightGoalState.value
            } catch (_: Exception) {
                _weightGoalState.value = _weightGoalState.value.copy(
                    isApplying = false,
                    applyMessage = null,
                    applyError = "목표를 적용하지 못했습니다. 기존 목표는 그대로 확인해 주세요."
                )
            }
        }
    }

    fun applyIntakeTarget() {
        refreshIntakePaceCalculation(showErrors = true)
        val state = _weightGoalState.value
        val calculation = state.intakePaceCalculation ?: return
        if (!calculation.canApply || state.isApplyingIntakeTarget) return
        _weightGoalState.value = state.copy(
            isApplyingIntakeTarget = true,
            intakeApplyMessage = null,
            intakeApplyError = null
        )
        viewModelScope.launch {
            try {
                persistManualGoal(calculation.targetIntakeKcal)
                _weightGoalState.value = _weightGoalState.value.copy(
                    isApplyingIntakeTarget = false,
                    intakeApplyMessage = "하루 섭취 목표에 적용했습니다.",
                    intakeApplyError = null
                )
                savedWeightDraft = _weightGoalState.value
            } catch (_: Exception) {
                _weightGoalState.value = _weightGoalState.value.copy(
                    isApplyingIntakeTarget = false,
                    intakeApplyMessage = null,
                    intakeApplyError = "목표를 적용하지 못했습니다. 기존 목표는 그대로 확인해 주세요."
                )
            }
        }
    }

    fun saveBodyProfile(onSuccess: (Double) -> Unit = {}) {
        val current = _bodyProfileState.value
        if (current.isSaving || saveAcknowledgement.isPending) return
        val validation = BodyProfileCalculator.validate(
            current.sex, current.ageInput, current.heightInput, current.weightInput
        )
        if (!validation.isValid) {
            _bodyProfileState.value = current.copy(
                sexError = validation.sexError,
                ageError = validation.ageError,
                heightError = validation.heightError,
                weightError = validation.weightError,
                saveMessage = null,
                saveError = null
            )
            return
        }
        val profile = requireNotNull(validation.profile)
        val store = bodyProfileStore
        if (store == null) {
            _bodyProfileState.value = current.copy(saveMessage = null, saveError = "신체정보 저장소를 사용할 수 없습니다.")
            return
        }
        _bodyProfileState.value = current.copy(isSaving = true, saveMessage = null, saveError = null)
        viewModelScope.launch {
            try {
                store.save(profile)
                val estimatedBmr = BodyProfileCalculator.estimateBmr(profile)
                val currentEnergy = _energyState.value
                val level = currentEnergy.activityLevel
                val pal = level?.let { selectedPal(currentEnergy, it) }
                if (level != null && pal != null) {
                    energyProfileRepository.saveProfile(
                        basalMetabolicRateKcal = estimatedBmr,
                        activityLevel = level,
                        palMultiplier = pal,
                        targetMode = currentEnergy.targetMode,
                        effectiveFromDate = todayProvider().format(dateFormatter),
                        nowEpochMillis = nowProvider()
                    )
                    hasUserEditedEnergyForm = false
                }
                _energyState.value = currentEnergy.copy(
                    bmrInput = estimatedBmr.toString(),
                    bmrError = null,
                    bmrReviewMessage = "신체정보로 계산한 예상 기초대사량을 반영했습니다."
                ).withUpdatedPreview()
                _bodyProfileState.value = profile.toUiState(saveMessage = "내 정보를 저장했습니다.")
                savedBodyDraft = _bodyProfileState.value
                savedEnergyDraft = _energyState.value
                onSuccess(profile.weightKg)
            } catch (_: Exception) {
                _bodyProfileState.value = current.copy(
                    isSaving = false,
                    saveMessage = null,
                    saveError = "내 정보를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."
                )
            }
        }
    }

    fun useEstimatedBmr() {
        val estimated = _bodyProfileState.value.estimatedBmrKcal ?: return
        onBmrChange(estimated.toString())
        _energyState.value = _energyState.value.copy(
            bmrReviewMessage = "신체정보 계산값을 입력했습니다. 적용하려면 아래에서 에너지 기준을 저장해 주세요."
        )
    }

    fun saveEnergyProfile(onSuccess: () -> Unit = {}) {
        if (_energyState.value.isSaving || saveAcknowledgement.isPending) return
        val validated = validateEnergyState(_energyState.value) ?: return
        _energyState.value = validated.copy(isSaving = true, saveMessage = null, saveError = null)

        viewModelScope.launch {
            try {
                val bmr = validated.bmrInput.toInt()
                val activityLevel = requireNotNull(validated.activityLevel)
                val pal = selectedPal(validated, activityLevel)!!
                energyProfileRepository.saveProfile(
                    basalMetabolicRateKcal = bmr,
                    activityLevel = activityLevel,
                    palMultiplier = pal,
                    targetMode = validated.targetMode,
                    effectiveFromDate = todayProvider().format(dateFormatter),
                    nowEpochMillis = nowProvider()
                )
                hasUserEditedEnergyForm = false
                _energyState.value = validated.copy(
                    isSaving = false,
                    saveMessage = "저장되었어요",
                    saveError = null
                )
                savedEnergyDraft = _energyState.value
                onSuccess()
            } catch (_: Exception) {
                _energyState.value = validated.copy(
                    isSaving = false,
                    saveMessage = null,
                    saveError = "저장에 실패했어요. 다시 시도해주세요."
                )
            }
        }
    }

    fun updateGoal(calories: Int, onSuccess: () -> Unit) {
        if (_isSaving.value || saveAcknowledgement.isPending || calories <= 0) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                persistManualGoal(calories)
                onSuccess()
            } catch (_: Exception) {
                // 기존 목표는 실패 시 변경된 것처럼 표시하지 않습니다.
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun checkFoodDataNow() {
        val coordinator = foodDataUpdateCoordinator ?: return
        viewModelScope.launch { coordinator.checkNow() }
    }

    private suspend fun persistManualGoal(
        calories: Int,
        knownBmr: Int? = null,
        knownLevel: ActivityLevel? = null,
        knownPal: Double? = null
    ) {
        val today = todayProvider().format(dateFormatter)
        goalRepository.insertGoal(CalorieGoal(targetCalories = calories, startDate = today))
        // 직접 입력과 명시적인 계산 결과 적용은 모두 같은 MANUAL 저장 경로를 사용합니다.
        val energy = _energyState.value
        val bmr = knownBmr ?: energy.bmrInput.toIntOrNull()
        val level = knownLevel ?: energy.activityLevel
        val pal = knownPal ?: level?.let { selectedPal(energy, it) }
        if (bmr != null && bmr > 0 && level != null && pal != null) {
            energyProfileRepository.saveProfile(
                basalMetabolicRateKcal = bmr,
                activityLevel = level,
                palMultiplier = pal,
                targetMode = TargetMode.MANUAL,
                effectiveFromDate = today,
                nowEpochMillis = nowProvider()
            )
            hasUserEditedEnergyForm = false
            _energyState.value = energy.copy(targetMode = TargetMode.MANUAL)
            savedEnergyDraft = _energyState.value
        }
    }

    private fun validateEnergyState(state: EnergySettingsUiState): EnergySettingsUiState? {
        val bmr = state.bmrInput.toIntOrNull()
        val bmrError = when {
            state.bmrInput.isBlank() -> "기초대사량을 입력해 주세요."
            bmr == null || bmr <= 0 -> "0보다 큰 숫자를 입력해 주세요."
            else -> null
        }
        val activityError = if (state.activityLevel == null) "활동 수준을 선택해 주세요." else null
        val palError = if (state.activityLevel == ActivityLevel.CUSTOM) {
            val pal = state.customPalInput.toDoubleOrNull()
            if (pal == null || !EnergyBalanceCalculator.isValidCustomPal(pal)) {
                "PAL은 1.40 이상 2.40 이하로 입력해 주세요."
            } else null
        } else null
        val reviewMessage = bmr?.takeIf { EnergyBalanceCalculator.shouldReviewBmrInput(it) }?.let {
            "입력한 기초대사량이 일반적인 범위와 크게 다를 수 있습니다. 값을 다시 확인해 주세요."
        }

        val updated = state.copy(
            bmrError = bmrError,
            activityError = activityError,
            customPalError = palError,
            bmrReviewMessage = reviewMessage,
            saveMessage = null,
            saveError = null
        ).withUpdatedPreview()
        _energyState.value = updated
        return if (bmrError == null && activityError == null && palError == null) updated else null
    }

    private fun EnergySettingsUiState.withUpdatedPreview(): EnergySettingsUiState {
        val bmr = bmrInput.toIntOrNull()
        val level = activityLevel
        val pal = if (level == null) null else selectedPal(this, level)
        val preview = if (bmr != null && bmr > 0 && pal != null) {
            runCatching {
                EnergyBalanceCalculator.roundKcal(
                    EnergyBalanceCalculator.calculateMaintenanceKcal(bmr.toDouble(), pal)
                )
            }.getOrNull()
        } else null
        return copy(maintenancePreviewKcal = preview)
    }

    private fun selectedPal(state: EnergySettingsUiState, level: ActivityLevel): Double? =
        level.defaultPalMultiplier ?: state.customPalInput.toDoubleOrNull()

    private fun formatPal(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

    private fun initialBodyProfileState(): BodyProfileUiState {
        val stored = bodyProfileStore?.read()
        if (stored != null) return stored.toUiState()
        val draft = bodyProfileStore?.readDraft()
        return BodyProfileUiState(
            sex = draft?.sex,
            ageInput = draft?.ageYears?.toString().orEmpty(),
            heightInput = draft?.heightCm?.let(::formatBodyValue).orEmpty(),
            weightInput = draft?.weightKg?.let(::formatBodyValue).orEmpty()
        )
    }

    private fun BodyProfile.toUiState(saveMessage: String? = null) = BodyProfileUiState(
        sex = sex,
        ageInput = ageYears.toString(),
        heightInput = formatBodyValue(heightCm),
        weightInput = formatBodyValue(weightKg),
        estimatedBmrKcal = BodyProfileCalculator.estimateBmr(this),
        saveMessage = saveMessage
    )

    private fun updateBodyProfile(transform: BodyProfileUiState.() -> BodyProfileUiState) {
        val updated = _bodyProfileState.value.transform().copy(saveMessage = null, saveError = null)
        val profile = BodyProfileCalculator.validate(
            updated.sex, updated.ageInput, updated.heightInput, updated.weightInput
        ).profile
        _bodyProfileState.value = updated.copy(
            estimatedBmrKcal = profile?.let(BodyProfileCalculator::estimateBmr)
        )
        refreshWeightGoalCalculation(showErrors = false)
    }

    private fun refreshWeightGoalCalculation(showErrors: Boolean) {
        val state = _weightGoalState.value
        val currentWeight = _bodyProfileState.value.weightInput.toDoubleOrNull()
            ?: bodyProfileStore?.read()?.weightKg
        val target = state.targetWeightInput.toDoubleOrNull()
        val weeks = state.durationWeeksInput.toIntOrNull()
        val bmr = _energyState.value.bmrInput.toIntOrNull()
        val maintenance = _energyState.value.maintenancePreviewKcal
        val targetError = if (showErrors && target == null) "목표 몸무게를 입력해 주세요." else null
        val weeksError = if (showErrors && (weeks == null || weeks <= 0)) "1주 이상의 기간을 입력해 주세요." else null
        if (currentWeight == null || target == null || weeks == null || bmr == null || maintenance == null) {
            _weightGoalState.value = state.copy(
                calculation = null,
                targetWeightError = targetError,
                durationError = weeksError,
                calculationError = if (showErrors && currentWeight == null) "내 정보에서 현재 몸무게를 먼저 저장해 주세요."
                else if (showErrors && (bmr == null || maintenance == null)) "에너지 기준을 먼저 입력해 주세요."
                else null
            )
            return
        }
        val result = WeightGoalCalculator.calculate(currentWeight, target, weeks, maintenance, bmr)
        _weightGoalState.value = state.copy(
            calculation = result.getOrNull(),
            targetWeightError = targetError,
            durationError = weeksError,
            calculationError = result.exceptionOrNull()?.message?.takeIf { showErrors || state.targetWeightInput.isNotBlank() },
            applyMessage = null,
            applyError = null
        )
    }

    private fun refreshIntakePaceCalculation(showErrors: Boolean) {
        val state = _weightGoalState.value
        val target = state.intakeTargetInput.toIntOrNull()
        val bmr = _energyState.value.bmrInput.toIntOrNull()
        val maintenance = _energyState.value.maintenancePreviewKcal
        val inputError = when {
            !showErrors -> null
            state.intakeTargetInput.isBlank() -> "하루 섭취 목표를 입력해 주세요."
            target == null || target <= 0 -> "0보다 큰 숫자를 입력해 주세요."
            else -> null
        }
        if (target == null || target <= 0 || bmr == null || maintenance == null) {
            _weightGoalState.value = state.copy(
                intakePaceCalculation = null,
                intakeTargetError = inputError ?: if (showErrors && (bmr == null || maintenance == null)) {
                    "기초대사량과 활동 수준을 먼저 저장해 주세요."
                } else null
            )
            return
        }
        val result = WeightGoalCalculator.calculateIntakePace(maintenance, bmr, target)
        _weightGoalState.value = state.copy(
            intakePaceCalculation = result.getOrNull(),
            intakeTargetError = inputError ?: result.exceptionOrNull()?.message,
            intakeApplyMessage = null,
            intakeApplyError = null
        )
    }

    private fun initialWeightGoalState(): WeightGoalUiState {
        val stored = weightGoalStore?.read()
        return WeightGoalUiState(
            targetWeightInput = stored?.targetWeightKg?.let(::formatBodyValue).orEmpty(),
            durationWeeksInput = stored?.durationWeeks?.toString().orEmpty(),
            storedGoal = stored
        )
    }

    private fun normalizedDecimalInput(value: String, integerDigits: Int): String {
        val normalized = value.replace(',', '.').filter { it.isDigit() || it == '.' }
        val firstDot = normalized.indexOf('.')
        return if (firstDot < 0) normalized.take(integerDigits) else {
            normalized.substring(0, firstDot).take(integerDigits) + "." +
                normalized.substring(firstDot + 1).replace(".", "").take(1)
        }
    }

    private fun formatBodyValue(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(java.util.Locale.US, "%.1f", value)
}

internal fun EnergySettingsUiState.draftValues(): List<Any?> = listOf(bmrInput, activityLevel, customPalInput, targetMode)
internal fun BodyProfileUiState.draftValues(): List<Any?> = listOf(sex, ageInput, heightInput, weightInput)
internal fun WeightGoalUiState.draftValues(): List<Any?> = listOf(targetWeightInput, durationWeeksInput, intakeTargetInput)
