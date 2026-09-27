package com.example.healthcare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.healthcare.ui.screens.DashboardContent
import com.example.healthcare.ui.screens.ExerciseCoachScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.DashboardEnergyUiState
import com.example.healthcare.domain.ExerciseActivity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExerciseCoachUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun cardAppearsOnlyAboveTarget() {
        var intake by mutableStateOf(2000)
        var opened = false
        rule.setContent {
            HealthCareTheme {
                DashboardContent(
                    selectedDate = LocalDate.now(), meals = emptyList(), totalCalories = intake,
                    targetCalories = 2000, statusText = "상태", energyState = DashboardEnergyUiState(),
                    onPreviousDay = {}, onNextDay = {}, onAddRecord = {}, onOpenEnergySettings = {},
                    exerciseWeightKg = 70.0,
                    onOpenExerciseCoach = { opened = true }
                )
            }
        }
        rule.onNodeWithTag("exercise-over-character").assertDoesNotExist()
        rule.onNodeWithTag("exercise-coach-cta").assertDoesNotExist()
        intake = 2150
        rule.onNodeWithTag("exercise-over-character", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise-coach-cta").assertIsDisplayed()
        rule.onNodeWithText("오늘 목표보다 약 150 kcal 많아요").assertIsDisplayed()
        rule.onNodeWithTag("exercise-coach-cta").performClick()
        rule.runOnIdle { assert(opened) }
    }

    @Test fun detailsValidateWeightAndShowSixChoices() {
        var weight by mutableStateOf<Double?>(null)
        rule.setContent {
            HealthCareTheme { ExerciseCoachScreen(150, weight, { weight = it }, {}) }
        }
        rule.onNodeWithText("몸무게 저장").performClick()
        rule.onNodeWithText("20~300 kg 사이의 숫자를 입력해 주세요.").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextReplacement("70")
        rule.onNodeWithText("몸무게 저장").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("exercise-excess-section-HOME_EXERCISE"))
        rule.onNodeWithTag("exercise-excess-section-HOME_EXERCISE").assertIsDisplayed()
        rule.onNodeWithTag("exercise-excess-section-HOME_EXERCISE").performClick()
        assert(
            rule.onAllNodesWithText("집에서 편한 속도로 몸을 움직이는 정도예요.")
                .fetchSemanticsNodes().isNotEmpty()
        )
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("exercise-100-section"))
        rule.onNodeWithTag("exercise-100-section").assertIsDisplayed()
        ExerciseActivity.entries.forEach { activity ->
            rule.onNodeWithTag("exercise-100-section-${activity.name}").assertExists()
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("exercise-excess-section"))
        rule.onNodeWithTag("exercise-excess-section").assertIsDisplayed()
        ExerciseActivity.entries.forEach { activity ->
            rule.onNodeWithTag("exercise-excess-section-${activity.name}").assertExists()
        }
    }

    @Test fun detailsAlwaysShowHundredKcalButHideExcessSectionAtTarget() {
        rule.setContent {
            HealthCareTheme { ExerciseCoachScreen(0, 70.0, {}, {}) }
        }
        rule.onNodeWithTag("exercise-100-section").assertIsDisplayed()
        rule.onNodeWithTag("exercise-excess-summary").assertDoesNotExist()
        rule.onNodeWithTag("exercise-excess-section").assertDoesNotExist()
    }

    @Test fun compactHomeKeepsCharacterAndCtaAtCombinedLargeTextWithoutScrolling() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.3f)) {
                HealthCareTheme(appFontScale = 1.3f) {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        DashboardContent(
                            selectedDate = LocalDate.now(),
                            meals = emptyList(),
                            totalCalories = 2_180,
                            targetCalories = 2_000,
                            statusText = "180 kcal 초과",
                            energyState = DashboardEnergyUiState(),
                            onPreviousDay = {}, onNextDay = {}, onAddRecord = {},
                            onOpenEnergySettings = {}, exerciseWeightKg = 70.0,
                            onOpenExerciseCoach = {}
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("exercise-over-character", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise-coach-cta").assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size)
    }
}
