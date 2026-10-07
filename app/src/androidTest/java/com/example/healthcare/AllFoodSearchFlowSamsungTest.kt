package com.example.healthcare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real search navigation and existing record calculations, never product application actions. */
class AllFoodSearchFlowSamsungTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val app get()=context.applicationContext as HealthcareApplication
    private val database get()=AppDatabase.getDatabase(context)
    @Before fun ready()=runBlocking {
        assertEquals("com.example.healthcare.qa",context.packageName)
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        withTimeout(180000) { while(database.foodItemDao().count()!=67357)delay(100) }
        waitTag("dashboard-root")
    }
    private fun waitTag(tag:String)=compose.waitUntil(30000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text:String)=compose.waitUntil(30000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun visible(text:String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
        compose.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }
    private fun flow(query:String,full:Boolean=true,exactId:String?=null)=runBlocking {
        val before=database.mealRecordDao().getAllMeals().first()
        val results=app.nutritionRepository.search(query).first()
        assertTrue(query,results.isNotEmpty())
        val groups=FoodSearchPolicy.groupSearchResults(results,query)
        val food=if(exactId!=null)results.single { it.id==exactId } else groups.map { it.representative }.first { FoodAmountPolicy.canCalculate(it) }
        val group=groups.single { it.representative.id==food.id || it.alternatives.any { a->a.id==food.id } }
        val model=FoodDetailPolicy.forFood(food)
        try {
            compose.onNodeWithTag("dashboard-meal-breakfast").performClick();waitText("음식 검색")
            compose.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름",substring=true)).performTextReplacement(query)
            Espresso.closeSoftKeyboard();waitTag("food-search-result-${groups.first().representative.id}")
            if(group.representative.id!=food.id) {
                compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("food-search-alternatives-${group.representative.id}"))
                compose.onNodeWithTag("food-search-alternatives-${group.representative.id}").performClick()
            }
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("food-search-result-${food.id}"))
            compose.onNodeWithTag("food-search-result-${food.id}").performClick();waitText("음식 상세")
            visible("${model.nutritionBasis} · ${model.nutrition[0].value}")
            visible(model.nutrition.drop(1).joinToString(" · ") { "${it.label} ${it.value}" })
            model.facts.forEach { visible("${it.label} · ${it.value}") }
            if(full) {
                val choice=FoodAmountPolicy.defaultChoice(food)!!
                val quantity=if(choice.unit in setOf("g","ml"))food.referenceAmount else 1.0
                val expected=FoodAmountPolicy.calculate(food,quantity,choice.unit)!!
                compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasSetTextAction() and hasText("수량"))
                compose.onNode(hasSetTextAction() and hasText("수량")).performTextReplacement(RecordedAmountSnapshot.format(quantity))
                Espresso.closeSoftKeyboard();visible("아침에 기록")
                compose.onNodeWithText("아침에 기록").performClick();waitTag("record-saved-dialog")
                compose.onNodeWithText("저장 완료").assertIsDisplayed()
                compose.onNodeWithTag("record-saved-confirm").performClick();waitTag("record-completion")
                val inserted=database.mealRecordDao().getAllMeals().first().filter { r->before.none { it.id==r.id } }.single()
                assertEquals(food.id,inserted.foodItemId);assertEquals(expected.calories,inserted.calories)
                assertEquals(expected.basisAmount,inserted.servingAmount!!,0.00001)
                compose.onNodeWithTag("completion-home").performClick();waitTag("dashboard-root")
                compose.onNode(hasText("통계") and hasClickAction()).performClick();waitText("식사 기록")
                compose.onNode(hasText("식사 기록") and hasClickAction()).performClick()
                compose.waitUntil(30000) { runCatching {
                    compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("history-item-edit-${inserted.id}"));true
                }.getOrDefault(false) }
                compose.onNode(hasText(model.name) and hasAnySibling(hasTestTag("history-item-edit-${inserted.id}"))).performClick()
                waitTag("history-detail-edit")
                compose.onNodeWithTag("history-detail-edit").performClick()
                compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("history-edit-save"))
                compose.onNodeWithTag("history-edit-save").performClick();waitTag("record-saved-dialog")
                compose.onNodeWithText("저장 완료").assertIsDisplayed()
                compose.onNodeWithTag("record-saved-confirm").performClick();waitTag("record-completion")
                assertEquals(expected.calories,database.mealRecordDao().getAllMeals().first().single { it.id==inserted.id }.calories)
            }
            val after=database.mealRecordDao().getAllMeals().first()
            before.forEach { old -> assertEquals(old,after.single { it.id==old.id }) }
            java.io.File(context.cacheDir,"all-food-search-flows.jsonl").appendText(org.json.JSONObject()
                .put("query",query).put("foodId",food.id).put("source",food.sourceType).put("status","PASS")
                .put("saveDetailEdit",full).put("searchResultCount",results.size).toString()+"\n")
        } finally {
            database.mealRecordDao().getAllMeals().first().filter { r->before.none { it.id==r.id } }.forEach { database.mealRecordDao().deleteMeal(it) }
        }
    }
    @Test fun spaghetti()=flow("스파게티")
    @Test fun kimbap()=flow("김밥")
    @Test fun egg()=flow("계란")
    @Test fun koreanEgg()=flow("달걀")
    @Test fun rice()=flow("밥")
    @Test fun ramen()=flow("라면")
    @Test fun jjapagetti()=flow("짜파게티")
    @Test fun pizza()=flow("피자")
    @Test fun chicken()=flow("치킨")
    @Test fun burger()=flow("햄버거")
    @Test fun salad()=flow("샐러드")
    @Test fun iceCream()=flow("아이스크림")
    @Test fun coffee()=flow("커피")
    @Test fun milk()=flow("우유")
    @Test fun bread()=flow("빵")
    @Test fun riceCake()=flow("떡")
    @Test fun soup()=flow("국")
    @Test fun stew()=flow("찌개")
    @Test fun friedRice()=flow("볶음밥")
    @Test fun pasta()=flow("파스타")
    @Test fun lunchBox()=flow("도시락")
    @Test fun newReferenceFood()=flow("느타리버섯",true,"kfind-d110-476140000-0001")
    @Test fun newFood()=flow("두릅산적")
    @Test fun caloriesOnlyFood()=flow("리틀텐")
    @Test fun officialFranchiseNutrition()=flow("맥도날드")
    @Test fun ambiguousSpaghettiActualManualDetail():Unit=runBlocking {
        val results=app.nutritionRepository.search("스파게티").first()
        val food=FoodSearchPolicy.groupSearchResults(results,"스파게티").map { it.representative }.first { FoodSearchPolicy.needsBasisReview(it) }
        val model=FoodDetailPolicy.forFood(food)
        compose.onNodeWithTag("dashboard-meal-breakfast").performClick();waitText("음식 검색")
        compose.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름",substring=true)).performTextReplacement("스파게티")
        Espresso.closeSoftKeyboard();waitTag("food-search-result-${food.id}")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("food-search-result-${food.id}"))
        compose.onNodeWithTag("food-search-result-${food.id}").performClick();waitText("음식 상세")
        visible("${model.nutritionBasis} · ${model.nutrition[0].value}")
        model.facts.forEach { visible("${it.label} · ${it.value}") }
        visible("원문 영양 기준량은 검토가 필요해요. 확인한 먹은 양과 칼로리를 직접 입력해 주세요.")
        assertTrue(FoodAmountPolicy.choices(food).isEmpty())
        Unit
    }
    @Test fun actualSpaghettiSubtypeSearchResultsRetainDistinctIdentities():Unit=runBlocking {
        val output=org.json.JSONArray()
        for(query in listOf("스파게티","토마토 스파게티","미트소스 스파게티","크림 스파게티","까르보나라","봉골레","해산물 스파게티")) {
            val results=app.nutritionRepository.search(query).first()
            assertTrue("Required spaghetti subtype query must resolve: $query",results.isNotEmpty())
            val detail=org.json.JSONArray()
            results.forEach { food ->
                val model=FoodDetailPolicy.forFood(food)
                assertEquals(food.id,model.foodId);assertTrue(model.nutritionBasis.isNotBlank())
                assertTrue(model.facts.any { it.key=="sourceReference" })
                detail.put(org.json.JSONObject().put("foodId",food.id).put("name",model.name).put("basis",model.nutritionBasis)
                    .put("nutrition",org.json.JSONArray(model.nutrition.map { it.value })).put("source",food.sourceType)
                    .put("referenceIds",org.json.JSONArray(model.compositionIds)))
            }
            output.put(org.json.JSONObject().put("query",query).put("searchCount",results.size).put("foods",detail))
        }
        java.io.File(context.cacheDir,"all-food-spaghetti-subtype-search.json").writeText(output.toString(2))
        Unit
    }
    @Test fun officialMenuWithoutNutritionRetainsDescriptionAndUnknownState():Unit=runBlocking {
        val menu=FranchiseCatalog.officialMenus("본죽").first { it.name=="본죽팥칼국수" }
        val model=FoodDetailPolicy.forMenu(menu)
        compose.onNodeWithTag("dashboard-meal-breakfast").performClick();waitText("음식 검색")
        compose.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름",substring=true)).performTextReplacement("본죽 팥칼국수")
        Espresso.closeSoftKeyboard();waitTag("franchise-menu-${menu.id}")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("franchise-menu-${menu.id}"))
        compose.onNodeWithTag("franchise-menu-${menu.id}").performClick();waitText("음식 상세")
        visible("현재 확인된 상세 영양정보가 없습니다.")
        model.facts.forEach { visible("${it.label} · ${it.value}") }
        model.ingredientText?.let { visible(it) }
        // Missing official values must remain manual; selecting a menu never assigns a fake zero.
        compose.onNodeWithText("현재 확인된 상세 영양정보가 없습니다.").assertIsDisplayed()
        Unit
    }
}
