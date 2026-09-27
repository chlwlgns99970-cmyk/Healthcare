package com.example.healthcare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.healthcare.ui.TopLevelDestination
import com.example.healthcare.ui.components.FloatingNavigationDock
import com.example.healthcare.ui.screens.CalorieSummaryCard
import com.example.healthcare.ui.screens.WellnessManualRecordScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorialUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun floatingDockHasFiveDestinationsAndCentralRecordActionAt360Dp() {
        var selected by mutableStateOf(TopLevelDestination.DASHBOARD)
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp)) {
                    FloatingNavigationDock(selected) { selected = it }
                }
            }
        }
        listOf("홈", "추천", "기록", "통계", "설정").forEach {
            composeRule.onAllNodesWithText(it).onFirst().assertIsDisplayed()
        }
        composeRule.onNodeWithText("기록").performClick()
        composeRule.runOnIdle { assertEquals(TopLevelDestination.ADD, selected) }
    }

    @Test
    fun guidedRecordKeepsEveryStepReachableAt360DpAndLargeText() {
        composeRule.setContent {
            HealthCareTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        WellnessManualRecordScreen(
                            uiState = AddRecordUiState(foodName = "김밥", calories = "280"),
                            frequentFoods = emptyList(),
                            favoriteFoods = emptyList(),
                            recentMeals = emptyList(),
                            photoPath = null,
                            onBack = {},
                            onSaved = {},
                            onPhotoRecord = {},
                            viewModel = null,
                            guided = true
                        )
                    }
                }
            }
        }
        clickAfterScroll("다음 · 먹은 양")
        clickAfterScroll("다음 · 식사 시간")
        clickAfterScroll("기록 확인")
        composeRule.onNodeWithText("이대로 기록할까요?").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("기록 저장"))
        composeRule.onNodeWithText("기록 저장").assertIsDisplayed()
    }

    @Test
    fun directRecordSaveRemainsReachableAfterFocusingLastFieldAt360DpAndLargeText() {
        checkSaveWithFocusedMemo(photoPath = null)
    }

    @Test
    fun photoRecordSaveRemainsReachableAfterFocusingLastFieldAt360DpAndLargeText() {
        checkSaveWithFocusedMemo(photoPath = "preview.jpg")
    }

    @Test
    fun todayHeroSeparatesIntakeFromGoalAndRemaining() {
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp)) {
                    CalorieSummaryCard(total = 1420, target = 2000,
                        statusText = "목표까지 580 kcal", recordCount = 3)
                }
            }
        }
        composeRule.onNodeWithText("580").assertIsDisplayed()
        composeRule.onNodeWithText("kcal 남음").assertIsDisplayed()
        composeRule.onNodeWithText("1,420 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("2,000 kcal").assertIsDisplayed()
    }

    @Test
    fun todayHeroChangesToOverStateWithoutChangingItsStructure() {
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp)) {
                    CalorieSummaryCard(total = 2250, target = 2000,
                        statusText = "목표 대비 +250kcal", recordCount = 4)
                }
            }
        }
        composeRule.onNodeWithText("+250").assertIsDisplayed()
        composeRule.onNodeWithText("kcal 초과").assertIsDisplayed()
        composeRule.onNodeWithText("2,250 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("2,000 kcal").assertIsDisplayed()
    }

    private fun clickAfterScroll(text: String) {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).performClick()
    }

    private fun checkSaveWithFocusedMemo(photoPath: String?) {
        composeRule.setContent {
            HealthCareTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(360.dp).height(640.dp)) {
                        WellnessManualRecordScreen(
                            uiState = AddRecordUiState(foodName = "김밥", calories = "280"),
                            frequentFoods = emptyList(),
                            favoriteFoods = emptyList(),
                            recentMeals = emptyList(),
                            photoPath = photoPath,
                            onBack = {},
                            onSaved = {},
                            onPhotoRecord = {},
                            viewModel = null,
                            guided = false
                        )
                    }
                }
            }
        }
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("manual-record-memo"))
        composeRule.onNodeWithTag("manual-record-memo").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("manual-record-save"))
        repeat(2) {
            composeRule.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onNodeWithTag("manual-record-save").isDisplayed()
        }
        composeRule.onNodeWithTag("manual-record-save").assertIsDisplayed()
        composeRule.onNodeWithTag("manual-record-memo").performImeAction()
    }
}
