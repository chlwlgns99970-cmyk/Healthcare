package com.example.healthcare

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.domain.PortionEstimationType
import com.example.healthcare.domain.PortionPreset
import com.example.healthcare.ui.screens.WellnessManualRecordScreen
import com.example.healthcare.ui.screens.QuickFoodRecordScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordUiState
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    @Test
    fun quickRecordShowsLockedMealSaveFavoriteAndDetailsAt360DpLargeText() {
        val food = FoodItem(
            id = "tuna-gimbap", sourceType = "K-FIND-PRODUCT", sourceFoodCode = "tuna-gimbap",
            name = "참치김밥", normalizedName = "참치김밥", category = "밥류",
            referenceAmount = 180.0, unit = "g", energyKcal = 420.0,
            carbohydrateGrams = 62.0, proteinGrams = 14.0, fatGrams = 12.0,
            servingDescription = "공식 총내용량 180g · 포장단위 줄", brand = "검수 브랜드",
            dataVersion = "test", createdAt = 0, updatedAt = 0
        )
        val portion = PortionPreset(
            id = "package-1", label = "1줄", amount = 180.0, unit = "g",
            estimationType = PortionEstimationType.PACKAGE_LABEL,
            sourceReference = "공식 영양정보", description = "공식 총내용량"
        )
        var detailClicks = 0
        composeRule.setContent {
            HealthCareTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(360.dp).height(640.dp)) {
                        QuickFoodRecordScreen(
                            state = AddRecordUiState(
                                foodName = "참치김밥",
                                calories = "420",
                                servingAmount = "180",
                                servingUnit = "g",
                                mealType = MealType.BREAKFAST,
                                selectedFoodItemId = food.id,
                                selectedFood = food,
                                selectedPortion = portion
                            ),
                            favoriteFoods = emptyList(),
                            recentMeals = emptyList(),
                            mealTypeLocked = true,
                            onBack = {},
                            onDetails = { detailClicks++ },
                            onSaved = {},
                            viewModel = null
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("참치김밥").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("참치김밥 즐겨찾기 추가").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("아침에 기록"))
        composeRule.onNodeWithText("아침에 기록").assertIsDisplayed()
        composeRule.onAllNodesWithText("점심").assertCountEquals(0)
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("자세히 입력"))
        composeRule.onNodeWithText("자세히 입력").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, detailClicks) }
    }

    @Test
    fun quickRecordSaveButtonPersistsExactlyOneBreakfastRecord() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val viewModel = AddRecordViewModel(
            MealRepository(database.mealRecordDao()),
            FoodRepository(database.frequentFoodDao()),
            nutritionRepository = NutritionRepository(database.foodItemDao())
        )
        val food = FoodItem(
            id = "quick-save", sourceType = "K-FIND-PRODUCT", sourceFoodCode = "quick-save",
            name = "참치김밥", normalizedName = "참치김밥", category = "밥류",
            referenceAmount = 180.0, unit = "g", energyKcal = 420.0,
            servingDescription = "100g 기준 · 공식 총내용량 180g · 포장단위 줄",
            dataVersion = "test", createdAt = 0, updatedAt = 0
        )
        viewModel.onMealTypeChange(MealType.BREAKFAST)
        viewModel.selectVerifiedFood(food)
        viewModel.selectPortionPreset(requireNotNull(com.example.healthcare.domain.PortionGuide.defaultPreset(food)))
        var successMealType: MealType? = null
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            HealthCareTheme {
                QuickFoodRecordScreen(
                    state = state,
                    favoriteFoods = emptyList(),
                    recentMeals = emptyList(),
                    mealTypeLocked = true,
                    onBack = {},
                    onDetails = {},
                    onSaved = { successMealType = it },
                    viewModel = viewModel
                )
            }
        }

        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("아침에 기록"))
        composeRule.onNodeWithText("아침에 기록").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { database.mealRecordDao().getAllMeals().first().size == 1 }
        }
        val saved = runBlocking { database.mealRecordDao().getAllMeals().first() }
        assertEquals(1, saved.size)
        assertEquals(MealType.BREAKFAST, saved.single().mealType)
        assertEquals("참치김밥", saved.single().foodName)
        assertEquals(MealType.BREAKFAST, successMealType)
    }
}
