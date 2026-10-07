package com.example.healthcare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.domain.FoodAmountPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** QA-only real search, Room save, acknowledgement and existing history edit navigation. */
class RecordSaveConfirmationSamsungTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val db get()=AppDatabase.getDatabase(context)
    private fun tag(value:String)=compose.waitUntil(30000) { compose.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty() }
    private fun text(value:String)=compose.waitUntil(30000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    private fun scrollTo(matcher:SemanticsMatcher)=compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)

    @Test fun actualSaveDoubleTapRotationForegroundConfirmationAndEdit():Unit=runBlocking {
        assertEquals("com.example.healthcare.qa",context.packageName)
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        tag("dashboard-root")
        val before=db.mealRecordDao().getAllMeals().first()
        try {
            compose.onNodeWithTag("dashboard-meal-breakfast").performClick();text("음식 검색")
            compose.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름",substring=true)).performTextReplacement("짜파게티")
            Espresso.closeSoftKeyboard()
            val id="official-retail-nongshim-p0000dyw-single-140g"
            tag("food-search-result-$id");compose.onNodeWithTag("food-search-result-$id").performClick();text("음식 상세")
            val food=db.foodItemDao().findById(id)!!
            val expected=FoodAmountPolicy.calculate(food,1.0,"봉")!!
            assertEquals(610,expected.calories)
            scrollTo(hasSetTextAction() and hasText("수량"))
            compose.onNode(hasSetTextAction() and hasText("수량")).performTextReplacement("1")
            Espresso.closeSoftKeyboard();scrollTo(hasText("아침에 기록"))
            compose.onNodeWithText("아침에 기록").performTouchInput { click();advanceEventTime(20);click() }
            tag("record-saved-dialog")
            compose.onNodeWithText("저장 완료").assertIsDisplayed()
            compose.onNodeWithText("기록이 저장되었습니다.").assertIsDisplayed()
            compose.onNodeWithTag("record-saved-confirm").assertIsDisplayed()
            compose.onNodeWithTag("record-completion").assertDoesNotExist()
            val saved=db.mealRecordDao().getAllMeals().first().filter { r->before.none { it.id==r.id } }.single()
            assertEquals(id,saved.foodItemId);assertEquals(610,saved.calories)
            assertEquals(140.0,saved.servingAmount!!,0.0)
            assertEquals(96.0,saved.carbohydrateGrams!!,0.0)
            assertEquals(9.0,saved.proteinGrams!!,0.0);assertEquals(20.0,saved.fatGrams!!,0.0)
            Espresso.pressBack();tag("record-saved-dialog")
            compose.activityRule.scenario.recreate();tag("record-saved-dialog")
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);tag("record-saved-dialog")
            assertEquals(1,db.mealRecordDao().getAllMeals().first().count { r->before.none { it.id==r.id } })
            compose.onNodeWithTag("record-saved-confirm").performTouchInput { click();advanceEventTime(20);click() }
            tag("record-completion");compose.onNodeWithTag("record-saved-dialog").assertDoesNotExist()
            compose.onNodeWithTag("completion-home").performClick();tag("dashboard-root")
            compose.onNode(hasText("통계") and hasClickAction()).performClick();text("식사 기록")
            compose.onNode(hasText("식사 기록") and hasClickAction()).performClick()
            compose.waitUntil(30000) { runCatching { scrollTo(hasTestTag("history-item-edit-${saved.id}"));true }.getOrDefault(false) }
            compose.onNode(hasText(saved.foodName) and hasAnySibling(hasTestTag("history-item-edit-${saved.id}"))).performClick()
            tag("history-detail-edit");compose.onNodeWithTag("history-detail-edit").performClick()
            scrollTo(hasTestTag("history-edit-save"));compose.onNodeWithTag("history-edit-save").performClick()
            tag("record-saved-dialog");compose.onNodeWithText("저장 완료").assertIsDisplayed()
            compose.onNodeWithText("기록이 수정되었습니다.").assertIsDisplayed()
            compose.onNodeWithTag("record-completion").assertDoesNotExist()
            assertEquals(610,db.mealRecordDao().getAllMeals().first().single { it.id==saved.id }.calories)
            compose.onNodeWithTag("record-saved-confirm").performClick();tag("record-completion")
            val after=db.mealRecordDao().getAllMeals().first()
            before.forEach { old->assertEquals(old,after.single { it.id==old.id }) }
            java.io.File(context.cacheDir,"record-save-confirmation-result.json").writeText(
                """{"status":"PASS","actualDbSave":true,"doubleTapRecordCount":1,"rotation":"PASS","foreground":"PASS","backKeepsConfirmation":true,"confirmBeforeNavigation":true,"edit":"PASS","calories":610,"basisGrams":140,"macros":[96,9,20],"existingRecordsUnchanged":true}""")
        } finally {
            db.mealRecordDao().getAllMeals().first().filter { r->before.none { it.id==r.id } }.forEach { db.mealRecordDao().deleteMeal(it) }
        }
        Unit
    }
}
