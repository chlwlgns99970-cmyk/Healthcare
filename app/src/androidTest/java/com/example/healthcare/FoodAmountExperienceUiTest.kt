package com.example.healthcare

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.EnergyProfileRepository
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.GoalRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.domain.RecordedAmountSnapshot
import com.example.healthcare.ui.screens.HistoryEditPane
import com.example.healthcare.ui.screens.QuickFoodRecordScreen
import com.example.healthcare.ui.screens.SmartFoodInputScreen
import com.example.healthcare.ui.screens.WellnessManualRecordScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import com.example.healthcare.ui.viewmodel.HistoryViewModel
import com.example.healthcare.ui.viewmodel.SmartInputMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Only an isolated in-memory database is populated; the user's QA tables are never opened here. */
class FoodAmountExperienceUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: AppDatabase
    private lateinit var add: AddRecordViewModel
    private lateinit var history: HistoryViewModel
    private val models = ViewModelStore()

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val meals = MealRepository(database.mealRecordDao())
        val nutrition = NutritionRepository(database.foodItemDao())
        add = AddRecordViewModel(meals, FoodRepository(database.frequentFoodDao()), nutritionRepository = nutrition)
        history = HistoryViewModel(meals, GoalRepository(database.calorieGoalDao()),
            EnergyProfileRepository(database.energyProfileDao()), nutritionRepository = nutrition)
        composeRule.activityRule.scenario.onActivity { models.put("add", add); models.put("history", history) }
    }

    @After fun teardown() {
        composeRule.activityRule.scenario.onActivity { models.clear() }
        if (::database.isInitialized) database.close()
    }

    @Test fun verifiedRollHalfRecordAndHistoryEditKeepUnitAt360AndLargeText() {
        val food = baseFood("kfind-product-p123-203020200-2032", "백종원한줄김밥", 137.0).copy(
            sourceType = "K-FIND-PRODUCT", sourceFoodCode = "P123-203020200-2032", brand = "(주)비지에프푸드",
            carbohydrateGrams = 25.95, proteinGrams = 4.57, fatGrams = 1.64,
            servingDescription = "100g 기준 · 공식 총내용량 216g · 포장단위 줄")
        select(food)
        var editing by mutableStateOf(false)
        var savedMealType: MealType? = null
        var edited = false
        composeRule.setContent {
            LargeTextFixture {
                if (editing) {
                    val state by history.editState.collectAsState()
                    HistoryEditPane(state = state, onFoodNameChange = history::onEditFoodNameChange,
                        onCaloriesChange = history::onEditCaloriesChange, onMealTypeChange = history::onEditMealTypeChange,
                        onDateChange = history::onEditDateChange, onTimeChange = history::onEditTimeChange,
                        onServingAmountChange = history::onEditServingAmountChange, onServingUnitChange = history::onEditServingUnitChange,
                        onQuantityChange = history::onEditQuantityChange, onQuantityUnitChange = history::onEditQuantityUnitChange,
                        onPortionRatio = history::selectEditRatio, onMemoChange = history::onEditMemoChange,
                        onSave = { history.saveMealEdit { edited = true } }, onBack = {})
                } else QuickScreen { savedMealType = it }
            }
        }
        scrollToTag("food-amount-quantity")
        composeRule.onNodeWithTag("food-amount-quantity").assertTextContains("1")
        composeRule.onNodeWithTag("food-amount-unit").assertTextContains("줄", substring = true)
        assertAmountFits()
        scrollToText("296 kcal")
        composeRule.onNodeWithText("296 kcal").assertIsDisplayed()
        scrollToTag("food-amount-quantity")
        composeRule.onNodeWithTag("food-amount-quantity").performTextReplacement("0.5")
        hideKeyboard()
        scrollToText("148 kcal")
        composeRule.onNodeWithText("148 kcal").assertIsDisplayed()
        saveQuick()
        composeRule.waitUntil(5_000) { savedMealType != null }
        val saved = onlyRecord()
        assertEquals(MealType.LUNCH, savedMealType)
        assertEquals(148, saved.calories)
        assertEquals(108.0, saved.servingAmount!!, 0.0)
        assertEquals("g", saved.servingUnit)
        assertEquals("0.5줄", saved.portionDisplayLabel)
        assertEquals(28.026, saved.carbohydrateGrams!!, 0.00001)
        assertEquals(4.9356, saved.proteinGrams!!, 0.00001)
        assertEquals(1.7712, saved.fatGrams!!, 0.00001)
        composeRule.runOnIdle { history.startEditing(saved); editing = true }
        scrollToTag("food-amount-quantity")
        composeRule.onNodeWithTag("food-amount-quantity").assertTextContains("0.5")
        composeRule.onNodeWithTag("food-amount-unit").assertTextContains("줄", substring = true)
        assertAmountFits()
        composeRule.onNodeWithTag("food-amount-quantity").performTextReplacement("1")
        hideKeyboard()
        scrollToTag("history-edit-calorie-preview")
        composeRule.onNodeWithTag("history-edit-calorie-preview").assertTextContains("296 kcal", substring = true)
        scrollToTag("history-edit-save")
        composeRule.onNodeWithTag("history-edit-save").performClick()
        composeRule.waitUntil(5_000) { edited }
        val updated = onlyRecord()
        assertEquals(saved.id, updated.id)
        assertEquals(296, updated.calories)
        assertEquals("1줄", updated.portionDisplayLabel)
        assertEquals(1.0, RecordedAmountSnapshot.from(updated)!!.quantity, 0.0)
    }

    @Test fun exactUsdaEggCountTwoSavesVerifiedMacrosAt360AndLargeText() {
        val egg = baseFood("usda-sr-173424", "달걀_삶은 것", 155.0).copy(
            sourceType = "USDA-SR-LEGACY", sourceFoodCode = "FDC-173424", category = "난류",
            carbohydrateGrams = 1.12, proteinGrams = 12.58, fatGrams = 10.61)
        select(egg)
        composeRule.setContent { LargeTextFixture { QuickScreen {} } }
        scrollToTag("food-amount-quantity")
        composeRule.onNodeWithTag("food-amount-unit").assertTextContains("개", substring = true)
        assertAmountFits()
        composeRule.onNodeWithTag("food-amount-quantity").performTextReplacement("2")
        hideKeyboard()
        scrollToText("155 kcal")
        composeRule.onNodeWithText("155 kcal").assertIsDisplayed()
        saveQuick()
        val saved = onlyRecord()
        assertEquals(100.0, saved.servingAmount!!, 0.0)
        assertEquals(155, saved.calories)
        assertEquals("2개", saved.portionDisplayLabel)
        assertEquals(1.12, saved.carbohydrateGrams!!, 0.00001)
        assertEquals(12.58, saved.proteinGrams!!, 0.00001)
        assertEquals(10.61, saved.fatGrams!!, 0.00001)
    }

    @Test fun weightFallbackOffersOnlyGAndPreservesUnknownMacrosAt360AndLargeText() {
        val food = baseFood("weight-only-fixture", "중량으로 입력하는 음식", 180.0).copy(
            carbohydrateGrams = null, proteinGrams = 7.0, fatGrams = null)
        select(food)
        assertEquals("", add.uiState.value.foodQuantity)
        assertEquals("", add.uiState.value.calories)
        composeRule.setContent { LargeTextFixture { QuickScreen {} } }
        scrollToTag("food-amount-quantity")
        composeRule.onNodeWithTag("food-amount-unit").assertTextContains("g", substring = true).performClick()
        composeRule.onNodeWithTag("food-amount-choice-g").assertIsDisplayed()
        listOf("줄", "개", "ml", "봉", "제품 전체").forEach {
            composeRule.onAllNodes(hasTestTag("food-amount-choice-$it")).assertCountEquals(0)
        }
        composeRule.onNodeWithTag("food-amount-choice-g").performClick()
        assertAmountFits()
        composeRule.onNodeWithTag("food-amount-quantity").performTextReplacement("75")
        hideKeyboard()
        scrollToText("135 kcal")
        composeRule.onNodeWithText("135 kcal").assertIsDisplayed()
        saveQuick()
        val saved = onlyRecord()
        assertEquals(75.0, saved.servingAmount!!, 0.0)
        assertEquals("g", saved.servingUnit)
        assertEquals(135, saved.calories)
        assertNull(saved.carbohydrateGrams)
        assertEquals(5.25, saved.proteinGrams!!, 0.00001)
        assertNull(saved.fatGrams)
    }

    @Test fun unresolvedKimbapSearchCardStaysSelectableAndPrefillsManualNameWithoutKcal() {
        val food = baseFood("kfind-d401-007030000-0001", "김밥_계란", 97.0).copy(
            sourceFoodCode = "D401-007030000-0001", normalizedName = "김밥계란", aliases = "|계란김밥|",
            category = "밥류", unit = "ml", servingDescription = "100ml 기준")
        runBlocking { database.foodItemDao().upsertAll(listOf(food)) }
        composeRule.activityRule.scenario.onActivity { add.showFoodSearch(); add.onFoodSearchChange("계란김밥") }
        composeRule.setContent {
            LargeTextFixture {
                val state by add.smartInputState.collectAsState()
                val recordState by add.uiState.collectAsState()
                if (state.mode == SmartInputMode.MANUAL) WellnessManualRecordScreen(recordState,
                    emptyList(), emptyList(), emptyList(), null, {}, {}, {}, add, guided = false, mealTypeLocked = true)
                else SmartFoodInputScreen(state = state, recentMeals = emptyList(),
                    onBack = {}, onPhoto = {}, onBarcode = {}, onNutritionLabel = {}, onSearch = {},
                    onSearchQueryChange = add::onFoodSearchChange, onFoodSelected = add::selectSearchFood,
                    onUseBarcodeItem = {}, onOcrCandidateSelected = {}, onOcrAmountChange = {},
                    onConfirmOcr = {}, onManual = {}, onRegisterBarcode = {}, onRepeatRecent = {})
            }
        }
        composeRule.waitUntil(5_000) { add.smartInputState.value.searchResults.any { it.id == food.id } }
        scrollToTag("food-search-result-${food.id}")
        composeRule.onNodeWithTag("food-search-result-${food.id}").assertIsEnabled().performClick()
        composeRule.waitUntil(5_000) { add.smartInputState.value.mode == SmartInputMode.MANUAL }
        scrollToText("음식 이름")
        composeRule.onNode(hasSetTextAction() and hasText("음식 이름")).assertTextContains("계란김밥")
        composeRule.onNodeWithText("먹은 양과 확인한 칼로리를 직접 입력해주세요.").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasSetTextAction() and hasText("칼로리"))
        assertEquals("", composeRule.onNode(hasSetTextAction() and hasText("칼로리"))
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        composeRule.runOnIdle {
            assertEquals("계란김밥", add.uiState.value.foodName)
            assertEquals(food.id, add.uiState.value.selectedFoodItemId)
            assertEquals("", add.uiState.value.calories)
            assertEquals("", add.uiState.value.servingAmount)
            assertTrue(add.uiState.value.sourceDescription.orEmpty().contains("K-FIND"))
            assertNull(add.uiState.value.estimatedProteinGrams)
        }
        assertEquals(0, runBlocking { database.mealRecordDao().getAllMeals().first().size })
    }

    private fun select(food: FoodItem) {
        runBlocking { database.foodItemDao().upsertAll(listOf(food)) }
        composeRule.activityRule.scenario.onActivity { add.onMealTypeChange(MealType.LUNCH); add.selectSearchFood(food) }
    }

    @Composable private fun QuickScreen(onSaved: (MealType) -> Unit) {
        val state by add.uiState.collectAsState()
        QuickFoodRecordScreen(state, emptyList(), emptyList(), true, {}, {}, onSaved, add)
    }

    @Composable private fun LargeTextFixture(content: @Composable () -> Unit) {
        HealthCareTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.30f)) {
                Box(Modifier.width(360.dp).height(720.dp)) { content() }
            }
        }
    }

    private fun scrollToTag(tag: String) = composeRule.onAllNodes(hasScrollAction()).onFirst()
        .performScrollToNode(hasTestTag(tag))
    private fun scrollToText(text: String) = composeRule.onAllNodes(hasScrollAction()).onFirst()
        .performScrollToNode(hasText(text))
    private fun hideKeyboard() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        composeRule.waitForIdle()
    }
    private fun assertAmountFits() {
        val quantity = composeRule.onNodeWithTag("food-amount-quantity").assertIsDisplayed().getUnclippedBoundsInRoot()
        val unit = composeRule.onNodeWithTag("food-amount-unit").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(quantity.left >= 0.dp && quantity.right <= 360.dp)
        assertTrue(unit.left >= quantity.right && unit.right <= 360.dp)
        assertTrue(unit.bottom - unit.top >= 48.dp)
    }
    private fun saveQuick() {
        scrollToText("점심에 기록")
        composeRule.onNodeWithText("점심에 기록").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) { runBlocking { database.mealRecordDao().getAllMeals().first().size == 1 } }
    }
    private fun onlyRecord(): MealRecord = runBlocking { database.mealRecordDao().getAllMeals().first().single() }
    private fun baseFood(id: String, name: String, kcal: Double) = FoodItem(id = id, sourceType = "K-FIND",
        sourceFoodCode = id, name = name, normalizedName = name, category = "기타", referenceAmount = 100.0,
        unit = "g", energyKcal = kcal, servingDescription = "100g 기준", dataVersion = "QA-isolated-fixture",
        createdAt = 0, updatedAt = 0)
}
