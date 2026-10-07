package com.example.healthcare

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.*
import com.example.healthcare.domain.FranchiseCatalog
import com.example.healthcare.domain.RecordCompletion
import com.example.healthcare.ui.screens.AddRecordScreen
import com.example.healthcare.ui.screens.RecordCompletionScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Only isolated Room rows are written. Real QA records are preserved. */
class RecordCompletionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: AppDatabase
    private lateinit var add: AddRecordViewModel
    private val models = ViewModelStore()
    @Before fun fixture() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        add = AddRecordViewModel(MealRepository(db.mealRecordDao()), FoodRepository(db.frequentFoodDao()),
            nutritionRepository = NutritionRepository(db.foodItemDao()))
        compose.activityRule.scenario.onActivity { models.put("add", add) }
    }
    @After fun close() {
        compose.activityRule.scenario.onActivity { models.clear() }
        db.close()
    }
    private fun showCompletionFlow(onHome: () -> Unit, onHistory: () -> Unit) {
        var saved by mutableStateOf<MealRecord?>(null)
        compose.setContent {
            HealthCareTheme {
                val native = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(native.density, 1.30f)) {
                    Box(Modifier.width(360.dp).height(720.dp)) {
                        if (saved == null) AddRecordScreen(viewModel = add, initialMealType = MealType.LUNCH,
                            onRecordSaved = { saved = requireNotNull(add.completedRecord.value) })
                        else RecordCompletionScreen(RecordCompletion(saved!!), onHome, onHistory)
                    }
                }
            }
        }
    }
    private fun roll() = FoodItem(id="official-roll", sourceType="K-FIND-PRODUCT",sourceFoodCode="P123-203020200-2032",
        name="백종원한줄김밥",normalizedName="백종원한줄김밥",category="김밥류",brand="(주)비지에프푸드",
        referenceAmount=100.0,unit="g",energyKcal=137.0,
        servingDescription="100g 기준 · 공식 총내용량 216g · 포장단위 줄",dataVersion="isolated",createdAt=0,updatedAt=0)
    private fun saveRoll() {
        compose.runOnIdle { add.onMealTypeChange(MealType.LUNCH); add.selectVerifiedFood(roll()) }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("점심에 기록"))
        compose.onNodeWithText("점심에 기록").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("record-completion").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun lunchRollCompletionShowsStoredSnapshotAndHistoryAt360AndLargeFont() {
        var viewed=false
        showCompletionFlow({}, { viewed=true })
        saveRoll()
        compose.onNodeWithText("기록했어요").assertIsDisplayed()
        compose.onNodeWithText("점심").assertIsDisplayed()
        compose.onNodeWithText("백종원한줄김밥").assertIsDisplayed()
        compose.onNodeWithTag("completion-amount").assertTextEquals("1줄").assertIsDisplayed()
        compose.onNodeWithText("296 kcal").assertIsDisplayed()
        compose.onNodeWithTag("completion-home").assertIsDisplayed()
        compose.onNodeWithTag("completion-history").assertIsDisplayed().performClick()
        assertTrue(viewed)
        val saved=runBlocking { db.mealRecordDao().getAllMeals().first().single() }
        assertEquals(216.0,saved.servingAmount!!,0.0); assertEquals("1줄",saved.portionDisplayLabel)
        assertEquals(saved.copy(id=0),add.completedRecord.value)
    }
    @Test fun completionHomeAndBackNeverSaveAgain() {
        var homeCount=0
        showCompletionFlow({homeCount++}, {})
        saveRoll()
        compose.onNodeWithTag("completion-home").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.runOnIdle { add.saveRecord {} }
        compose.waitForIdle()
        assertEquals(2,homeCount)
        assertEquals(1,runBlocking { db.mealRecordDao().getAllMeals().first().size })
    }
    @Test fun bonjukOfficialMenuOpensPrefilledManualWithUnknownNutrition() {
        val menu=FranchiseCatalog.officialMenus("본죽").first()
        compose.setContent { HealthCareTheme { AddRecordScreen(add) } }
        compose.runOnIdle {
            add.showFoodSearch()
            add.selectFoodSearchMode(com.example.healthcare.ui.viewmodel.FoodSearchMode.FRANCHISE)
            add.selectProductBrand(com.example.healthcare.data.entity.FoodBrandSummary("본죽", 0))
        }
        compose.waitUntil(10_000) { !add.smartInputState.value.isSearching }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("franchise-menu-${menu.id}"))
        compose.onNodeWithTag("franchise-menu-${menu.id}").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(menu.recordName,add.uiState.value.foodName)
        assertEquals("",add.uiState.value.calories)
        assertEquals("",add.uiState.value.servingAmount)
        assertTrue(add.uiState.value.sourceDescription!!.contains(menu.sourceUrl))
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(menu.recordName,substring=true))
        compose.onNode(hasSetTextAction() and hasText(menu.recordName)).assertIsDisplayed()
        assertEquals(0,runBlocking { db.mealRecordDao().getAllMeals().first().size })
    }
}
