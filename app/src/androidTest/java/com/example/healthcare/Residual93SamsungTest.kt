package com.example.healthcare

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.domain.RecipeCaloriePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Actual QA search/save/detail/edit. Private QA snapshot is restored after the run. */
class Residual93SamsungTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val database get()=AppDatabase.getDatabase(context)
    @Before fun ready()=runBlocking {
        assertEquals("com.example.healthcare.qa",context.packageName)
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        withTimeout(180000) { while (database.foodItemDao().count()!=67357) delay(100) }
        waitTag("dashboard-root")
    }
    private fun waitTag(tag:String)=compose.waitUntil(30000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text:String)=compose.waitUntil(30000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun visible(text:String) {
        compose.waitUntil(30000) { runCatching {
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text));true
        }.getOrDefault(false) }
        compose.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }
    private fun flow(name:String,id:String,reference:String,grams:String,kcal:Int)=runBlocking {
        val before=database.mealRecordDao().getAllMeals().first()
        try {
            compose.onNodeWithTag("dashboard-meal-breakfast").performClick()
            waitText("음식 검색")
            compose.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름",substring=true)).performTextReplacement(name)
            Espresso.closeSoftKeyboard()
            waitTag("food-search-result-$id")
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("food-search-result-$id"))
            compose.onNodeWithTag("food-search-result-$id").assertIsDisplayed().performClick()
            waitText("음식 상세")
            val rows=RecipeCaloriePolicy.lookupCompositions(id).single { it.first().recipeId==reference }
            assertTrue(rows.all { it.recipeComplete && it.amountGrams>0 })
            visible("재료별 예상 열량 · 공식 레시피 참고 구성")
            rows.forEach { visible("${it.ingredientName} ${com.example.healthcare.domain.RecordedAmountSnapshot.format(it.amountGrams)}g · 약 ${kotlin.math.round(it.estimatedKcal).toInt()} kcal") }
            visible("재료별 예상 열량 · 공식 레시피 참고 구성")
            java.io.File(context.cacheDir,"residual-93-$id.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasSetTextAction() and hasText("수량"))
            compose.onNode(hasSetTextAction() and hasText("수량")).performTextReplacement(grams)
            Espresso.closeSoftKeyboard()
            visible("$kcal kcal")
            visible("아침에 기록");compose.onNodeWithText("아침에 기록").performClick()
            waitTag("record-saved-dialog")
            compose.onNodeWithTag("record-saved-confirm").performClick();waitTag("record-completion")
            val inserted=database.mealRecordDao().getAllMeals().first().filter { r->before.none { it.id==r.id } }.single()
            assertEquals(id,inserted.foodItemId);assertEquals(kcal,inserted.calories)
            assertEquals(grams.toDouble(),inserted.servingAmount!!,0.00001)
            compose.onNodeWithTag("completion-home").performClick();waitTag("dashboard-root")
            compose.onNode(hasText("통계") and hasClickAction()).performClick();waitText("식사 기록")
            compose.onNode(hasText("식사 기록") and hasClickAction()).performClick()
            try { visible(name) } catch (error:Throwable) {
                java.io.File(context.cacheDir,"residual-93-history-semantics.txt").writeText(compose.onRoot().printToString(20))
                throw error
            }
            compose.onAllNodesWithText(name).onFirst().performClick();waitTag("history-detail-edit")
            compose.onNodeWithTag("history-detail-edit").performClick()
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("history-edit-save"))
            compose.onNodeWithTag("history-edit-save").performClick();waitTag("record-saved-dialog")
            compose.onNodeWithTag("record-saved-confirm").performClick();waitTag("record-completion")
            val after=database.mealRecordDao().getAllMeals().first()
            assertEquals(kcal,after.single { it.id==inserted.id }.calories)
            before.forEach { old->assertEquals(old,after.single { it.id==old.id }) }
        } finally {
            database.mealRecordDao().getAllMeals().first().filter { r->before.none { it.id==r.id } }.forEach { database.mealRecordDao().deleteMeal(it) }
        }
    }
    @Test fun dureupSearchReferenceSaveDetailEdit()=flow("두릅산적","rda-menuzen-d093044","MENUZEN-D093044","144.8",222)
    @Test fun dorajiSearchReferenceSaveDetailEdit()=flow("도라지양념구이","rda-menuzen-d083004","MENUZEN-D083004","112",168)
}
