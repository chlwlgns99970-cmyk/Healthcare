package com.example.healthcare

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.entity.FoodBrandSummary
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.repository.MealRecommendationWithIngredients
import com.example.healthcare.domain.ScoredMealRecommendation
import com.example.healthcare.ui.screens.DashboardContent
import com.example.healthcare.ui.screens.MealPlanContent
import com.example.healthcare.ui.screens.SmartFoodInputScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.DashboardEnergyUiState
import com.example.healthcare.ui.viewmodel.FoodSearchMode
import com.example.healthcare.ui.viewmodel.MealPlanUiState
import com.example.healthcare.ui.viewmodel.SmartInputUiState
import com.example.healthcare.ui.viewmodel.SmartInputMode
import com.example.healthcare.ui.viewmodel.TodayCoachUiState
import java.time.LocalDate
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SmartCoachUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smartInputStartsWithAutomaticOptionsAndKeepsManualAsFallback() {
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    SmartFoodInputScreen(
                        state = SmartInputUiState(photoAnalysisAvailable = false),
                        recentMeals = emptyList(),
                        onBack = {},
                        onPhoto = {},
                        onBarcode = {},
                        onNutritionLabel = {},
                        onSearch = {},
                        onSearchQueryChange = {},
                        onFoodSelected = {},
                        onUseBarcodeItem = {},
                        onOcrCandidateSelected = {},
                        onOcrAmountChange = {},
                        onConfirmOcr = {},
                        onManual = {},
                        onRegisterBarcode = {},
                        onRepeatRecent = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("사진으로 기록").assertIsDisplayed()
        composeRule.onNodeWithText("바코드 스캔").assertIsDisplayed()
        composeRule.onNodeWithText("직접 입력").assertIsDisplayed()
    }

    @Test
    fun solidVolumeCandidateDoesNotReadAsUserServing() {
        fun food(id: String, unit: String, kcal: Double) = FoodItem(
            id = id, sourceType = "K-FIND", sourceFoodCode = id, name = "김밥_참치",
            normalizedName = "김밥참치", category = "밥류", referenceAmount = 100.0,
            unit = unit, energyKcal = kcal, servingDescription = "100$unit 기준",
            dataVersion = "test", createdAt = 0, updatedAt = 0
        )
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    SmartFoodInputScreen(
                        state = SmartInputUiState(mode = SmartInputMode.SEARCH, searchQuery = "참치김밥",
                            searchResults = listOf(food("g", "g", 174.0), food("ml", "ml", 128.0))),
                        recentMeals = emptyList(), onBack = {}, onPhoto = {}, onBarcode = {},
                        onNutritionLabel = {}, onSearch = {}, onSearchQueryChange = {},
                        onFoodSelected = {}, onUseBarcodeItem = {}, onOcrCandidateSelected = {},
                        onOcrAmountChange = {}, onConfirmOcr = {}, onManual = {},
                        onRegisterBarcode = {}, onRepeatRecent = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText("영양정보 100g 기준 · 약 174 kcal").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("영양정보 단위 확인 필요"))
        composeRule.onNodeWithText("영양정보 단위 확인 필요").assertIsDisplayed()
        composeRule.onNodeWithText("100ml 기준", substring = true).assertDoesNotExist()
    }

    @Test
    fun emptySearchOffersSafeBroaderTermAndManualEntry() {
        var suggestedQuery = ""
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    SmartFoodInputScreen(
                        state = SmartInputUiState(mode = SmartInputMode.SEARCH, searchQuery = "참치김밥"),
                        recentMeals = emptyList(), onBack = {}, onPhoto = {}, onBarcode = {},
                        onNutritionLabel = {}, onSearch = {}, onSearchQueryChange = { suggestedQuery = it },
                        onFoodSelected = {}, onUseBarcodeItem = {}, onOcrCandidateSelected = {},
                        onOcrAmountChange = {}, onConfirmOcr = {}, onManual = {},
                        onRegisterBarcode = {}, onRepeatRecent = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText("검색 결과가 없어요").assertIsDisplayed()
        composeRule.onNodeWithText("‘김밥’ 결과 보기").performClick()
        assertEquals("김밥", suggestedQuery)
        composeRule.onNodeWithText("직접 입력으로 전환").assertIsDisplayed()
    }

    @Test
    fun brandBrowseShowsOfficialCompanyNameAndCountAtLargeText() {
        var selectedBrand = ""
        val longCompanyName = "주식회사 아주 긴 공식 제조사 이름 식품 제조공장"
        composeRule.setContent {
            HealthCareTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        SmartFoodInputScreen(
                            state = SmartInputUiState(
                                mode = SmartInputMode.SEARCH,
                                searchMode = FoodSearchMode.BRAND,
                                searchQuery = "식품",
                                brandResults = listOf(FoodBrandSummary(longCompanyName, 32))
                            ),
                            recentMeals = emptyList(), onBack = {}, onPhoto = {}, onBarcode = {},
                            onNutritionLabel = {}, onSearch = {}, onSearchQueryChange = {},
                            onFoodSelected = {}, onUseBarcodeItem = {}, onOcrCandidateSelected = {},
                            onOcrAmountChange = {}, onConfirmOcr = {}, onManual = {},
                            onRegisterBarcode = {}, onRepeatRecent = {},
                            onBrandSelected = { selectedBrand = it.brand }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("브랜드·제조사").assertIsDisplayed()
        composeRule.onNodeWithText(longCompanyName).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(longCompanyName, selectedBrand) }
        composeRule.onNodeWithText("제품 32개").assertIsDisplayed()
    }

    @Test
    fun selectedBrandProductReusesExistingProductSelectionFlow() {
        var selectedFoodId = ""
        val company = FoodBrandSummary("(주)검수식품", 1)
        val burger = FoodItem(
            id = "burger-product", sourceType = "K-FIND-PRODUCT", sourceFoodCode = "burger-product",
            name = "아주 긴 제품명을 가진 불고기 햄버거", normalizedName = "아주긴제품명을가진불고기햄버거",
            aliases = "|불고기햄버거|검수식품|", category = "즉석식품류", referenceAmount = 100.0,
            unit = "g", energyKcal = 250.0,
            servingDescription = "100g 기준 · 공식 총내용량 180g", brand = company.brand,
            dataVersion = "test", createdAt = 0, updatedAt = 0
        )
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    SmartFoodInputScreen(
                        state = SmartInputUiState(
                            mode = SmartInputMode.SEARCH,
                            searchMode = FoodSearchMode.BRAND,
                            selectedBrand = company,
                            brandProducts = listOf(burger),
                            brandCategories = listOf("즉석식품류")
                        ),
                        recentMeals = emptyList(), onBack = {}, onPhoto = {}, onBarcode = {},
                        onNutritionLabel = {}, onSearch = {}, onSearchQueryChange = {},
                        onFoodSelected = { selectedFoodId = it.id }, onUseBarcodeItem = {},
                        onOcrCandidateSelected = {}, onOcrAmountChange = {}, onConfirmOcr = {},
                        onManual = {}, onRegisterBarcode = {}, onRepeatRecent = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("공식 데이터에 등록된 제품 1개").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("제품 전체 180g · 450 kcal"))
        composeRule.onNodeWithText("제품 전체 180g · 450 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("아주 긴 제품명을 가진 불고기 햄버거").performClick()
        composeRule.runOnIdle { assertEquals("burger-product", selectedFoodId) }
    }

    @Test
    fun missingBrandUsesExplicitEmptyState() {
        composeRule.setContent {
            HealthCareTheme {
                SmartFoodInputScreen(
                    state = SmartInputUiState(
                        mode = SmartInputMode.SEARCH,
                        searchMode = FoodSearchMode.BRAND,
                        searchQuery = "없는업체"
                    ),
                    recentMeals = emptyList(), onBack = {}, onPhoto = {}, onBarcode = {},
                    onNutritionLabel = {}, onSearch = {}, onSearchQueryChange = {},
                    onFoodSelected = {}, onUseBarcodeItem = {}, onOcrCandidateSelected = {},
                    onOcrAmountChange = {}, onConfirmOcr = {}, onManual = {},
                    onRegisterBarcode = {}, onRepeatRecent = {}
                )
            }
        }
        composeRule.onNodeWithText("해당 브랜드·제조사를 찾지 못했어요").assertIsDisplayed()
    }

    @Test
    fun dashboardShowsNextMealBudgetAndNonFastingExceededMessage() {
        composeRule.setContent {
            HealthCareTheme {
                DashboardContent(
                    selectedDate = LocalDate.now(),
                    meals = emptyList(),
                    totalCalories = 2200,
                    targetCalories = 2000,
                    statusText = "목표 대비 +200kcal",
                    energyState = DashboardEnergyUiState(intakeCalories = 2200),
                    coachState = TodayCoachUiState(
                        targetCalories = 2000,
                        intakeCalories = 2200,
                        remainingCalories = 0,
                        remainingMeals = listOf(MealType.DINNER),
                        nextMealType = MealType.DINNER,
                        nextMealBudgetKcal = null,
                        targetExceeded = true
                    ),
                    onPreviousDay = {},
                    onNextDay = {},
                    onAddRecord = {},
                    onOpenEnergySettings = {},
                    onOpenMealPlan = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("다음 식사는 가볍게").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun dashboardKeepsRecordAndRecommendationActionsObvious() {
        var recordOpened = false
        var recommendationOpened = false
        composeRule.setContent {
            HealthCareTheme {
                DashboardContent(
                    selectedDate = LocalDate.now(),
                    meals = listOf(
                        MealRecord(date = LocalDate.now().toString(), time = "08:00", mealType = MealType.BREAKFAST,
                            foodName = "아침 식사", calories = 420),
                        MealRecord(date = LocalDate.now().toString(), time = "12:00", mealType = MealType.LUNCH,
                            foodName = "점심 식사", calories = 900)
                    ),
                    totalCalories = 1320,
                    targetCalories = 2000,
                    statusText = "목표까지 680kcal",
                    energyState = DashboardEnergyUiState(intakeCalories = 1320),
                    coachState = TodayCoachUiState(
                        targetCalories = 2000,
                        intakeCalories = 1320,
                        remainingCalories = 680,
                        remainingMeals = listOf(MealType.DINNER),
                        nextMealType = MealType.DINNER,
                        nextMealBudgetKcal = 550
                    ),
                    onPreviousDay = {},
                    onNextDay = {},
                    onAddRecord = { recordOpened = true },
                    onOpenEnergySettings = {},
                    onOpenMealPlan = { _, _ -> recommendationOpened = true }
                )
            }
        }

        composeRule.onNodeWithText("추천 메뉴를 확인해 보세요").performClick()
        composeRule.runOnIdle { assertEquals(true, recommendationOpened) }
        composeRule.onNodeWithContentDescription("오늘의 추천 식사 보기").assertHasClickAction()
        composeRule.onNodeWithText("420 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("900 kcal").assertIsDisplayed()
        composeRule.onAllNodesWithText("기록 없음").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("아침").performClick()
        composeRule.runOnIdle { assertEquals(true, recordOpened) }
    }

    @Test
    fun expandedRecommendationRangeClearlyMarksLargeCalorieDifference() {
        val template = MealTemplate(
            id = "qa-lunch",
            name = "검수 식단",
            supportedMealTypes = "|LUNCH|",
            totalKcal = 710,
            preparationMinutes = 5,
            costLevel = "LOW",
            source = "QA_TEST",
            createdAt = 0,
            updatedAt = 0
        )
        val recommendation = ScoredMealRecommendation(
            template = template,
            score = 70.0,
            calorieDifference = -223,
            toleranceKcal = 233,
            reason = "남은 한 끼 칼로리에 가까운 식단이에요."
        )
        composeRule.setContent {
            HealthCareTheme {
                MealPlanContent(
                    state = MealPlanUiState(
                        mealType = MealType.LUNCH,
                        budgetKcal = 933,
                        recommendations = listOf(
                            MealRecommendationWithIngredients(recommendation, emptyList(), listOf("검수 음식"))
                        )
                    ),
                    onBack = {},
                    onRefresh = {},
                    onSelect = {},
                    onAdjustPortion = {},
                    onIngredientIncluded = { _, _ -> },
                    onShowReplacements = {},
                    onReplace = { _, _ -> },
                    onConfirm = {}
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = File(
            requireNotNull(context.getExternalFilesDir(null)),
            "recommendation-hero-hq-after.png"
        )
        composeRule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            screenshot.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
        assertTrue(screenshot.length() > 0L)

        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("검수 식단"))
        composeRule.onNodeWithText("검수 식단").assertIsDisplayed()
        composeRule.onNodeWithText("남은 한 끼 칼로리에 가까운 식단이에요.").assertIsDisplayed()
        composeRule.onNodeWithText("약 710 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("5분", substring = true).assertDoesNotExist()
    }
}
