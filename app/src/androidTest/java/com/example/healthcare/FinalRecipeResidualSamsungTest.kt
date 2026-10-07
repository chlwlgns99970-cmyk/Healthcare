package com.example.healthcare

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.FoodMetadataStore
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.domain.RecipeCaloriePolicy
import com.example.healthcare.ui.screens.RecipeReferenceCard
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Packaged production-common assets on Samsung; record writes use only in-memory Room. */
class FinalRecipeResidualSamsungTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val kimbap="kfind-d101-007000000-0001"
    @Before fun loadPackagedAssets() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        runBlocking { FoodMetadataStore(context).ensureLoaded() }
    }
    private fun show(id:String) {
        compose.setContent { HealthCareTheme {
            val device=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(device.density,1.30f)) {
                Column(Modifier.width(360.dp).height(720.dp).verticalScroll(rememberScrollState())) {
                    RecipeReferenceCard(id)
                }
            }
        } }
    }
    private fun visible(text:String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
        compose.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }
    private fun capture(name:String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(context.cacheDir,"final-residual-$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        };bitmap.recycle()
    }
    @Test fun originalCompleteRemainsSeparate() {
        val id="kfind-d408-356000000-0001"
        assertTrue(RecipeCaloriePolicy.lookupCompositions(id).any { it.first().compositionKind=="ORIGINAL" && it.first().recipeComplete })
        show(id);visible("재료별 예상 열량");visible("원본 레시피에서 확인한 구성");capture("original-complete")
    }
    @Test fun referenceCompleteShowsAllInputs() {
        val rows=RecipeCaloriePolicy.lookupCompositions(kimbap).first { it.first().recipeId=="MENUZEN-D016004" }
        assertEquals(9,rows.size);assertTrue(rows.all { it.recipeComplete })
        show(kimbap);visible("김밥(햄) · ${rows.first().recipeBasis}")
        visible("${rows.last().ingredientName} ${com.example.healthcare.domain.RecordedAmountSnapshot.format(rows.last().amountGrams)}g · 약 ${kotlin.math.round(rows.last().estimatedKcal).toInt()} kcal")
        capture("reference-complete")
    }
    @Test fun partialOriginalDoesNotBecomeComplete() {
        val original=RecipeCaloriePolicy.lookupCompositions(kimbap).first { it.first().compositionKind=="ORIGINAL" }
        assertFalse(original.first().recipeComplete)
        show(kimbap);visible("재료별 예상 열량 · 확인 가능한 재료 기준");visible("확인 가능한 재료 기준 · 일부 재료만 표시합니다. 전체 재료의 합계가 아닙니다.")
        capture("partial-original")
    }
    @Test fun kimbapVariantIsExplicitlySeparate() {
        show(kimbap);visible("공식 김밥 레시피 참고 구성 · 선택한 음식의 실제 배합은 아닙니다.")
        assertEquals("MENUZEN-D016004",RecipeCaloriePolicy.lookup(kimbap).first().recipeId)
        capture("kimbap")
    }
    @Test fun newlyPublishedOfficialReferenceLoadsFromApk() {
        val rows=RecipeCaloriePolicy.lookupCompositions(kimbap).first { it.first().recipeId=="MFDS-BOOK3-P22" }
        assertEquals(5,rows.size);assertFalse(rows.first().recipeComplete)
        show(kimbap);visible("오징어불고기김밥 · ${rows.first().recipeBasis}")
        visible("참기름 15g · 약 138 kcal");capture("new-reference")
    }
    @Test fun referenceAmountsAndUnitArePreserved() {
        val rows=RecipeCaloriePolicy.lookupCompositions(kimbap).first { it.first().recipeId=="MENUZEN-D016004" }
        assertEquals(276.0,rows.sumOf { it.amountGrams },0.0)
        show(kimbap);visible("멥쌀, 백미, 밥 210g · 약 319 kcal");capture("unit")
    }
    @Test fun originalSugarSpoonConversionIsVisible() {
        val id="kfind-d315-664000000-0001"
        val sugar=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId=="MFDS-335" }
            .single { it.ingredientName=="설탕" }
        assertEquals("ORIGINAL",sugar.compositionKind)
        assertEquals(12.6,sugar.amountGrams,0.0)
        assertFalse(sugar.recipeComplete)
        show(id);visible("설탕 12.6g · 약 49 kcal")
        visible("원본 레시피에서 확인한 구성");capture("sugar-conversion")
    }
    @Test fun officialCaloriesInActualRecordScreenStay140() {
        val records=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val models=ViewModelStore();lateinit var add:AddRecordViewModel
        compose.activityRule.scenario.onActivity {
            add=AddRecordViewModel(MealRepository(records.mealRecordDao()),FoodRepository(records.frequentFoodDao()))
            models.put("reference-official-kcal",add);add.selectSearchFood(packagedFood(kimbap))
            add.onFoodQuantityChange("100")
        }
        try {
            compose.setContent { HealthCareTheme { com.example.healthcare.ui.screens.AddRecordScreen(viewModel=add) } }
            visible("100g 기준 · 140 kcal")
            assertEquals("140",add.uiState.value.calories)
            capture("official-kcal")
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() };records.close() }
    }
    @Test fun savedMealCaloriesNeverUseReferenceSum() {
        val records=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val models=ViewModelStore();lateinit var add:AddRecordViewModel;var saved=false
        compose.activityRule.scenario.onActivity {
            add=AddRecordViewModel(MealRepository(records.mealRecordDao()),FoodRepository(records.frequentFoodDao()))
            models.put("reference-record-kcal",add);add.selectSearchFood(packagedFood(kimbap))
            add.onServingUnitChange("g");add.onServingAmountChange("100");add.onCaloriesChange("140")
        }
        try {
            compose.runOnIdle { add.saveRecord { saved=true } };compose.waitUntil(5000) { saved }
            val meal=runBlocking { records.mealRecordDao().getAllMeals().first().single() }
            assertEquals(140,meal.calories);assertEquals(kimbap,meal.foodItemId)
            assertEquals(100.0,meal.servingAmount!!,0.0)
            assertTrue(kotlin.math.abs(RecipeCaloriePolicy.lookup(kimbap).sumOf { it.estimatedKcal }-140)>1)
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() };records.close() }
    }
    @Test fun namedVariantRetainsItsQualifiedRecipeName() {
        val id="kfind-d606-266000000-0001"
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId=="MENUZEN-D065004" }
        assertTrue(rows.first().recipeComplete)
        show(id);visible("김치찌개(돼지고기, 된장) · ${rows.first().recipeBasis}")
        visible("별도 공식 참고 구성 · 원본의 누락 중량을 대체한 값이 아닙니다.")
        capture("named-variant")
    }
    @Test fun surveyReferenceIsNotPresentedAsCookingRecipe() {
        val id="kfind-d705-213000000-0001"
        assertTrue(RecipeCaloriePolicy.lookupCompositions(id).any { it.first().compositionKind=="SURVEY_AVERAGE" })
        show(id);visible("재료별 예상 열량 · 공공 조사 평균 참고 구성")
        visible("식이 조사 평균 자료로, 음식 조리를 위한 정보로 사용하기에는 적절하지 않습니다.")
        capture("survey-reference")
    }
    @Test fun newParaeSurveyKeepsRadishQualifierAndWarning() {
        val id="kfind-d114-655000000-0001"
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId=="KDCA-281-13104" }
        assertTrue(rows.all { it.recipeComplete && it.compositionKind=="SURVEY_AVERAGE" })
        assertEquals("파래무침, 무",rows.first().recipeName)
        show(id);visible("재료별 예상 열량 · 공공 조사 평균 참고 구성")
        visible("식이 조사 평균 자료로, 음식 조리를 위한 정보로 사용하기에는 적절하지 않습니다.")
        capture("parae-radish-survey")
    }
    private fun newlyMappedOriginal(id: String, rid: String, name: String) {
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId==rid }
        assertTrue(rows.all { it.compositionKind=="ORIGINAL" && it.recipeComplete })
        assertEquals(rid,RecipeCaloriePolicy.lookup(id).first().recipeId)
        show(id);visible("원본 레시피에서 확인한 구성")
        visible("$name · ${rows.first().recipeBasis}");capture(rid)
    }
    @Test fun newlyMappedNogakOriginalIsComplete() {
        newlyMappedOriginal("kfind-d314-613000000-0001","MFDS-183","노각생채")
    }
    @Test fun newlyMappedDoenjangYeolmuOriginalIsComplete() {
        newlyMappedOriginal("kfind-d113-590050000-0001","MFDS-278","열무된장무침")
    }
    @Test fun newlyMappedFreshSquidVegetableOriginalIsComplete() {
        newlyMappedOriginal("kfind-d410-488320000-0001","MFDS-294","오징어야채볶음")
    }
    @Test fun newFishPorridgeReferenceKeepsGinsengQualifier() {
        val id="kfind-d304-187000000-0001"
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId=="MENUZEN-D040043" }
        assertTrue(rows.all { it.recipeComplete && it.compositionKind=="REFERENCE_RECIPE" })
        show(id);visible("인삼어죽 · ${rows.first().recipeBasis}")
        visible("별도 공식 참고 구성 · 원본의 누락 중량을 대체한 값이 아닙니다.")
        capture("ginseng-fish-porridge")
    }
    @Test fun directOriginalGramsIgnoreOtherRecipeHouseholdSizes() {
        val id="kfind-d106-297000000-0001"
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId=="RDA-89555" }
        assertFalse(rows.first().recipeComplete)
        assertEquals(250.0,rows.single { it.ingredientName=="두부" }.amountGrams,0.0)
        show(id);visible("두부 250g · 약 242 kcal");capture("original-local-grams")
    }
    private fun newNamedReference(id:String,rid:String,name:String) {
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId==rid }
        assertTrue(rows.all { it.recipeComplete && it.compositionKind=="REFERENCE_RECIPE" })
        show(id);visible("$name · ${rows.first().recipeBasis}")
        val last=rows.last()
        visible("${last.ingredientName} ${com.example.healthcare.domain.RecordedAmountSnapshot.format(last.amountGrams)}g · 약 ${kotlin.math.round(last.estimatedKcal).toInt()} kcal")
        capture(rid)
    }
    @Test fun newPotatoSaladKeepsGarlicChipName() { newNamedReference("kfind-d114-640020000-0001","MENUZEN-D135066","마늘칩 감자샐러드") }
    @Test fun newBeefPattyKeepsMushroomName() { newNamedReference("kfind-d108-376000000-0001","MENUZEN-D082037","버섯떡갈비") }
    @Test fun newSoupRiceKeepsBeefName() { newNamedReference("kfind-d301-004000000-0001","MENUZEN-D015004","소고기국밥") }
    private fun packagedFood(id:String):FoodItem=context.assets.open("fooddata/food_items.csv").bufferedReader().use { reader ->
        val headers=FoodMetadataStore.csv(requireNotNull(reader.readLine()))
        val values=reader.lineSequence().map(FoodMetadataStore::csv).first { it[0]==id }
        val row=headers.zip(values).toMap()
        FoodItem(id=id,sourceType=row.getValue("sourceType"),sourceFoodCode=row.getValue("sourceFoodCode"),
            name=row.getValue("name"),normalizedName=row.getValue("normalizedName"),referenceAmount=row.getValue("referenceAmount").toDouble(),
            unit=row.getValue("unit"),energyKcal=row.getValue("energyKcal").toDouble(),
            carbohydrateGrams=row["carbohydrateGrams"]?.toDoubleOrNull(),proteinGrams=row["proteinGrams"]?.toDoubleOrNull(),
            fatGrams=row["fatGrams"]?.toDoubleOrNull(),servingDescription=row.getValue("servingDescription"),
            dataVersion=row.getValue("dataVersion"),createdAt=row.getValue("createdAt").toLong(),updatedAt=row.getValue("updatedAt").toLong())
    }
}

