package com.example.healthcare

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.BodyProfileStore
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodySex
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QaRuntimeUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun requireIsolatedQaPackage() {
        assertEquals(QA_APPLICATION_ID, BuildConfig.APPLICATION_ID)
        assertEquals(QA_APPLICATION_ID, targetContext.packageName)
        if (BodyProfileStore(targetContext).read() == null) {
            BodyProfileStore(targetContext).save(
                BodyProfile(BodySex.MALE, ageYears = 35, heightCm = 175.0, weightKg = 70.0)
            )
            composeRule.activityRule.scenario.recreate()
        }
    }

    @Test
    fun manualRecordKeyboardPersistenceHistoryAndDeleteFlow() {
        // QA data persists between connected-test runs, including interrupted runs.
        // Use a unique label so a prior run cannot be mistaken for this record.
        val deletableRecordName = "목표 초과 검수 기록 ${System.currentTimeMillis()}"
        val startingCalories = runBlocking {
            (targetContext.applicationContext as HealthcareApplication).mealRepository
                .getTotalCaloriesByDate(LocalDate.now().toString()).first() ?: 0
        }
        openAddRecord()

        scrollTo(hasText("기록 저장"))
        composeRule.onNodeWithText("기록 저장").performClick()
        scrollTo(hasText("음식 이름을 입력해주세요."))
        composeRule.onNodeWithText("음식 이름을 입력해주세요.").assertIsDisplayed()
        scrollTo(hasText("올바른 칼로리를 입력해주세요."))
        composeRule.onNodeWithText("올바른 칼로리를 입력해주세요.").assertIsDisplayed()

        openPreciseAmount()
        input("음식 이름", LONG_FOOD_NAME)
        input("양", "1")
        input("단위", "인분")
        input("칼로리", "680")
        input("메모 (선택)", LONG_MEMO)
        scrollTo(hasText("점심"))
        composeRule.onNodeWithText("점심").performClick()

        scrollTo(hasText("기록 저장"))
        composeRule.onNodeWithText("기록 저장").assertIsDisplayed()
        saveRootScreenshot("keyboard-save-visible-384dp.png")
        Espresso.pressBack()
        scrollTo(hasSetTextAction() and hasText("음식 이름", substring = true))
        field("음식 이름").assertTextContains(LONG_FOOD_NAME)
        scrollTo(hasSetTextAction() and hasText("메모 (선택)", substring = true))
        field("메모 (선택)").assertTextContains(LONG_MEMO)
        scrollTo(hasText("기록 저장"))
        composeRule.onNodeWithText("기록 저장").performClick()
        awaitDashboardTotal(startingCalories + 680)

        saveRecord("김밥", "420", "아침")
        awaitDashboardTotal(startingCalories + 1_100)
        saveRecord("QA 간식 기록", "150", "간식")
        awaitDashboardTotal(startingCalories + 1_250)

        val beforeManual500 = formatTotal(startingCalories + 1_250)
        composeRule.onNodeWithText(beforeManual500).assertIsDisplayed()
        saveRecord("500 kcal 저장 검수", "500", "저녁")
        awaitDashboardTotal(startingCalories + 1_750)

        saveRecord(deletableRecordName, "400", "간식")
        awaitDashboardTotal(startingCalories + 2_150)
        saveRootScreenshot("dashboard-qa-data-384dp.png")

        composeRule.activityRule.scenario.recreate()
        awaitDashboardTotal(startingCalories + 2_150)

        composeRule.onNodeWithText("통계").performClick()
        composeRule.onNodeWithText("식사 기록").performClick()
        scrollTo(hasText(LONG_FOOD_NAME))
        composeRule.onNodeWithText(LONG_FOOD_NAME).assertIsDisplayed().performClick()
        scrollTo(hasText(LONG_MEMO))
        composeRule.onNodeWithText(LONG_MEMO).assertIsDisplayed()
        saveRootScreenshot("history-detail-long-content-384dp.png")
        composeRule.onNodeWithTag("history-detail-edit").performClick()
        replace("음식 이름", EDITED_FOOD_NAME)
        replace("칼로리", "700")
        scrollTo(hasText("저녁"))
        composeRule.onNodeWithText("저녁").performClick()
        scrollTo(hasSetTextAction() and hasText("메모 (선택)", substring = true))
        replace("메모 (선택)", EDITED_MEMO)
        scrollTo(hasText("수정 저장"))
        composeRule.onNodeWithText("수정 저장").performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodesWithText("저장 완료").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("record-saved-confirm").performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodesWithTag("record-completion").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("completion-home").performClick()
        composeRule.onNodeWithText("통계").performClick()
        composeRule.onNodeWithText("식사 기록").performClick()
        scrollTo(hasText(EDITED_FOOD_NAME))
        composeRule.onNodeWithText(EDITED_FOOD_NAME).assertIsDisplayed().performClick()
        scrollTo(hasText(EDITED_MEMO))
        composeRule.onNodeWithText(EDITED_MEMO).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("뒤로 가기").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(0)
        awaitDashboardTotal(startingCalories + 2_170)

        composeRule.onNodeWithText("통계").performClick()
        composeRule.onNodeWithText("식사 기록").performClick()
        scrollTo(hasText(deletableRecordName))
        composeRule.onNodeWithText(deletableRecordName).performClick()
        scrollTo(hasText("기록 삭제"))
        composeRule.onNodeWithContentDescription("기록 삭제").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(deletableRecordName).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(0)
        awaitDashboardTotal(startingCalories + 1_770)

        openAddRecord()
        openPreciseAmount()
        input("음식 이름", deletableRecordName)
        input("양", "1")
        input("단위", "회")
        input("칼로리", "400")
        scrollTo(hasText("간식"))
        composeRule.onNodeWithText("간식").performClick()
        scrollTo(hasText("기록 저장"))
        composeRule.onNodeWithText("기록 저장").performClick()
        awaitDashboardTotal(startingCalories + 2_170)
    }

    private fun openAddRecord() {
        composeRule.onNodeWithText("기록").performClick()
        composeRule.onNodeWithText("어떤 방식으로\n하루를 기록할까요?").assertIsDisplayed()
        composeRule.onNodeWithText("직접 입력").performClick()
        composeRule.onNodeWithText("기록 추가").assertIsDisplayed()
        composeRule.onNodeWithText("한 화면에서 자세히 입력").performClick()
    }

    private fun saveRecord(name: String, calories: String, mealType: String) {
        openAddRecord()
        openPreciseAmount()
        input("음식 이름", name)
        input("양", "1")
        input("단위", "회")
        input("칼로리", calories)
        scrollTo(hasText(mealType))
        composeRule.onNodeWithText(mealType).performClick()
        scrollTo(hasText("기록 저장"))
        composeRule.onNodeWithText("기록 저장").performClick()
    }

    private fun input(label: String, value: String) {
        val matcher = hasSetTextAction() and hasText(label, substring = true)
        scrollTo(matcher)
        composeRule.onNode(matcher).performClick().performTextInput(value)
    }

    private fun openPreciseAmount() {
        scrollTo(hasText("더 정확히 입력하기"))
        composeRule.onNodeWithText("더 정확히 입력하기").performClick()
    }

    private fun replace(label: String, value: String) {
        val matcher = hasSetTextAction() and hasText(label, substring = true)
        scrollTo(matcher)
        composeRule.onNode(matcher).performClick().performTextReplacement(value)
    }

    private fun field(label: String): SemanticsNodeInteraction = composeRule.onNode(
        hasSetTextAction() and hasText(label, substring = true)
    )

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
    }

    private fun awaitDashboardTotal(calories: Int) {
        if (composeRule.onAllNodesWithText("기록 저장").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.waitUntil(TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithTag("record-saved-confirm").fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (composeRule.onAllNodesWithText("저장 완료").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("record-saved-confirm").performClick()
            composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodesWithTag("record-completion").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithTag("completion-home").performClick()
        }
        composeRule.onNodeWithTag("bottom-tab-DASHBOARD").performClick()
        val value = formatTotal(calories)
        try {
            val inMealHistory = composeRule.onAllNodesWithText("식사 기록").fetchSemanticsNodes().isNotEmpty()
            val displayedTotal = if (inMealHistory) hasText("$value kcal") else hasText(value)
            if (inMealHistory) {
                scrollTo(displayedTotal)
            }
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                composeRule.onAllNodes(displayedTotal).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodes(displayedTotal).onFirst().assertIsDisplayed()
        } catch (failure: Throwable) {
            val persistedTotal = runBlocking {
                (targetContext.applicationContext as HealthcareApplication).mealRepository
                    .getTotalCaloriesByDate(LocalDate.now().toString()).first()
            }
            val visibleUi = composeRule.onAllNodes(
                SemanticsMatcher("has visible text") { it.config.getOrNull(SemanticsProperties.Text) != null }
            ).fetchSemanticsNodes().mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            }.joinToString(" | ").take(5_000)
            throw AssertionError(
                "Expected dashboard total $value; QA database total=$persistedTotal; visible UI=$visibleUi",
                failure
            )
        }
    }

    private fun formatTotal(calories: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(calories)

    private fun saveRootScreenshot(fileName: String) {
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { activity ->
            assertEquals(QA_APPLICATION_ID, activity.packageName)
            // The IME owns window focus while keyboard QA is in progress. Capture is limited to
            // the Compose root, so require the QA activity's decor view to be visibly attached
            // without treating the expected IME focus hand-off as another foreground app.
            assertTrue("PRIVACY_GUARD_TRIGGERED", activity.window.decorView.isShown)
        }
        val directory = File(targetContext.getExternalFilesDir(null), "ui-qa").apply { mkdirs() }
        FileOutputStream(File(directory, fileName)).use { output ->
            composeRule.onNode(isRoot()).captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private companion object {
        const val QA_APPLICATION_ID = "com.example.healthcare.qa"
        const val TIMEOUT_MILLIS = 10_000L
        const val LONG_FOOD_NAME = "닭가슴살과 현미밥을 곁들인 매콤한 구운 채소 샐러드"
        const val EDITED_FOOD_NAME = "수정한 닭가슴살 샐러드"
        const val EDITED_MEMO = "편집 기능 검수를 위해 수정한 메모입니다."
        const val LONG_MEMO = "오늘 점심 테스트를 위해 입력한 긴 메모입니다. " +
            "작은 화면과 키보드가 열린 상태에서도 저장 버튼과 오류 문구가 " +
            "정상적으로 보이는지 확인하기 위한 가상의 검수 데이터입니다."
    }
}
