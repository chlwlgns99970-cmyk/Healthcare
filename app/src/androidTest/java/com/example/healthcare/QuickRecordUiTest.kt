package com.example.healthcare

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.PortionEstimationType
import com.example.healthcare.domain.PortionPreset
import com.example.healthcare.ui.screens.WellnessManualRecordScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordUiState
import org.junit.Rule
import org.junit.Test

class QuickRecordUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lockedBreakfastContextSkipsMealReselectionAndGoesFromAmountToConfirmation() {
        val food = FoodItem(
            id = "rice", sourceType = "K-FIND", sourceFoodCode = "rice",
            name = "흰밥", normalizedName = "흰밥", category = "밥류",
            referenceAmount = 100.0, unit = "g", energyKcal = 140.0,
            servingDescription = "100g 기준", dataVersion = "test", createdAt = 0, updatedAt = 0
        )
        val portion = PortionPreset(
            id = "rice-one", label = "한 공기", amount = 210.0, unit = "g",
            estimationType = PortionEstimationType.HOUSEHOLD_UNIT,
            sourceReference = "식품안전나라", description = "공식 생활 단위"
        )
        composeRule.setContent {
            HealthCareTheme {
                WellnessManualRecordScreen(
                    uiState = AddRecordUiState(
                        foodName = "흰밥", calories = "294", servingAmount = "210",
                        servingUnit = "g", mealType = MealType.BREAKFAST,
                        selectedFood = food, selectedPortion = portion
                    ),
                    frequentFoods = emptyList(), favoriteFoods = emptyList(), recentMeals = emptyList(),
                    photoPath = null, onBack = {}, onSaved = {}, onPhotoRecord = {}, viewModel = null,
                    guided = true, mealTypeLocked = true
                )
            }
        }

        composeRule.onNodeWithText("2 / 3  먹은 양").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("기록 확인"))
        composeRule.onNodeWithText("기록 확인").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("아침에 기록"))
        composeRule.onNodeWithText("아침에 기록").assertIsDisplayed()
        composeRule.onAllNodesWithText("점심").assertCountEquals(0)
        composeRule.onAllNodesWithText("다음 · 식사 시간").assertCountEquals(0)
    }
}
