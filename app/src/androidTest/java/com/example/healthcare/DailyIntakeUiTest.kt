package com.example.healthcare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.domain.DailyIntakeTimeline
import com.example.healthcare.ui.components.DailyIntakeStatusCard
import com.example.healthcare.ui.screens.HistoryListPane
import com.example.healthcare.ui.theme.HealthCareTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DailyIntakeUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val date = LocalDate.of(2026, 9, 20)
    private val goal = CalorieGoal(targetCalories = 2000, startDate = "2026-09-01")

    @Test
    fun statusCardExplainsOverBalancedUnderAndBmrWarning() {
        val caloriesState = mutableIntStateOf(2200)
        composeRule.setContent {
            val timeline = timeline(listOf(meal(date, caloriesState.intValue)), withBmr = true)
            HealthCareTheme(darkTheme = false) {
                DailyIntakeStatusCard(timeline.summary(date), timeline.streakEndingOn(date),
                    true, {}, {})
            }
        }
        val cases = listOf(
            Triple(2200, "목표보다 200 kcal 많아요", "포동 캐릭터 1일차"),
            Triple(1950, "목표에 가깝게 기록했어요", "균형 유지 1일차"),
            Triple(1700, "현재 기록은 목표보다 300 kcal 적어요", "가벼운 기록 1일차"),
            Triple(1200, "현재 기록은 BMR보다 300 kcal 낮아요", "하루 기록을 확인하고 다음 식사를 챙겨보세요")
        )
        cases.forEach { (calories, expected, second) ->
            composeRule.runOnIdle { caloriesState.intValue = calories }
            composeRule.onNodeWithText(expected).assertExists()
            composeRule.onNodeWithText(second, substring = true).assertExists()
        }
    }

    @Test
    fun calendarAndChartAt360Dp() = checkCompactWidth(360)

    @Test
    fun calendarAndChartAt390Dp() = checkCompactWidth(390)

    @Test
    fun calendarAndChartAt412Dp() = checkCompactWidth(412)

    private fun checkCompactWidth(width: Int) {
        val meals = listOf(meal(date.minusDays(2), 2100), meal(date, 1700))
        val timeline = timeline(meals)
        var selected: LocalDate? = null
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density, 1.3f)) {
                HealthCareTheme(darkTheme = false) {
                    Box(Modifier.width(width.dp).height(800.dp)) {
                        HistoryListPane(
                            selectedDate = date,
                            meals = listOf(meals.last()),
                            totalCalories = 1700,
                            targetCalories = 2000,
                            statusText = "목표까지 300kcal",
                            onDateSelected = { selected = it },
                            onItemClick = {},
                            timeline = timeline
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription("9월 18일, 목표 초과", substring = true)
            .performClick()
        composeRule.runOnIdle { assertEquals(date.minusDays(2), selected) }
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            androidx.compose.ui.test.hasText("최근 30일"))
        composeRule.onNodeWithText("최근 30일").performClick()
        composeRule.onNodeWithTag("intake-chart").assertExists()
        composeRule.onNodeWithContentDescription("8월 22일", substring = true).assertExists()
    }

    private fun timeline(meals: List<MealRecord>, withBmr: Boolean = false) =
        DailyIntakeTimeline.build(meals, listOf(goal), if (withBmr) listOf(EnergyProfileHistory(
            basalMetabolicRateKcal = 1500, activityLevelCode = ActivityLevel.LIGHT,
            palMultiplier = 1.55, targetMode = TargetMode.MANUAL,
            effectiveFromDate = "2026-09-01"
        )) else emptyList())

    private fun meal(date: LocalDate, calories: Int) = MealRecord(
        date = date.toString(), time = "12:00", mealType = MealType.LUNCH,
        foodName = "테스트 식사", calories = calories
    )
}
