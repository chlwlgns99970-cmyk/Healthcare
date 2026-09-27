package com.example.healthcare

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.domain.BodySex
import com.example.healthcare.domain.WeightGoalCalculator
import com.example.healthcare.ui.screens.BodyProfileCard
import com.example.healthcare.ui.screens.DashboardContent
import com.example.healthcare.ui.screens.EnergySettingsCard
import com.example.healthcare.ui.screens.WeightGoalCard
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.DashboardEnergyUiState
import com.example.healthcare.ui.viewmodel.EnergySettingsUiState
import com.example.healthcare.ui.viewmodel.BodyProfileUiState
import com.example.healthcare.ui.viewmodel.WeightGoalUiState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class EnergyUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsAcceptsBmrActivityAndShowsMaintenancePreview() {
        var state by mutableStateOf(EnergySettingsUiState())
        composeRule.setContent {
            HealthCareTheme {
                LazyColumn {
                    item {
                        EnergySettingsCard(
                            state = state,
                            onBmrChange = { value ->
                                state = state.copy(
                                    bmrInput = value,
                                    maintenancePreviewKcal = if (value == "1500" && state.activityLevel == ActivityLevel.LIGHT) 2325 else null
                                )
                            },
                            onActivityLevelSelected = { level ->
                                state = state.copy(
                                    activityLevel = level,
                                    maintenancePreviewKcal = if (state.bmrInput == "1500" && level == ActivityLevel.LIGHT) 2325 else null
                                )
                            },
                            onCustomPalChange = { state = state.copy(customPalInput = it) },
                            onTargetModeSelected = { state = state.copy(targetMode = it) },
                            onSave = {}
                        )
                    }
                }
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasSetTextAction() and hasText("기초대사량", substring = true)
        )
        composeRule.onNode(hasSetTextAction() and hasText("기초대사량", substring = true))
            .performTextInput("1500")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("가벼운 활동"))
        composeRule.onNodeWithText("가벼운 활동").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("2,325"))
        composeRule.onNodeWithText("2,325").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("활동량 포함 유지 기준"))
        composeRule.onNodeWithText("활동량 포함 유지 기준").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("에너지 기준 저장"))
        composeRule.onNodeWithText("에너지 기준 저장").assertIsDisplayed()
    }

    @Test
    fun dashboardKeepsEnergySummaryAndCalendarReachableInLightAndDarkThemes() {
        var configured by mutableStateOf(false)
        composeRule.setContent {
            HealthCareTheme(darkTheme = configured) {
                DashboardContent(
                    selectedDate = LocalDate.of(2026, 9, 16),
                    meals = emptyList(),
                    totalCalories = if (configured) 2625 else 0,
                    targetCalories = if (configured) 2325 else 2000,
                    statusText = if (configured) "목표 대비 +300kcal" else "목표까지 2,000kcal",
                    energyState = if (configured) configuredEnergyState() else DashboardEnergyUiState(),
                    onPreviousDay = {},
                    onNextDay = {},
                    onAddRecord = {},
                    onOpenEnergySettings = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("날짜 선택").assertIsDisplayed()
        composeRule.runOnIdle { configured = true }
        composeRule.onNodeWithText("2,625").assertIsDisplayed()
        composeRule.onNodeWithText("/ 2,325 kcal").assertIsDisplayed()
    }

    @Test
    fun energyInputsExposeHeadingsErrorsAndSelectedStates() {
        val state = EnergySettingsUiState(
            bmrInput = "",
            bmrError = "기초대사량을 입력해 주세요.",
            activityLevel = ActivityLevel.CUSTOM,
            customPalInput = "2.41",
            customPalError = "PAL은 1.40 이상 2.40 이하로 입력해 주세요.",
            targetMode = TargetMode.MAINTENANCE
        )
        composeRule.setContent {
            HealthCareTheme {
                LazyColumn {
                    item {
                        EnergySettingsCard(
                            state = state,
                            onBmrChange = {},
                            onActivityLevelSelected = {},
                            onCustomPalChange = {},
                            onTargetModeSelected = {},
                            onSave = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("나의 에너지 기준")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

        val bmr = hasSetTextAction() and hasText("기초대사량", substring = true)
        composeRule.onNode(hasScrollAction()).performScrollToNode(bmr)
        composeRule.onNode(bmr)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("직접 설정"))
        composeRule.onNodeWithText("직접 설정").assertIsSelected()

        val customPal = hasSetTextAction() and hasText("나만의 활동 계수", substring = true)
        composeRule.onNode(hasScrollAction()).performScrollToNode(customPal)
        composeRule.onNode(customPal)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("활동량 포함 유지 기준"))
        composeRule.onNodeWithText("활동량 포함 유지 기준").assertIsSelected()
    }

    @Test
    fun bodyProfileAndEstimatedBmrRemainReachableAt360DpAndLargeText() {
        var useClicked = false
        val state = BodyProfileUiState(
            sex = BodySex.MALE,
            ageInput = "35",
            heightInput = "175",
            weightInput = "70.4",
            estimatedBmrKcal = 1628
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme {
                    LazyColumn(Modifier.width(360.dp).height(800.dp)) {
                        item {
                            BodyProfileCard(state, {}, {}, {}, {}, {}, { useClicked = true })
                        }
                    }
                }
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("1,628", substring = true))
        composeRule.onNodeWithText("1,628", substring = true).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("BMR에 사용"))
        composeRule.onNodeWithText("BMR에 사용").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assert(useClicked) }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("저장하기"))
        composeRule.onNodeWithText("저장하기").assertIsDisplayed()
    }

    @Test
    fun extremeWeightGoalExplainsStatusBeforeDeficitAt360DpAndExtraLargeText() {
        val calculation = WeightGoalCalculator.calculate(81.0, 69.0, 4, 1550, 1000).getOrThrow()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme(appFontScale = 1.3f) {
                    LazyColumn(Modifier.width(360.dp).height(800.dp)) {
                        item {
                            WeightGoalCard(
                                state = WeightGoalUiState(
                                    targetWeightInput = "69",
                                    durationWeeksInput = "4",
                                    calculation = calculation
                                ),
                                currentWeightText = "81",
                                energyState = EnergySettingsUiState(
                                    bmrInput = "1000",
                                    activityLevel = ActivityLevel.LIGHT,
                                    maintenancePreviewKcal = 1550
                                ),
                                onTargetWeightChange = {},
                                onWeeksChange = {},
                                onCalculate = {},
                                onApply = {}
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("이 기간은 너무 짧아요"))
        composeRule.onNodeWithText("이 기간은 너무 짧아요").assertIsDisplayed()
        composeRule.onNodeWithText("-1,750", substring = true).assertDoesNotExist()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("계산 방법 보기"))
        composeRule.onNodeWithText("계산 방법 보기").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("왜 3,300 kcal인가요?"))
        composeRule.onNodeWithText("왜 3,300 kcal인가요?").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("먹는 칼로리가 아니라", substring = true))
        composeRule.onNode(hasText("먹는 칼로리가 아니라", substring = true)).assertIsDisplayed()
    }

    @Test
    fun intakePaceHighlightsTimeForOneKilogram() {
        val pace = WeightGoalCalculator.calculateIntakePace(2200, 1500, 1900).getOrThrow()
        composeRule.setContent {
            HealthCareTheme {
                LazyColumn(Modifier.width(360.dp).height(800.dp)) {
                    item {
                        WeightGoalCard(
                            state = WeightGoalUiState(
                                intakeTargetInput = "1900",
                                intakePaceCalculation = pace
                            ),
                            currentWeightText = "81",
                            energyState = EnergySettingsUiState(
                                bmrInput = "1500",
                                activityLevel = ActivityLevel.LIGHT,
                                maintenancePreviewKcal = 2200
                            ),
                            onTargetWeightChange = {},
                            onWeeksChange = {},
                            onCalculate = {},
                            onApply = {}
                        )
                    }
                }
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("약 3주 5일"))
        composeRule.onNodeWithText("약 3주 5일").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("약 26일"))
        composeRule.onNodeWithText("약 26일").assertIsDisplayed()
    }

    @Test
    fun intakeSurplusHighlightsEquivalentTimeWithoutGuaranteeingWeightGain() {
        val pace = WeightGoalCalculator.calculateIntakePace(2200, 1500, 2500).getOrThrow()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme(appFontScale = 1.3f) {
                    LazyColumn(Modifier.width(360.dp).height(800.dp)) {
                        item {
                            WeightGoalCard(
                                state = WeightGoalUiState(
                                    intakeTargetInput = "2500",
                                    intakePaceCalculation = pace
                                ),
                                currentWeightText = "81",
                                energyState = EnergySettingsUiState(
                                    bmrInput = "1500",
                                    activityLevel = ActivityLevel.LIGHT,
                                    maintenancePreviewKcal = 2200
                                ),
                                onTargetWeightChange = {},
                                onWeeksChange = {},
                                onCalculate = {},
                                onApply = {}
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("1kg 증가 상당까지"))
        composeRule.onNodeWithText("1kg 증가 상당까지").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("약 3주 5일"))
        composeRule.onNodeWithText("약 3주 5일").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("하루 약 300 kcal가 유지 예상보다 많아요."))
        composeRule.onNodeWithText("하루 약 300 kcal가 유지 예상보다 많아요.").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("실제 체중 변화는 달라질 수 있습니다."))
        composeRule.onNodeWithText("실제 체중 변화는 달라질 수 있습니다.").assertIsDisplayed()
    }

    @Test
    fun equalIntakeAndMaintenanceDoNotShowAWeightChangeDuration() {
        val pace = WeightGoalCalculator.calculateIntakePace(2200, 1500, 2200).getOrThrow()
        composeRule.setContent {
            HealthCareTheme {
                LazyColumn(Modifier.width(360.dp).height(800.dp)) {
                    item {
                        WeightGoalCard(
                            state = WeightGoalUiState(
                                intakeTargetInput = "2200",
                                intakePaceCalculation = pace
                            ),
                            currentWeightText = "81",
                            energyState = EnergySettingsUiState(
                                bmrInput = "1500",
                                activityLevel = ActivityLevel.LIGHT,
                                maintenancePreviewKcal = 2200
                            ),
                            onTargetWeightChange = {},
                            onWeeksChange = {},
                            onCalculate = {},
                            onApply = {}
                        )
                    }
                }
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("현재 설정은 유지 예상과 같아요."))
        composeRule.onNodeWithText("현재 설정은 유지 예상과 같아요.").assertIsDisplayed()
        composeRule.onNodeWithText("1kg 감량까지").assertDoesNotExist()
        composeRule.onNodeWithText("1kg 증가 상당까지").assertDoesNotExist()
    }

    private fun configuredEnergyState() = DashboardEnergyUiState(
        profile = EnergyProfileHistory(
            basalMetabolicRateKcal = 1500,
            activityLevelCode = ActivityLevel.LIGHT,
            palMultiplier = 1.55,
            targetMode = TargetMode.MAINTENANCE,
            effectiveFromDate = "2026-09-16"
        ),
        manualTargetCalories = 2000,
        effectiveTargetCalories = 2325,
        intakeCalories = 2625,
        maintenanceCalories = 2325,
        dailySurplusCalories = 300,
        sevenDayEquivalentKg = 0.2727,
        thirtyDayEquivalentKg = 1.1688
    )
}
