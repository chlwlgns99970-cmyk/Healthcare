package com.example.healthcare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.RecommendationStage
import com.example.healthcare.ui.screens.MealPlanContent
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.MealPlanUiState
import com.example.healthcare.ui.viewmodel.RecommendedIngredientUi
import com.example.healthcare.ui.viewmodel.SelectedMealUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MealPlanConfirmationUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun recommendedAmountSavesDirectlyAndChangedAmountOpensExistingConfirmation() {
        val food = FoodItem("rice", "K-FIND", "rice", "흰밥", "흰밥", category = "밥류",
            referenceAmount = 100.0, unit = "g", energyKcal = 140.0,
            servingDescription = "100g 기준", dataVersion = "test", createdAt = 0, updatedAt = 0)
        val ingredient = RecommendedIngredientUi(1, food, 100.0, 100.0, "g", 140,
            adjustable = true, minimumAmount = null, maximumAmount = null, adjustmentStep = null)
        var state by mutableStateOf(MealPlanUiState(
            mealType = MealType.LUNCH,
            budgetKcal = 500,
            selectedMeal = SelectedMealUi("meal", "점심 식단", "", listOf(ingredient), 140)
        ))
        var saveCount = 0
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp).height(760.dp)) {
                    MealPlanContent(state, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> },
                        onConfirm = { saveCount++ },
                        onChangeAmount = { state = state.copy(showConsumptionConfirm = true) },
                        onSaveConsumption = { saveCount++ },
                        onConsumedRatio = { ratio ->
                            state = state.copy(consumedRatio = ratio,
                                selectedMeal = state.selectedMeal?.copy(
                                    ingredients = state.selectedMeal!!.ingredients.map { it.copy(consumedRatio = ratio) }
                                ))
                        }
                    )
                }
            }
        }
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("먹었어요"))
        composeRule.onNodeWithText("먹었어요").performClick()
        assertEquals(1, saveCount)
        composeRule.onNodeWithText("추천한 양만큼 드셨나요?").assertDoesNotExist()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("먹은 양이 달라요"))
        composeRule.onNodeWithText("먹은 양이 달라요").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("추천한 양만큼 드셨나요?"))
        composeRule.onNodeWithText("추천한 양만큼 드셨나요?").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("절반 정도 먹었어요"))
        composeRule.onNodeWithText("절반 정도 먹었어요").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("이 양으로 기록"))
        composeRule.onNodeWithText("약 70 kcal", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("이 양으로 기록").performClick()
        assertEquals(2, saveCount)
    }

    @Test fun emptyRecommendationOffersChangedConditionAndDirectSearchAt360Dp() {
        var widened = 0
        var searches = 0
        var settings = 0
        composeRule.setContent {
            HealthCareTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                Box(Modifier.width(360.dp).height(760.dp)) {
                    MealPlanContent(
                        state = MealPlanUiState(mealType = MealType.DINNER, budgetKcal = 600,
                            dailyRemainingKcal = 2000, hasLoaded = true, templateCount = 4,
                            verifiedCandidateCount = 4, stage = RecommendationStage.EXACT),
                        onBack = {}, onRefresh = {}, onSelect = {}, onAdjustPortion = {},
                        onIngredientIncluded = { _, _ -> }, onShowReplacements = {},
                        onReplace = { _, _ -> }, onConfirm = {},
                        onAdvanceFallback = { widened++ }, onSearchFood = { searches++ },
                        onOpenPreferences = { settings++ }
                    )
                }
                }
            }
        }
        composeRule.onNodeWithText("식사 추천").assertExists()
        composeRule.onNodeWithText("딱 맞는 식단은 없어요").assertExists()
        composeRule.onNodeWithText("조건을 유지하고 다시 찾기").assertDoesNotExist()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("칼로리 범위 넓혀 보기"))
        composeRule.onNodeWithText("칼로리 범위 넓혀 보기").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("음식 검색으로 직접 기록"))
        composeRule.onNodeWithText("음식 검색으로 직접 기록").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("추천 조건 설정"))
        composeRule.onNodeWithText("추천 조건 설정").performClick()
        assertEquals(1, widened)
        assertEquals(1, searches)
        assertEquals(1, settings)
    }
}
