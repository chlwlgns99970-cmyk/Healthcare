package com.example.healthcare

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.ui.screens.AddRecordScreen
import com.example.healthcare.ui.screens.QuickRecordAction
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses only an in-memory database; the installed QA user's records are untouched. */
class SameAmountQuickRecordUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
    private val vm = AddRecordViewModel(MealRepository(db.mealRecordDao()), FoodRepository(db.frequentFoodDao()),
        nutritionRepository = NutritionRepository(db.foodItemDao()))
    private var completed = 0
    @After fun close() { db.close() }

    private fun recent() = MealRecord(id = 41, date = "2026-09-20", time = "08:00", mealType = MealType.BREAKFAST,
        foodName = "김밥 한 줄", calories = 296, carbohydrateGrams = 40.0, proteinGrams = null,
        servingAmount = 216.0, servingUnit = "g", portionPresetId = "amount/줄/216.0/1.0",
        portionDisplayLabel = "1줄", foodItemId = "source-no-longer-present")

    private fun show(meal: MealType?, search: Boolean = false) {
        rule.setContent {
            HealthCareTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(360.dp)) {
                        AddRecordScreen(viewModel = vm, initialMealType = meal,
                            initialAction = if (search) QuickRecordAction.SEARCH else null,
                            onRecordSaved = { completed++ })
                    }
                }
            }
        }
    }
    private fun scrollTo(tag: String) {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag(tag))
    }
    private fun rows() = runBlocking { db.mealRecordDao().getAllMeals().first() }

    @Test fun knownLunchContextRecordsRecentSnapshotInOneTapAtLargeText() {
        runBlocking { db.mealRecordDao().insertMeal(recent()) }
        show(MealType.LUNCH)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("repeat-recent-41").fetchSemanticsNodes().isNotEmpty() }
        scrollTo("repeat-recent-41")
        rule.onNodeWithTag("repeat-recent-41").assertIsDisplayed().performClick()
        rule.waitUntil(10_000) { completed == 1 }
        val inserted = rows().single { it.id != 41L }
        assertEquals(MealType.LUNCH, inserted.mealType)
        assertEquals("1줄", inserted.portionDisplayLabel)
        assertEquals(296, inserted.calories)
        assertEquals(40.0, inserted.carbohydrateGrams!!, 0.0)
        assertNull(inserted.proteinGrams)
        val updatedTag = "repeat-recent-${inserted.id}"
        rule.waitUntil(10_000) { rule.onAllNodesWithTag(updatedTag).fetchSemanticsNodes().isNotEmpty() }
        scrollTo(updatedTag)
        rule.onNodeWithTag(updatedTag).performClick()
        rule.waitForIdle()
        assertEquals(2, rows().size)
    }

    @Test fun unknownMealContextAsksOnceThenRecordsChosenDinner() {
        runBlocking { db.mealRecordDao().insertMeal(recent()) }
        show(null)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("repeat-recent-41").fetchSemanticsNodes().isNotEmpty() }
        scrollTo("repeat-recent-41")
        rule.onNodeWithTag("repeat-recent-41").performClick()
        rule.onNodeWithText("어느 식사에 기록할까요?").assertIsDisplayed()
        assertEquals(1, rows().size)
        rule.onNodeWithText("저녁", useUnmergedTree = true).performClick()
        rule.waitUntil(10_000) { completed == 1 }
        assertEquals(MealType.DINNER, rows().single { it.id != 41L }.mealType)
        rule.onNodeWithText("어느 식사에 기록할까요?").assertDoesNotExist()
    }

    @Test fun savedFavoriteWithMissingSourceRecordsSameAmountInKnownContext() {
        runBlocking { db.frequentFoodDao().insertFood(FrequentFood(id = 12, foodName = "저장한 음식",
            defaultServing = "85g", calories = 210, isFavorite = true, isFrequent = false,
            foodItemId = "missing-source", carbohydrateGrams = 31.0, proteinGrams = null, fatGrams = 6.0)) }
        show(MealType.SNACK, search = true)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("repeat-saved-12").fetchSemanticsNodes().isNotEmpty() }
        scrollTo("repeat-saved-12")
        rule.onNodeWithTag("repeat-saved-12").assertIsDisplayed().performClick()
        rule.waitUntil(10_000) { completed == 1 }
        val inserted = rows().single()
        assertEquals(MealType.SNACK, inserted.mealType)
        assertEquals(210, inserted.calories)
        assertEquals(85.0, inserted.servingAmount!!, 0.0)
        assertEquals("missing-source", inserted.foodItemId)
        assertNull(inserted.proteinGrams)
    }

    @Test fun recentAmountOpensSharedInputAndSavesChangedSnapshot() {
        runBlocking { db.mealRecordDao().insertMeal(recent()) }
        show(MealType.LUNCH)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("change-recent-amount-41").fetchSemanticsNodes().isNotEmpty() }
        scrollTo("change-recent-amount-41")
        rule.onNodeWithTag("change-recent-amount-41").performClick()
        scrollTo("food-amount-quantity")
        rule.onNodeWithTag("food-amount-quantity").assertTextContains("1").performTextReplacement("0.5")
        rule.activity.runOnUiThread { vm.saveRecord { completed++ } }
        rule.waitUntil(10_000) { completed == 1 }
        val saved = rows().single { it.id != 41L }
        assertEquals(148, saved.calories)
        assertEquals("0.5줄", saved.portionDisplayLabel)
        assertEquals(20.0, saved.carbohydrateGrams!!, 0.0)
        assertNull(saved.proteinGrams)
    }
}
