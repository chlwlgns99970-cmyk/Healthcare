package com.example.healthcare

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.*
import com.example.healthcare.data.model.*
import com.example.healthcare.data.repository.*
import com.example.healthcare.data.seed.BundledFoodDataSeeder
import com.example.healthcare.domain.*
import com.example.healthcare.ui.screens.MealPlanScreen
import com.example.healthcare.ui.screens.TodayRecommendationContent
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** The actual bundled recommendation foods are read into an isolated in-memory database only. */
class RecommendationServingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var coach: MealCoachRepository
    private val models = ViewModelStore()
    private val fixturePrefs = "recommendation-serving-fixture"
    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.healthcare.qa", context.packageName)
        runBlocking { (context.applicationContext as HealthcareApplication).foodMetadataStore.ensureLoaded() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        coach = MealCoachRepository(db, db.mealCoachDao(), db.foodItemDao(), db.mealRecordDao())
    }
    @After fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() }
        db.close()
        context.getSharedPreferences(fixturePrefs, 0).edit().clear().commit()
    }
    private fun compact(content: @Composable () -> Unit) = compose.setContent {
        val native = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(native.density, 1.30f)) {
            HealthCareTheme { Box(Modifier.width(360.dp).height(780.dp)) { content() } }
        }
    }
    private fun baseFood(id: String, name: String, kcal: Double) = FoodItem(id, "QA", id, name,
        MealRecommendationEngine.normalizeFoodName(name), category = "밥류", referenceAmount = 100.0,
        unit = "g", energyKcal = kcal, carbohydrateGrams = 20.0, proteinGrams = 10.0, fatGrams = 5.0,
        servingDescription = "100g 기준", dataVersion = "verified fixture", createdAt = 0, updatedAt = 0)

    @Test fun verifiedEggTwoCountPersistsThroughConsumptionAt360AndLargeText() {
        val egg = baseFood("egg", "달걀_삶은 것", 155.0).copy(sourceType = "USDA-SR-LEGACY", sourceFoodCode = "FDC-173424",
            carbohydrateGrams = 1.12, proteinGrams = 12.58, fatGrams = 10.61)
        verifyConsumption(egg, 100.0, 1.0, "2", "개", 155)
    }
    @Test fun verifiedRollOneNaturalServingPersistsThroughConsumptionAt360AndLargeText() {
        val roll = baseFood("roll", "백종원한줄김밥", 137.0).copy(sourceType = "K-FIND-PRODUCT",
            sourceFoodCode = "P123-203020200-2032", carbohydrateGrams = 25.95, proteinGrams = 4.57, fatGrams = 1.64, servingDescription = "100g 기준 · 공식 총내용량 216g · 포장단위 줄")
        // The old 280g template is replaced by a verified one-roll candidate before the detail is displayed.
        verifyConsumption(roll, 280.0, 216.0 / 280.0, "1", "줄", 296)
    }
    private fun verifyConsumption(food: FoodItem, base: Double, portion: Double, quantity: String, unit: String, kcal: Int) {
        val id = "fixture-${food.id}"
        runBlocking {
            coach.ensureDefaultPreference()
            db.foodItemDao().upsertAll(listOf(food))
            db.mealCoachDao().upsertTemplates(listOf(MealTemplate(id, food.name, "|LUNCH|", kcal,
                10.0, 20.0, 5.0, 10, "LOW", "|COOK|INGREDIENTS_COMPLETE|", source = "verified fixture", createdAt = 0, updatedAt = 0)))
            db.mealCoachDao().upsertIngredients(listOf(MealTemplateIngredient(mealTemplateId = id,
                foodItemId = food.id, amount = base, unit = food.unit, adjustable = true)))
        }
        lateinit var vm: MealPlanViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = MealPlanViewModel(coach, NutritionRepository(db.foodItemDao())); models.put("meal", vm)
        }
        var completed: MealRecord? = null
        compact {
            MealPlanScreen(MealType.LUNCH, kcal, 1500, vm, onBack = {}, onSaved = {},
                dailyTemplateId = id, dailyPortion = portion, onSavedRecord = { completed = it })
        }
        compose.waitUntil(15_000) { vm.uiState.value.selectedMeal != null }
        scroll(hasTestTag("food-amount-quantity"))
        compose.onNodeWithTag("food-amount-quantity").assertTextContains(quantity)
        compose.onNodeWithTag("food-amount-unit").assertTextContains(unit, substring = true)
        val bounds = compose.onNodeWithTag("food-amount-quantity").getUnclippedBoundsInRoot()
        assertTrue((bounds.right - bounds.left).value > 70f && (bounds.right - bounds.left).value <= 360f)
        assertEquals("$quantity$unit", vm.uiState.value.selectedMeal!!.ingredients.single().consumedSnapshot()?.label)
        assertFalse(vm.hasUnsavedInput)
        val ingredientId = vm.uiState.value.selectedMeal!!.ingredients.single().ingredientId
        compose.runOnIdle { vm.setConsumedRatio(0.5) }
        assertTrue(vm.hasUnsavedInput)
        compose.runOnIdle { vm.setConsumedRatio(1.0); vm.setIngredientQuantity(ingredientId, "0") }
        assertTrue(vm.hasUnsavedInput)
        compose.runOnIdle { vm.setIngredientQuantity(ingredientId, quantity) }
        assertFalse(vm.hasUnsavedInput)
        compose.runOnIdle { vm.setIngredientQuantityUnit(ingredientId, "g") }
        assertTrue(vm.hasUnsavedInput)
        compose.runOnIdle { vm.setIngredientQuantityUnit(ingredientId, unit) }
        assertFalse(vm.hasUnsavedInput)
        scroll(hasText("먹었어요")); compose.onNodeWithText("먹었어요").performClick()
        compose.waitUntil(15_000) { completed != null }
        val rows = runBlocking { db.mealRecordDao().getAllMeals().first() }
        assertEquals(1, rows.size); assertEquals(rows.single(), completed)
        assertEquals("$quantity$unit", RecordedAmountSnapshot.from(rows.single())?.label)
        assertEquals(kcal, rows.single().calories)
        // A completed view model rejects a second confirmation and preserves the single committed row.
        compose.runOnIdle { vm.confirmConsumed() }
        assertEquals(1, runBlocking { db.mealRecordDao().getAllMeals().first().size })
    }
    private fun scroll(matcher: SemanticsMatcher) = compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)

    @Test fun real292MenusProduce1500GoalPlanWithBoundedPortionsAt360AndLargeText() {
        val isolatedContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences(fixturePrefs, mode)
        }
        val (seeds, plan) = runBlocking {
            val seeded = BundledFoodDataSeeder(isolatedContext, db).seedIfAvailable()
            assertTrue(seeded is BundledFoodDataSeeder.SeedResult.Seeded)
            val actualSeeds = coach.dailyPlanSeeds()
            val profile = EnergyProfileHistory(basalMetabolicRateKcal = 1500, activityLevelCode = ActivityLevel.LIGHT,
                palMultiplier = 1.55, targetMode = TargetMode.BMR, effectiveFromDate = java.time.LocalDate.now().toString())
            val target = DailyCalorieTarget.resolve(null, profile)
            assertEquals(1500, target)
            val actualPlan = requireNotNull(DailyMealPlanEngine.generate(java.time.LocalDate.now().toString(), target,
                DailyRecommendationTheme.CHEAT, actualSeeds, coach.ensureDefaultPreference()))
            actualSeeds to actualPlan
        }
        assertEquals(292, seeds.size); assertEquals(4, plan.meals.size); assertEquals(1500, plan.targetKcal)
        val reviewed=listOf("kfind-catalog-d305-239000000-0001" to 150.0,"kfind-catalog-d110-472000000-0001" to 50.0)
        reviewed.forEach { (id,amount) ->
            val seed=seeds.single { it.template.id==id }
            val links=runBlocking { db.mealCoachDao().getIngredients(id) }
            val option=RecommendationServingPolicy.available(seed,MealType.LUNCH).single()
            assertEquals(amount,links.single().amount*option.portion,0.00001)
            assertTrue(option.labels.single().endsWith("${amount.toInt()}g"))
        }
        plan.meals.forEach { meal ->
            val serving = requireNotNull(RecommendationServingPolicy.selected(seeds.single { it.template.id == meal.templateId }, meal.mealType, meal.portion))
            assertEquals(serving.kcal, meal.kcal); assertEquals(serving.labels, meal.amountLabels)
        }
        assertTrue(plan.meals.last().kcal <= 225)
        compact { TodayRecommendationContent(TodayMealPlanUiState(selectedTheme = plan.theme, plan = plan, target = 1500),
            onSelect = {}, onThemes = {}, onReplace = {}, onAlternate = {}, onRetry = {}, onPreferences = {}, onEnergy = {}, onOpenMeal = { _, _ -> }) }
        plan.meals.forEach { meal ->
            scroll(hasTestTag("daily-name-${meal.mealType.name}"))
            compose.onNodeWithTag("daily-name-${meal.mealType.name}").assertIsDisplayed()
            scroll(hasTestTag("daily-amount-${meal.mealType.name}"))
            compose.onNodeWithTag("daily-amount-${meal.mealType.name}").assertTextContains(meal.amountLabels.single())
            val bounds = compose.onNodeWithTag("daily-amount-${meal.mealType.name}").getUnclippedBoundsInRoot()
            assertTrue((bounds.right - bounds.left).value <= 360f)
        }
    }
    @Test fun realRepositoryKimbapSearchRetainsNamedServingBeforeResultTruncationAt360AndLargeText() {
        val isolated=object : ContextWrapper(context) {
            override fun getSharedPreferences(name:String,mode:Int)=context.getSharedPreferences(fixturePrefs,mode)
        }
        val results=runBlocking {
            assertTrue(BundledFoodDataSeeder(isolated,db).seedIfAvailable() is BundledFoodDataSeeder.SeedResult.Seeded)
            val started=android.os.SystemClock.elapsedRealtime()
            val found=NutritionRepository(db.foodItemDao()).search("김밥").first()
            println("FOLLOWUP_REAL_SEARCH ${android.os.SystemClock.elapsedRealtime()-started}ms ${found.size} results")
            found
        }
        val groups=FoodSearchPolicy.groupSearchResults(results,"김밥")
        assertEquals("김밥",groups.first().representative.name)
        val roll=groups[1].representative
        assertEquals("kfind-product-p123-203020200-2032",roll.id)
        assertEquals("줄",FoodAmountPolicy.defaultChoice(roll)!!.unit)
        compact { com.example.healthcare.ui.screens.SmartFoodInputScreen(
            state=SmartInputUiState(mode=SmartInputMode.SEARCH,searchQuery="김밥",searchResults=results),recentMeals=emptyList(),
            onBack={},onPhoto={},onBarcode={},onNutritionLabel={},onSearch={},onSearchQueryChange={},onFoodSelected={},
            onUseBarcodeItem={},onOcrCandidateSelected={},onOcrAmountChange={},onConfirmOcr={},onManual={},onRegisterBarcode={},
            onRepeatRecent={},configuredAllergies=emptySet()) }
        scroll(hasTestTag("food-search-result-${roll.id}"))
        compose.onNodeWithTag("food-search-result-${roll.id}").assertIsDisplayed()
        compose.onNodeWithText("1줄 · 296 kcal").assertIsDisplayed()
        compose.onNodeWithText("1줄 216g 기준").assertIsDisplayed()
        compose.onNodeWithText("브랜드·제조사 · (주)비지에프푸드").assertIsDisplayed()
        compose.onNodeWithTag("allergy-notice-UNRESOLVED").assertDoesNotExist()
    }
}
