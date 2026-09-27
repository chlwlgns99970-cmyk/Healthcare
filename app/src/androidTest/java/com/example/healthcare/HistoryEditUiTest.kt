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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.ui.screens.HistoryEditPane
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.MealEditUiState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HistoryEditUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun existingValuesCanBeEditedAndSaveBecomesSingleShot() {
        val original = MealRecord(
            id = 7,
            date = "2026-09-17",
            time = "08:30",
            mealType = MealType.BREAKFAST,
            foodName = "원래 음식",
            calories = 320,
            memo = "원래 메모",
            servingAmount = 1.0,
            servingUnit = "인분"
        )
        var state by mutableStateOf(
            MealEditUiState(
                original = original,
                foodName = original.foodName,
                calories = original.calories.toString(),
                mealType = original.mealType,
                date = LocalDate.parse(original.date),
                time = LocalTime.parse(original.time),
                servingAmount = "1",
                servingUnit = "인분",
                memo = original.memo.orEmpty()
            )
        )
        var saveCount = 0

        composeRule.setContent {
            HealthCareTheme {
                HistoryEditPane(
                    state = state,
                    onFoodNameChange = { state = state.copy(foodName = it) },
                    onCaloriesChange = { state = state.copy(calories = it) },
                    onMealTypeChange = { state = state.copy(mealType = it) },
                    onDateChange = { state = state.copy(date = it) },
                    onTimeChange = { state = state.copy(time = it) },
                    onServingAmountChange = { state = state.copy(servingAmount = it) },
                    onServingUnitChange = { state = state.copy(servingUnit = it) },
                    onMemoChange = { state = state.copy(memo = it) },
                    onSave = {
                        saveCount += 1
                        state = state.copy(isSaving = true)
                    },
                    onBack = {}
                )
            }
        }

        field("음식 이름").assertTextContains("원래 음식")
            .performTextReplacement("수정한 음식")
        field("칼로리").assertTextContains("320")
            .performTextReplacement("450")

        scrollTo(hasText("저녁"))
        composeRule.onNodeWithText("저녁").performClick()
        scrollTo(hasSetTextAction() and hasText("메모 (선택)", substring = true))
        field("메모 (선택)").performTextReplacement("수정한 메모")
        scrollTo(hasText("수정 저장"))
        composeRule.onNodeWithTag("history-edit-save").assertIsDisplayed().performClick()

        assertEquals(1, saveCount)
        composeRule.onNodeWithTag("history-edit-save").assertIsNotEnabled()
    }

    @Test
    fun lastFieldAndSaveRemainReachableWithKeyboardAt360DpAndLargeText() {
        val original = MealRecord(
            id = 9,
            date = "2026-09-17",
            time = "08:30",
            mealType = MealType.BREAKFAST,
            foodName = "김밥",
            calories = 280
        )
        var state by mutableStateOf(
            MealEditUiState(
                original = original,
                foodName = original.foodName,
                calories = "280",
                memo = "기존 메모"
            )
        )
        composeRule.setContent {
            HealthCareTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(360.dp).height(640.dp)) {
                        HistoryEditPane(
                            state = state,
                            onFoodNameChange = { state = state.copy(foodName = it) },
                            onCaloriesChange = { state = state.copy(calories = it) },
                            onMealTypeChange = { state = state.copy(mealType = it) },
                            onDateChange = { state = state.copy(date = it) },
                            onTimeChange = { state = state.copy(time = it) },
                            onServingAmountChange = { state = state.copy(servingAmount = it) },
                            onServingUnitChange = { state = state.copy(servingUnit = it) },
                            onMemoChange = { state = state.copy(memo = it) },
                            onSave = {},
                            onBack = {}
                        )
                    }
                }
            }
        }
        scrollTo(hasTestTag("history-edit-memo"))
        composeRule.onNodeWithTag("history-edit-memo").performTextReplacement("수정한 메모")
        scrollTo(hasTestTag("history-edit-save"))
        composeRule.onNodeWithTag("history-edit-save").assertIsDisplayed()
        composeRule.onNodeWithTag("history-edit-memo").performImeAction()
        composeRule.runOnIdle { assertEquals("수정한 메모", state.memo) }
    }

    private fun field(label: String) = composeRule.onNode(
        hasSetTextAction() and hasText(label, substring = true)
    )

    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
    }
}
