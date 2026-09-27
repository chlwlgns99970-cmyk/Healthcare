package com.example.healthcare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.ui.screens.PortionSelector
import com.example.healthcare.ui.screens.WellnessManualRecordScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordUiState
import com.example.healthcare.ui.viewmodel.CompanionFoodEntry
import org.junit.Rule
import org.junit.Test

class PortionSelectorUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun householdChoiceUpdatesResultAndSelectedSemanticsAt360Dp() {
        val rice = FoodItem(
            id = "test-rice", sourceType = "K-FIND", sourceFoodCode = "rice", name = "흰밥",
            normalizedName = "흰밥", category = "밥류", referenceAmount = 100.0, unit = "g",
            energyKcal = 140.0, servingDescription = "100g 기준", dataVersion = "test",
            createdAt = 0, updatedAt = 0
        )
        var state by mutableStateOf(AddRecordUiState(
            foodName = "흰밥", selectedFood = rice, selectedFoodItemId = rice.id,
            referenceCalories = 140, referenceServingAmount = 100.0, referenceServingUnit = "g",
            calories = "140"
        ))
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    PortionSelector(
                        state = state,
                        onPreset = { preset ->
                            val estimate = requireNotNull(PortionGuide.estimate(rice, preset))
                            state = state.copy(selectedPortion = preset, calories = estimate.calories.toString())
                        },
                        onReferenceRatio = { _, _ -> },
                        onUnknown = { state = state.copy(portionHelpOpen = true) },
                        onVessel = { state = state.copy(selectedVessel = it) },
                        onFraction = {},
                        onPrecise = { state = state.copy(preciseAmountOpen = true) }
                    )
                }
            }
        }
        composeRule.onNodeWithTag("portion-한 공기").performClick()
        composeRule.onNodeWithTag("portion-한 공기").assertIsSelected()
        composeRule.onNodeWithText("약 294 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("직접 양 입력").assertDoesNotExist()
    }

    @Test fun largeTextAndDarkThemeKeepPreciseOptionReachableAt360Dp() {
        val food = FoodItem(
            id = "test-rice", sourceType = "K-FIND", sourceFoodCode = "rice", name = "흰밥",
            normalizedName = "흰밥", category = "밥류", referenceAmount = 100.0, unit = "g",
            energyKcal = 140.0, servingDescription = "100g 기준", dataVersion = "test",
            createdAt = 0, updatedAt = 0
        )
        var preciseClicked = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme(darkTheme = true) {
                    LazyColumn(Modifier.width(360.dp).height(700.dp)) {
                        item {
                            PortionSelector(
                                state = AddRecordUiState(selectedFood = food, referenceCalories = 140),
                                onPreset = {}, onReferenceRatio = { _, _ -> }, onUnknown = {},
                                onVessel = {}, onFraction = {}, onPrecise = { preciseClicked = true }
                            )
                        }
                    }
                }
            }
        }
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("더 정확히 입력하기"))
        composeRule.onNodeWithText("더 정확히 입력하기").assertIsDisplayed().performClick()
        assert(preciseClicked)
    }

    @Test fun unknownAmountCanBeChosenByVesselAndFractionWithoutWeightField() {
        val rice = FoodItem(
            id = "test-rice", sourceType = "K-FIND", sourceFoodCode = "rice", name = "흰밥",
            normalizedName = "흰밥", category = "밥류", referenceAmount = 100.0, unit = "g",
            energyKcal = 140.0, servingDescription = "100g 기준", dataVersion = "test",
            createdAt = 0, updatedAt = 0
        )
        var state by mutableStateOf(AddRecordUiState(selectedFood = rice, referenceCalories = 140))
        composeRule.setContent {
            HealthCareTheme {
                LazyColumn(Modifier.width(360.dp).height(700.dp)) {
                    item {
                        PortionSelector(state, {}, { _, _ -> },
                            onUnknown = { state = state.copy(portionHelpOpen = true) },
                            onVessel = { state = state.copy(selectedVessel = it) },
                            onFraction = { fraction ->
                                val preset = requireNotNull(PortionGuide.visualEstimate(rice,
                                    requireNotNull(state.selectedVessel), fraction))
                                val estimate = requireNotNull(PortionGuide.estimate(rice, preset))
                                state = state.copy(selectedPortion = preset, calories = estimate.calories.toString(),
                                    portionHelpOpen = false)
                            },
                            onPrecise = {}
                        )
                    }
                }
            }
        }
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("잘 모르겠어요"))
        composeRule.onNodeWithText("잘 모르겠어요").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("밥그릇"))
        composeRule.onNodeWithTag("portion-밥그릇").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("절반 정도"))
        composeRule.onNodeWithTag("portion-절반 정도").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("약 147 kcal"))
        composeRule.onNodeWithText("약 147 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("직접 양 입력").assertDoesNotExist()
    }

    @Test fun directAmountFoodStaysUsableAt360DpAndLargeTextWithoutRelativeServingButtons() {
        val soup = FoodItem(
            id = "soup", sourceType = "K-FIND", sourceFoodCode = "D305-223200000-0001",
            name = "미역국_북어", normalizedName = "미역국북어", category = "국 및 탕류",
            referenceAmount = 100.0, unit = "g", energyKcal = 19.0,
            carbohydrateGrams = 1.04, proteinGrams = 2.46, fatGrams = 0.56,
            servingDescription = "100g 기준", dataVersion = "test", createdAt = 0, updatedAt = 0
        )
        val state = AddRecordUiState(
            foodName = "북어미역국", calories = "67", servingAmount = "350", servingUnit = "g",
            selectedFood = soup, selectedFoodItemId = soup.id, preciseAmountOpen = true,
            referenceCalories = 19, referenceServingAmount = 100.0, referenceServingUnit = "g"
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme {
                    Box(Modifier.width(360.dp).height(700.dp)) {
                        WellnessManualRecordScreen(
                            uiState = state, frequentFoods = emptyList(), favoriteFoods = emptyList(),
                            recentMeals = emptyList(), photoPath = null, onBack = {}, onSaved = {},
                            onPhotoRecord = {}, viewModel = null, guided = false
                        )
                    }
                }
            }
        }
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("실제 먹은 양"))
        composeRule.onNodeWithText("실제 먹은 양").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("direct-amount-input"))
        composeRule.onNodeWithTag("direct-amount-input").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("약 67 kcal"))
        composeRule.onNodeWithText("약 67 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("0.5배").assertDoesNotExist()
        composeRule.onNodeWithText("1배").assertDoesNotExist()
    }

    @Test fun companionFoodsShowSeparateBasisAndCombinedTotalAt360DpLargeText() {
        val ramen = FoodItem(
            id = "ramen", sourceType = "K-FIND", sourceFoodCode = "ramen", name = "라면_치즈",
            normalizedName = "라면치즈", category = "면 및 만두류", referenceAmount = 100.0, unit = "g",
            energyKcal = 99.0, servingDescription = "100g 기준", dataVersion = "test", createdAt = 0, updatedAt = 0
        )
        val rice = ramen.copy(id = "rice", sourceFoodCode = "rice", name = "쌀밥", normalizedName = "쌀밥",
            category = "밥류", energyKcal = 166.0)
        val ramenPortion = com.example.healthcare.domain.PortionPreset(
            id = "manual", label = "직접 입력 100g", amount = 100.0, unit = "g",
            estimationType = com.example.healthcare.domain.PortionEstimationType.MANUAL_AMOUNT,
            sourceReference = ramen.servingDescription, description = "직접 입력한 양"
        )
        val ricePortion = PortionGuide.presets(rice).first { it.label == "반 공기" }
        val state = AddRecordUiState(
            foodName = ramen.name, calories = "99", servingAmount = "100", servingUnit = "g",
            selectedFood = ramen, selectedFoodItemId = ramen.id, selectedPortion = ramenPortion,
            referenceCalories = 99, referenceServingAmount = 100.0, referenceServingUnit = "g",
            companionFoods = listOf(CompanionFoodEntry("rice-selection", rice, ricePortion, 174))
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        WellnessManualRecordScreen(
                            uiState = state, frequentFoods = emptyList(), favoriteFoods = emptyList(),
                            recentMeals = emptyList(), photoPath = null, onBack = {}, onSaved = {},
                            onPhotoRecord = {}, viewModel = null, guided = false
                        )
                    }
                }
            }
        }
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("같이 먹은 음식"))
        composeRule.onNodeWithText("같이 먹은 음식").assertIsDisplayed()
        composeRule.onNodeWithText("+ 밥 추가").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("모든 음식 총 예상"))
        composeRule.onNodeWithText("모든 음식 총 예상").assertIsDisplayed()
        composeRule.onNodeWithText("273 kcal").assertIsDisplayed()
    }
}
