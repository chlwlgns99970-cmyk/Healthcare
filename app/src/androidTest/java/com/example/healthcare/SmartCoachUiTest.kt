package com.example.healthcare

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.entity.FoodBrandSummary
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.FrequentFood
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
            .performScrollToNode(hasTestTag("food-search-alternatives-g"))
        composeRule.onNodeWithTag("food-search-alternatives-g").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("먹은 양과 칼로리를 직접 확인해요"))
        composeRule.onNodeWithText("먹은 양과 칼로리를 직접 확인해요").assertIsDisplayed()
        composeRule.onNodeWithText("원본 기준 100ml · 128 kcal").assertIsDisplayed()
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
    fun sameNameSearchShowsOneRepresentativeThenExpandsEveryOriginalProduct() {
        fun product(id: String, brand: String?) = FoodItem(
            id = id,
            sourceType = if (brand == null) "K-FIND" else "K-FIND-PRODUCT",
            sourceFoodCode = id,
            name = if (id == "cu") "참치 김밥" else "참치김밥",
            normalizedName = "참치김밥",
            category = "밥류",
            referenceAmount = if (id == "general") 100.0 else 180.0,
            unit = "g",
            energyKcal = if (id == "general") 220.0 else 420.0,
            carbohydrateGrams = 40.0,
            proteinGrams = 10.0,
            fatGrams = 8.0,
            servingDescription = "100g 기준",
            brand = brand,
            dataVersion = "test",
            createdAt = 0,
            updatedAt = 0
        )
        var selectedId = ""
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    SmartFoodInputScreen(
                        state = SmartInputUiState(
                            mode = SmartInputMode.SEARCH,
                            searchQuery = "참치김밥",
                            searchResults = listOf(
                                product("gs", "GS25"),
                                product("cu", "CU"),
                                product("general", null)
                            )
                        ),
                        recentMeals = emptyList(), onBack = {}, onPhoto = {}, onBarcode = {},
                        onNutritionLabel = {}, onSearch = {}, onSearchQueryChange = {},
                        onFoodSelected = { selectedId = it.id }, onUseBarcodeItem = {},
                        onOcrCandidateSelected = {}, onOcrAmountChange = {}, onConfirmOcr = {},
                        onManual = {}, onRegisterBarcode = {}, onRepeatRecent = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("food-search-result-general").assertIsDisplayed()
        composeRule.onAllNodes(hasTestTag("food-search-result-cu")).assertCountEquals(0)
        composeRule.onAllNodes(hasTestTag("food-search-result-gs")).assertCountEquals(0)
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("food-search-alternatives-general"))
        composeRule.onNodeWithText("같은 이름의 다른 음식 2건 보기").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("food-search-result-gs"))
        composeRule.onNodeWithTag("food-search-result-gs").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("food-search-result-cu"))
        composeRule.onNodeWithTag("food-search-result-cu").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("cu", selectedId) }
    }

    @Test
    fun favoriteSectionHasExplicitEmptyState() {
        composeRule.setContent {
            HealthCareTheme {
                SmartFoodInputScreen(
                    state = SmartInputUiState(mode = SmartInputMode.SEARCH),
                    recentMeals = emptyList(), onBack = {}, onPhoto = {}, onBarcode = {},
                    onNutritionLabel = {}, onSearch = {}, onSearchQueryChange = {},
                    onFoodSelected = {}, onUseBarcodeItem = {}, onOcrCandidateSelected = {},
                    onOcrAmountChange = {}, onConfirmOcr = {}, onManual = {},
                    onRegisterBarcode = {}, onRepeatRecent = {}
                )
            }
        }
        composeRule.onNodeWithText("즐겨찾기한 음식이 없어요", substring = true).assertIsDisplayed()
    }

    @Test
    fun favoriteSectionOpensSavedItemWithoutSearchingAgain() {
        val favorite = FrequentFood(
            id = 1,
            foodName = "참치김밥",
            defaultServing = "180g",
            calories = 420,
            isFavorite = true,
            isFrequent = false,
            foodItemId = "favorite-1",
            sourceType = "K-FIND-PRODUCT",
            sourceFoodCode = "favorite-1",
            brand = "검수 브랜드"
        )
        var selectedFavoriteId: String? = null
        composeRule.setContent {
            HealthCareTheme {
                SmartFoodInputScreen(
                    state = SmartInputUiState(mode = SmartInputMode.SEARCH),
                    recentMeals = emptyList(), onBack = {}, onPhoto = {}, onBarcode = {},
                    onNutritionLabel = {}, onSearch = {}, onSearchQueryChange = {},
                    onFoodSelected = {}, onUseBarcodeItem = {}, onOcrCandidateSelected = {},
                    onOcrAmountChange = {}, onConfirmOcr = {}, onManual = {},
                    onRegisterBarcode = {}, onRepeatRecent = {},
                    favoriteFoods = listOf(favorite),
                    onFavoriteFoodSelected = { selectedFavoriteId = it.foodItemId }
                )
            }
        }
        composeRule.onNodeWithTag("favorite-food-favorite-1").assertIsDisplayed().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        composeRule.runOnIdle { assertEquals("favorite-1", selectedFavoriteId) }
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
            .performScrollToNode(hasText("제품 전체 · 450 kcal"))
        composeRule.onNodeWithText("제품 전체 · 450 kcal").assertIsDisplayed()
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

        composeRule.onNodeWithText("오늘 목표보다 약 200 kcal 많아요").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(SemanticsMatcher("vertical scroll") {
            it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
        }).fetchSemanticsNodes().size)
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

        composeRule.onNodeWithText("오늘 식사 스타일을 골라보세요").assertIsDisplayed()
        composeRule.onNodeWithText("추천 고르기").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard-open-daily-plan").assertHasClickAction().performClick()
        composeRule.runOnIdle { assertEquals(true, recommendationOpened) }
        composeRule.onNodeWithText("아침 식사 · 420 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("점심 식사 · 900 kcal").assertIsDisplayed()
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
