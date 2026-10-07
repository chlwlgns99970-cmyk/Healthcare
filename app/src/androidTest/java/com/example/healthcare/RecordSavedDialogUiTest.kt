package com.example.healthcare

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.RecordCompletion
import com.example.healthcare.ui.components.RecordSavedDialog
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.RecordSaveConfirmationViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.ui.screens.AddRecordScreen
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class RecordSavedDialogUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun realSaveFailureHasNoPopupPreservesInputAndRetryCreatesOneRow() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val database=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        var fail=true;var attempts=0
        val repository=object:MealRepository(database.mealRecordDao()) {
            override suspend fun insertMeal(meal:MealRecord) {
                attempts++
                if(fail)error("Isolated test database failure")
                super.insertMeal(meal)
            }
        }
        val add=AddRecordViewModel(repository,FoodRepository(database.frequentFoodDao()))
        val models=ViewModelStore();models.put("isolated",add)
        val gate=RecordSaveConfirmationViewModel();var navigation=0
        try {
            compose.setContent { HealthCareTheme {
                AddRecordScreen(viewModel=add,onRecordSaved={
                    gate.saved(RecordCompletion(requireNotNull(add.completedRecord.value)))
                })
                val pending by gate.pending.collectAsState()
                pending?.let { RecordSavedDialog(it.edited) { if(gate.confirm()!=null)navigation++ } }
            } }
            compose.runOnIdle {
                add.showManualEntry(); add.onFoodNameChange("실패 검증 음식"); add.onCaloriesChange("200")
            }
            compose.onNodeWithText("한 화면에서 자세히 입력").performClick()
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("기록 저장"))
            val before=add.uiState.value
            compose.onNodeWithText("기록 저장").performClick()
            compose.waitUntil(10000) { add.uiState.value.saveError!=null }
            assertEquals("기록을 저장하지 못했습니다. 다시 시도해주세요.",add.uiState.value.saveError)
            assertEquals(1,attempts)
            compose.onNodeWithTag("record-saved-dialog").assertDoesNotExist()
            assertEquals(0,navigation);assertEquals(before.foodName,add.uiState.value.foodName)
            assertEquals(before.calories,add.uiState.value.calories);assertEquals(before.foodQuantity,add.uiState.value.foodQuantity)
            assertEquals(0,runBlocking { database.mealRecordDao().getAllMeals().first().size })
            compose.runOnIdle { fail=false }
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("기록 저장"))
            compose.onNodeWithText("기록 저장").assertIsDisplayed().assertIsEnabled().performClick()
            try { compose.waitUntil(10000) { gate.pending.value!=null } }
            catch(error:Throwable) {
                val state=add.uiState.value
                throw AssertionError("Retry did not complete: error=${state.saveError}, saving=${state.isSaving}, nameError=${state.nameError}, caloriesError=${state.caloriesError}, quantity=${state.foodQuantity}, amount=${state.servingAmount}, unit=${state.servingUnit}, rows=${runBlocking { database.mealRecordDao().getAllMeals().first().size }}",error)
            }
            compose.onNodeWithText("저장 완료").assertIsDisplayed()
            assertEquals(1,runBlocking { database.mealRecordDao().getAllMeals().first().size })
            assertEquals(2,attempts)
            compose.onNodeWithTag("record-saved-confirm").performClick();assertEquals(1,navigation)
        } finally {
            compose.activityRule.scenario.onActivity { models.clear() };database.close()
        }
    }
    @Test fun dialogAt360DpAndLargeFontConsumesOnce() {
        val gate=RecordSaveConfirmationViewModel()
        val event=RecordCompletion(MealRecord(date="2026-10-06",time="12:00",mealType=MealType.LUNCH,foodName="김밥",calories=296))
        var revision by mutableIntStateOf(0);var navigation=0
        var testedFont by androidx.compose.runtime.mutableFloatStateOf(1.30f)
        val screenPixels=compose.activity.resources.displayMetrics.widthPixels
        compose.setContent { HealthCareTheme {
            CompositionLocalProvider(LocalDensity provides Density(screenPixels/360f,testedFont)) {
                val pending by gate.pending.collectAsState()
                revision
                pending?.let { RecordSavedDialog(it.edited) { if(gate.confirm()!=null)navigation++ } }
            }
        } }
        compose.runOnIdle { gate.saved(event) }
        compose.onNodeWithText("저장 완료").assertIsDisplayed()
        compose.onNodeWithText("기록이 저장되었습니다.").assertIsDisplayed()
        compose.onNodeWithTag("record-saved-confirm").assertIsDisplayed()
        val title=compose.onNodeWithText("저장 완료").fetchSemanticsNode().boundsInRoot
        val body=compose.onNodeWithText("기록이 저장되었습니다.").fetchSemanticsNode().boundsInRoot
        val button=compose.onNodeWithTag("record-saved-confirm").fetchSemanticsNode().boundsInRoot
        assertTrue(title.bottom<body.top);assertTrue(body.bottom<button.top)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.cacheDir,"record-saved-dialog-360-large.png").outputStream().use {
            compose.onNodeWithTag("record-saved-dialog").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        compose.runOnIdle { testedFont = 1.69f }
        compose.onNodeWithText("저장 완료").assertIsDisplayed()
        compose.onNodeWithTag("record-saved-confirm").assertIsDisplayed()
        val largeTitle=compose.onNodeWithText("저장 완료").fetchSemanticsNode().boundsInRoot
        val largeBody=compose.onNodeWithText("기록이 저장되었습니다.").fetchSemanticsNode().boundsInRoot
        val largeButton=compose.onNodeWithTag("record-saved-confirm").fetchSemanticsNode().boundsInRoot
        assertTrue(largeTitle.bottom<largeBody.top);assertTrue(largeBody.bottom<largeButton.top)
        java.io.File(context.cacheDir,"record-saved-dialog-360-combined-large.png").outputStream().use {
            compose.onNodeWithTag("record-saved-dialog").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        compose.runOnIdle { revision++;assertFalse(gate.saved(event)) }
        compose.onAllNodesWithTag("record-saved-dialog").assertCountEquals(1)
        compose.onNodeWithTag("record-saved-confirm").performClick()
        compose.onNodeWithTag("record-saved-dialog").assertDoesNotExist()
        compose.runOnIdle { assertNull(gate.confirm());assertFalse(gate.saved(event));revision++ }
        assertEquals(1,navigation)
    }
}
