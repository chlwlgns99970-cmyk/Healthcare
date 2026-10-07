package com.example.healthcare

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import com.example.healthcare.data.FoodMetadataStore

/** Current search -> detail -> quantity flow, without writing a private meal. */
class FinalRecipeSearchSamsungTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun keepAwake() {
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName=="com.example.healthcare.qa")
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    private fun find(text:String, substring:Boolean=false) {
        compose.waitUntil(20000) {
            runCatching {
                compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text,substring=substring))
                true
            }.getOrDefault(false)
        }
    }
    private fun search(query:String) {
        compose.onNode(hasClickAction() and hasText("기록")).performClick()
        compose.waitUntil(20000) { compose.onAllNodes(hasText("음식 검색",substring=true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasClickAction() and hasText("음식 검색",substring=true)).performClick()
        compose.waitUntil(20000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름",substring=true)).performTextInput(query)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
    }
    @Test fun tunaSearchStillProvidesGeneralDishAndGramNutrition() {
        search("참치김밥");find("참치김밥")
        val alternatives=SemanticsMatcher("same dish alternatives") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("food-search-alternatives-")==true } and hasAnyAncestor(hasText("참치김밥"))
        compose.waitUntil(20000) { runCatching { compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(alternatives);true }.getOrDefault(false) }
        compose.onAllNodes(alternatives).onFirst().performClick()
        val matcher=SemanticsMatcher("food result card") { it.config.getOrNull(SemanticsProperties.TestTag)?.let { tag -> tag.startsWith("food-search-result-kfind-d") && tag.contains("-007450000-") }==true } and hasText("참치",substring=true) and hasText("김밥",substring=true)
        try {
            compose.waitUntil(20000) { runCatching { compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher);true }.getOrDefault(false) }
        } catch (error: Throwable) {
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"tuna-search-semantics.txt").writeText(compose.onRoot().printToString(20))
            throw error
        }
        val card=compose.onAllNodes(matcher).onFirst()
        val fid=card.fetchSemanticsNode().config[SemanticsProperties.TestTag].removePrefix("food-search-result-")
        val food=InstrumentationRegistry.getInstrumentation().targetContext.assets.open("fooddata/food_items.csv").bufferedReader().use { reader ->
            val fields=FoodMetadataStore.csv(requireNotNull(reader.readLine()))
            fields.zip(reader.lineSequence().map(FoodMetadataStore::csv).first { it[0]==fid }).toMap()
        }
        assertEquals("김밥_참치",food.getValue("name"))
        val expected="${food.getValue("energyKcal")} kcal"
        println("Tuna selected foodId=$fid official=$expected basis=${food.getValue("referenceAmount")}${food.getValue("unit")}")
        card.assertIsDisplayed().performClick()
        compose.waitUntil(20000) { runCatching { compose.onNodeWithText("음식 상세").assertIsDisplayed();true }.getOrDefault(false) }
        find(expected,true)
        compose.onAllNodes(hasText(expected,substring=true)).onFirst().assertIsDisplayed()

    }
    @Test fun noodleSearchStillCalculatesQuantityWithoutRecipeSum() {
        search("신라면");find("1봉 120g",true)
        compose.onNodeWithTag("food-search-result-kfind-product-p108-003000400-0138").performClick()
        compose.waitUntil(20000) { runCatching { compose.onNodeWithText("음식 상세").assertIsDisplayed();true }.getOrDefault(false) }
        find("500 kcal");compose.onNodeWithText("500 kcal").assertIsDisplayed()
        find("수량",true)
        compose.onNode(hasSetTextAction() and hasText("수량",substring=true)).performTextReplacement("2")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        find("1001 kcal");compose.onNodeWithText("1001 kcal").assertIsDisplayed()
    }
}
