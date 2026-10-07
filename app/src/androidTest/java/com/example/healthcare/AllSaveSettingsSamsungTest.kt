package com.example.healthcare

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import com.example.healthcare.ui.screens.*
import com.example.healthcare.ui.viewmodel.*
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.domain.BodySex
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real save buttons and QA persistence, restored by the external QA snapshot workflow. */
class AllSaveSettingsSamsungTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HealthcareApplication
    private fun settings() = SettingsViewModel(app.goalRepository, app.energyProfileRepository, app.bodyProfileStore, app.weightGoalStore)
    private fun scroll(text: String) = compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    private var captureIndex = 0
    private fun acknowledge() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("저장 완료").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("저장 완료").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        // Window fade-in is driven by Android, separately from the Compose test clock.
        android.os.SystemClock.sleep(350)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(app.cacheDir, "all-save-${++captureIndex}-${System.currentTimeMillis()}.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        compose.onNodeWithTag("record-saved-confirm").assertIsDisplayed().performTouchInput { click(); advanceEventTime(20); click() }
        compose.onNodeWithTag("record-saved-dialog").assertDoesNotExist()
    }
    @Test fun bodySaveWaitsBeforeCallback(): Unit {
        val vm = settings(); val store = ViewModelStore(); store.put("settings", vm)
        var followups = 0
        try {
            compose.setContent { HealthCareTheme { SettingsScreen(vm, initialSection = "BODY", onBodyWeightSaved = { followups++ }) } }
            compose.runOnIdle {
                vm.onBodySexSelected(BodySex.MALE); vm.onBodyAgeChange("30"); vm.onBodyHeightChange("175"); vm.onBodyWeightChange("70")
            }
            scroll("저장하기"); compose.onNodeWithText("저장하기").performTouchInput { click(); advanceEventTime(20); click() }
            compose.waitUntil(15000) { vm.saveAcknowledgement.isPending }
            assertEquals(70.0, app.bodyProfileStore.read()!!.weightKg, 0.0)
            assertEquals(0, followups); acknowledge(); assertEquals(1, followups)
            compose.onAllNodesWithText("내 신체정보").onFirst().assertExists()
        } finally { compose.runOnIdle { store.clear() } }
    }
    @Test fun energySaveShowsDialogAfterRepositorySuccess(): Unit {
        val vm = settings(); val store = ViewModelStore(); store.put("settings", vm)
        try {
            compose.setContent { HealthCareTheme { SettingsScreen(vm, initialSection = "ENERGY") } }
            compose.runOnIdle { vm.onBmrChange("1600"); vm.onActivityLevelSelected(ActivityLevel.LIGHT) }
            scroll("에너지 기준 저장"); compose.onNodeWithText("에너지 기준 저장").performTouchInput { click(); advanceEventTime(20); click() }
            compose.waitUntil(15000) { vm.saveAcknowledgement.isPending }
            assertEquals(1600, runBlocking { app.energyProfileRepository.getProfileForDate(java.time.LocalDate.now().toString()).first() }!!.basalMetabolicRateKcal)
            acknowledge(); compose.onNodeWithText("에너지 목표").assertExists()
        } finally { compose.runOnIdle { store.clear() } }
    }
    @Test fun manualGoalSaveClosesEditorOnlyAfterConfirmation(): Unit {
        val vm = settings(); val store = ViewModelStore(); store.put("settings", vm)
        try {
            compose.setContent { HealthCareTheme { SettingsScreen(vm, initialSection = "ENERGY") } }
            scroll("직접 설정한 일일 목표"); compose.onNodeWithText("직접 설정한 일일 목표").performClick()
            compose.onNode(hasSetTextAction() and hasText("목표 칼로리")).performTextReplacement("2100")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("저장").performTouchInput { click(); advanceEventTime(20); click() }
            compose.waitUntil(15000) { vm.saveAcknowledgement.isPending }
            compose.onNodeWithText("직접 목표 설정").assertExists()
            assertEquals(2100, runBlocking { app.goalRepository.latestGoal.first() }!!.targetCalories)
            acknowledge(); compose.onNodeWithText("직접 목표 설정").assertDoesNotExist()
        } finally { compose.runOnIdle { store.clear() } }
    }
    @Test fun preferenceSaveShowsPopupAndStaysOnSettings(): Unit {
        val vm = MealPreferenceViewModel(app.mealCoachRepository); val store = ViewModelStore(); store.put("preference", vm)
        try {
            compose.setContent { HealthCareTheme { MealPreferenceScreen(vm) {} } }
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(" 추천 설정 저장"))
            compose.onNodeWithText(" 추천 설정 저장").performTouchInput { click(); advanceEventTime(20); click() }
            compose.waitUntil(15000) { vm.saveAcknowledgement.isPending }
            assertNotNull(vm.uiState.value.message)
            acknowledge(); compose.onNodeWithText(" 추천 설정 저장").assertExists()
        } finally { compose.runOnIdle { store.clear() } }
    }
    @Test fun exerciseWeightSaveShowsPopupAfterConfirmedDiskWrite(): Unit {
        var writes = 0
        compose.setContent { HealthCareTheme { ExerciseCoachScreen(80, 70.0, {
            writes++; app.bodyProfileStore.saveExerciseWeight(it)
        }, {}) } }
        compose.onNode(hasSetTextAction() and hasText("몸무게")).performTextReplacement("71")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("몸무게 저장").performTouchInput { click(); advanceEventTime(20); click() }
        compose.waitUntil(15000) { compose.onAllNodesWithText("저장 완료").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(71.0, app.bodyProfileStore.readExerciseWeight()!!, 0.0)
        assertEquals(1, writes); acknowledge(); assertEquals(1, writes)
        compose.onNodeWithText("오늘의 움직임").assertExists()
    }
    @Test fun photoResultSaveWaitsForPopupBeforeOriginalBack(): Unit {
        val repository = object : com.example.healthcare.data.photo.FoodPhotoAnalysisRepository {
            override suspend fun analyze(request: com.example.healthcare.data.photo.model.FoodPhotoAnalysisRequest,
                onProgress: (com.example.healthcare.data.photo.model.PhotoAnalysisProgress) -> Unit
            ): com.example.healthcare.data.photo.model.PhotoAnalysisOutcome =
                com.example.healthcare.data.photo.model.PhotoAnalysisOutcome.Success(
                    com.example.healthcare.data.photo.model.FoodPhotoAnalysis(
                        analysisId = "isolated-qa-analysis", requestId = request.requestId,
                        analyzedAt = "2026-10-06T12:00:00Z",
                        detectedItems = listOf(com.example.healthcare.data.photo.model.DetectedFoodItem(
                            itemId = "isolated-qa-item", foodName = "사진 저장 검증", alternativeNames = emptyList(),
                            confidence = 0.8, estimatedAmount = 1.0, amountUnit = "인분", estimatedKcal = 550,
                            minimumKcal = 500, maximumKcal = 600, description = null)),
                        totalEstimatedKcal = 550, totalMinimumKcal = 500, totalMaximumKcal = 600,
                        warnings = emptyList(), modelVersion = "qa-fixture", isPartial = false))
        }
        val vm = AddRecordViewModel(app.mealRepository, app.foodRepository, repository)
        val store = ViewModelStore(); store.put("photo", vm); var backs = 0
        val before = runBlocking { app.mealRepository.allMeals.first() }
        try {
            compose.setContent { HealthCareTheme { AddRecordScreen(viewModel = vm, onBack = { backs++ }) } }
            compose.runOnIdle { vm.openCamera(); vm.onPhotoCaptured("qa-fixture.jpg"); vm.analyzePhoto("ko-KR", "Asia/Seoul") }
            compose.waitUntil(15000) { vm.photoState.value is PhotoAnalysisUiState.Result }
            scroll("확인한 내용으로 기록 저장")
            compose.onNodeWithText("확인한 내용으로 기록 저장").performTouchInput { click(); advanceEventTime(20); click() }
            compose.waitUntil(15000) { vm.photoSaveAcknowledgement.isPending }
            val added = runBlocking { app.mealRepository.allMeals.first() }.filter { meal -> before.none { it.id == meal.id } }
            assertEquals(1, added.size); assertEquals(550, added.single().calories)
            assertEquals(0, backs); acknowledge(); assertEquals(1, backs)
        } finally { compose.runOnIdle { store.clear() } }
    }

}
