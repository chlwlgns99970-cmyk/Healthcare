package com.example.healthcare

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.FoodMetadataStore
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.domain.*
import com.example.healthcare.ui.screens.FoodDetailEvidenceModel
import com.example.healthcare.ui.theme.HealthCareTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AllFoodDetailEvidenceSamsungTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun visible(text:String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
        compose.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }
    @Test fun allNewExactReferencesAndAmbiguousSpaghettiKeepSourceFacts()=runBlocking {
        assertEquals("com.example.healthcare.qa",context.packageName)
        FoodMetadataStore(context).ensureLoaded()
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val database=AppDatabase.getDatabase(context)
        val links=JSONArray(InstrumentationRegistry.getInstrumentation().context.assets.open("all-food-detail-new-reference-links.json").bufferedReader().readText())
        var model by mutableStateOf<FoodDetailModel?>(null)
        compose.setContent { HealthCareTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.30f)) {
                Column(Modifier.width(360.dp).height(720.dp).verticalScroll(rememberScrollState())) {
                    model?.let { FoodDetailEvidenceModel(it) }
                }
            }
        } }
        for(i in 0 until links.length()) {
            val link=links.getJSONObject(i)
            val food=database.foodItemDao().findById(link.getString("foodId"))!!
            val detail=FoodDetailPolicy.forFood(food)
            assertTrue(detail.compositionIds.contains(link.getString("referenceId")))
            compose.runOnIdle { model=detail }
            visible("재료별 예상 열량 · 공식 레시피 참고 구성")
            val composition=RecipeCaloriePolicy.lookupCompositions(food.id).single { it.first().recipeId==link.getString("referenceId") }
            composition.forEach { row -> visible("${row.ingredientName} ${RecordedAmountSnapshot.format(row.amountGrams)}g · 약 ${kotlin.math.round(row.estimatedKcal).toInt()} kcal") }
        }
        val spaghetti=database.foodItemDao().findById("kfind-d703-161000000-0001")!!
        val detail=FoodDetailPolicy.forFood(spaghetti)
        assertTrue(detail.basisNeedsReview);assertTrue(FoodAmountPolicy.choices(spaghetti).isEmpty())
        compose.runOnIdle { model=detail }
        visible("100ml 기준 · 100 kcal")
        detail.facts.forEach { visible("${it.label} · ${it.value}") }
        visible("원문 영양 기준량은 검토가 필요해요. 확인한 먹은 양과 칼로리를 직접 입력해 주세요.")
        val publicLinks=JSONArray(InstrumentationRegistry.getInstrumentation().context.assets.open("all-food-detail-public-recipe-links.json").bufferedReader().readText())
        for(i in 0 until publicLinks.length()) {
            val id=publicLinks.getJSONObject(i).getString("foodId")
            val sourceFood=database.foodItemDao().findById(id)!!
            val referenceModel=FoodDetailPolicy.forFood(sourceFood)
            compose.runOnIdle { model=referenceModel }
            visible("공공 레시피 원문 참고")
            referenceModel.referenceRecipe.forEach { visible("${it.label} · ${it.value}") }
            if(id=="kfind-d303-161490000-0001") {
                visible("공공 레시피 원문 참고")
                java.io.File(context.cacheDir,"all-food-tomato-reference.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
            }
        }
        java.io.File(context.cacheDir,"all-food-new-reference-ui.json").writeText("{\"newReferenceFoods\":${links.length()},\"pass\":${links.length()},\"publicTextReferenceFoodsPass\":${publicLinks.length()},\"spaghettiBasisReviewFacts\":\"PASS\",\"widthDp\":360,\"appFontScale\":1.30}")
    }
}
