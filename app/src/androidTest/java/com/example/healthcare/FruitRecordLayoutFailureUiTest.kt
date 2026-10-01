package com.example.healthcare

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.ui.AddRecordRoute
import com.example.healthcare.ui.DashboardRoute
import com.example.healthcare.ui.finishRecordFlow
import com.example.healthcare.ui.screens.AddRecordScreen
import com.example.healthcare.ui.screens.SmartFoodInputScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import com.example.healthcare.ui.viewmodel.SmartInputMode
import com.example.healthcare.ui.viewmodel.SmartInputUiState
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FruitRecordLayoutFailureUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fruitNameCaloriesAndAlternativesFitAt360DpWith130PercentText() {
        val apple = fruit()
        val product = apple.copy(id = "product", sourceType = "K-FIND-PRODUCT", name = "사과",
            normalizedName = "사과", brand = "OO", energyKcal = 100.0)
        var query by mutableStateOf("사과")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                HealthCareTheme(darkTheme = false, appFontScale = 1.3f) {
                    Box(Modifier.width(360.dp).height(780.dp)) {
                        SmartFoodInputScreen(
                            state = SmartInputUiState(mode = SmartInputMode.SEARCH, searchQuery = query,
                                searchResults = listOf(product, apple)), recentMeals = emptyList(),
                            onBack = {}, onPhoto = {}, onBarcode = {}, onNutritionLabel = {}, onSearch = {},
                            onSearchQueryChange = { query = it }, onFoodSelected = {}, onUseBarcodeItem = {},
                            onOcrCandidateSelected = {}, onOcrAmountChange = {}, onConfirmOcr = {},
                            onManual = {}, onRegisterBarcode = {}, onRepeatRecent = {}
                        )
                    }
                }
            }
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("food-search-result-apple"))
        val name = compose.onNode(hasText("사과") and !hasSetTextAction(), useUnmergedTree = true)
        val kcal = compose.onNodeWithText("영양정보 100g 기준 · 약 52 kcal", useUnmergedTree = true)
        val alternatives = compose.onNodeWithTag("food-search-alternatives-apple")
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "fruit-record-qa/search-360dp-font130.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        listOf(name, kcal, compose.onNodeWithText("다른 제품 1개", useUnmergedTree = true)).forEach { node ->
            node.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertFalse(layout.didOverflowHeight)
                (0 until layout.lineCount).forEach { assertFalse(layout.isLineEllipsized(it)) }
                // Intrinsic width can round above measured width by a fraction of a pixel.
                // Verify the actual visible glyph bounds rather than that rounded flag.
                layout.layoutInput.text.forEachIndexed { index, character ->
                    if (!character.isWhitespace()) {
                        val glyph = layout.getBoundingBox(index)
                        assertTrue("$character right=$glyph size=${layout.size}", glyph.right <= layout.size.width + 1f)
                        assertTrue(glyph.bottom <= layout.size.height + 1f)
                    }
                }
            }
        }
        assertTrue(name.fetchSemanticsNode().boundsInRoot.bottom <= kcal.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(kcal.fetchSemanticsNode().boundsInRoot.bottom <= alternatives.fetchSemanticsNode().boundsInRoot.top)
        alternatives.performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("food-search-result-product"))
        compose.onNodeWithTag("food-search-result-product").assertIsDisplayed()
    }

    @Test fun failedQuickSaveKeepsInputAndStackThenRetryCompletesOnlyOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        var fail = true
        var insertCount = 0
        var successCount = 0
        val repository = object : MealRepository(db.mealRecordDao()) {
            override suspend fun insertMeal(meal: MealRecord) {
                if (fail) error("forced QA failure")
                insertCount++
                super.insertMeal(meal)
            }
        }
        val viewModel = AddRecordViewModel(repository, FoodRepository(db.frequentFoodDao()))
        val stack = mutableStateListOf<NavKey>(DashboardRoute, AddRecordRoute)
        compose.runOnUiThread {
            viewModel.onMealTypeChange(MealType.BREAKFAST)
            viewModel.selectVerifiedFood(fruit())
            viewModel.onServingAmountChange("100")
        }
        compose.setContent {
            HealthCareTheme {
                NavDisplay(backStack = stack) { key ->
                    NavEntry(key) {
                        if (key == AddRecordRoute) AddRecordScreen(viewModel = viewModel,
                            initialMealType = MealType.BREAKFAST, onBack = { finishRecordFlow(stack) },
                            onRecordSaved = { if (finishRecordFlow(stack)) successCount++ })
                        else Text("QA Home")
                    }
                }
            }
        }
        try {
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("아침에 기록"))
            compose.onNodeWithText("아침에 기록").performClick()
            compose.waitUntil(10_000) { viewModel.uiState.value.saveError != null }
            compose.runOnIdle {
                assertEquals(listOf(DashboardRoute, AddRecordRoute), stack.toList())
                assertEquals("사과", viewModel.uiState.value.foodName)
                assertEquals("apple", viewModel.uiState.value.selectedFoodItemId)
                assertEquals("100", viewModel.uiState.value.servingAmount)
                assertEquals("52", viewModel.uiState.value.calories)
                assertFalse(viewModel.uiState.value.isSaving)
                assertEquals(0, successCount)
                fail = false
            }
            compose.onNodeWithText("아침에 기록").performClick()
            compose.waitUntil(10_000) { successCount == 1 }
            compose.onNodeWithText("QA Home").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf(DashboardRoute), stack.toList())
                assertEquals(1, insertCount)
                assertFalse(finishRecordFlow(stack))
                assertEquals(listOf(DashboardRoute), stack.toList())
            }
        } finally { db.close() }
    }

    private fun fruit() = FoodItem(id = "apple", sourceType = "USDA-SR-LEGACY", sourceFoodCode = "apple",
        name = "사과_껍질 포함_생것", normalizedName = "사과껍질포함생것", aliases = "|사과|과일|과일류|",
        category = "과일류", referenceAmount = 100.0, unit = "g", energyKcal = 52.0,
        servingDescription = "100g 기준", dataVersion = "test", createdAt = 0, updatedAt = 0)
}
