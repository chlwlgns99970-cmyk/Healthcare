package com.example.healthcare

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.*
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.*
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.*
import com.example.healthcare.domain.*
import com.example.healthcare.ui.screens.*
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** All mutations use an isolated Room database and a test-only preferences namespace. */
@RunWith(AndroidJUnit4::class)
class TodayMealPlanUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var coach: MealCoachRepository
    private lateinit var repo: TodayMealPlanRepository
    private lateinit var vm: TodayMealPlanViewModel
    private lateinit var store: TodayMealPlanStore
    private lateinit var context: Context
    private val models = ViewModelStore()
    private var density = 1f
    private val day get() = LocalDate.now().toString()

    @Before fun fixture() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.healthcare.qa",context.packageName)
        context.getSharedPreferences("daily-plan-test-recommendation_cycle_v2",0).edit().clear().commit()
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("daily-plan-test-$name", mode)
        }
        db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        coach=MealCoachRepository(db,db.mealCoachDao(),db.foodItemDao(),db.mealRecordDao(),InMemoryRecommendationCycleStore())
        store=TodayMealPlanStore(isolated)
        repo=TodayMealPlanRepository(store,coach,GoalRepository(db.calorieGoalDao()),EnergyProfileRepository(db.energyProfileDao()),MealRepository(db.mealRecordDao()),FoodRepository(db.frequentFoodDao()))
        runBlocking {
            coach.ensureDefaultPreference()
            db.calorieGoalDao().insertGoal(CalorieGoal(targetCalories=1850,startDate=day))
            val templates=mutableListOf<MealTemplate>(); val ingredients=mutableListOf<MealTemplateIngredient>()
            for (meal in DailyMealPlanEngine.slots) for (n in 0..7) {
                val id="${meal.name}-$n"; val kcal=(if(meal==MealType.SNACK)150 else 500)+n*20
                val name=if(n==0) "오이 ${meal.displayName}" else "${meal.displayName} 검증 메뉴 $n"
                db.foodItemDao().upsertAll(listOf(FoodItem(id="food-$id",sourceType="QA",sourceFoodCode=id,
                    name=name,normalizedName=MealRecommendationEngine.normalizeFoodName(name),category="밥류",referenceAmount=100.0,unit="g",
                    energyKcal=kcal.toDouble(),carbohydrateGrams=60.0,proteinGrams=20.0,fatGrams=12.0,servingDescription="100g",dataVersion="QA",createdAt=0,updatedAt=0)))
                templates+=MealTemplate(id,name,"|${meal.name}|",kcal,20.0,60.0,12.0,10,"LOW","|COOK|INGREDIENTS_COMPLETE|","|대두|","","KOREAN","QA verified",0,0)
                ingredients+=MealTemplateIngredient(mealTemplateId=id,foodItemId="food-$id",amount=100.0,unit="g",adjustable=true,minimumAmount=50.0,maximumAmount=150.0,adjustmentStep=10.0)
            }
            db.mealCoachDao().upsertTemplates(templates); db.mealCoachDao().upsertIngredients(ingredients)
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm=TodayMealPlanViewModel(repo); models.put("daily",vm) }
    }
    @After fun close() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() }
        db.close()
        context.getSharedPreferences("daily-plan-test-recommendation_cycle_v2",0).edit().clear().commit()
    }
    private fun show() = compose.setContent {
        val native=LocalDensity.current
        density=native.density
        CompositionLocalProvider(LocalDensity provides Density(native.density,1.3f)) {
            HealthCareTheme(darkTheme=false,appFontScale=1.3f) {
                Box(Modifier.width(360.dp).height(760.dp).testTag("daily-viewport")) {
                    TodayRecommendationScreen(vm,{}, {}, {}, { _,_ -> })
                }
            }
        }
    }
    private fun scroll(tag: String) {
        compose.onNodeWithTag("daily-plan-list").performScrollToNode(hasTestTag(tag))
    }
    private fun select(theme: DailyRecommendationTheme) {
        scroll("daily-theme-${theme.name}")
        compose.onNodeWithTag("daily-theme-${theme.name}").performClick()
        compose.waitUntil(20_000) { !vm.uiState.value.loading && vm.uiState.value.selectedTheme==theme && (vm.uiState.value.plan!=null || vm.uiState.value.message!=null) }
    }
    private fun assertPlan(theme: DailyRecommendationTheme) {
        val p=requireNotNull(vm.uiState.value.plan)
        assertEquals(theme,p.theme); assertEquals(1850,p.targetKcal); assertEquals(4,p.meals.size)
        assertEquals(4,p.meals.map { it.templateId }.distinct().size); assertEquals(p.meals.sumOf { it.kcal },p.totalKcal)
        scroll("daily-plan-total")
        compose.onNodeWithTag("daily-plan-total").assertTextEquals("총 ${p.totalKcal} / 1850 kcal")
        p.meals.forEach { meal -> scroll("daily-name-${meal.mealType.name}"); compose.onNodeWithTag("daily-name-${meal.mealType.name}").assertTextEquals(meal.name).assertIsDisplayed() }
        assertTrue(runBlocking { db.mealRecordDao().getMealsByDate(day).first() }.isEmpty())
    }
    @Test fun selectorHasAllEightReadableThemesAndNoFoodAt360AndCombined169Font() {
        show()
        compose.onNodeWithTag("daily-plan-title").assertTextEquals("오늘은 어떻게 먹고 싶나요?")
        DailyRecommendationTheme.entries.forEach { theme ->
            val tag="daily-theme-${theme.name}"; scroll(tag)
            val bounds=compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.height>=48*density-1)
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(theme.label,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty()); assertFalse("${theme.name} textSize=${layouts.first().size} lines=${layouts.first().lineCount} bottom=${layouts.first().getLineBottom(layouts.first().lineCount-1)} widthOverflow=${layouts.first().didOverflowWidth}",layouts.first().hasVisualOverflow)
            compose.onNodeWithText(theme.description,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> val description=mutableListOf<TextLayoutResult>(); action(description); assertFalse(description.first().hasVisualOverflow) }
        }
        compose.onNodeWithTag("daily-meal-BREAKFAST").assertDoesNotExist()
        compose.onNodeWithTag("daily-plan-total").assertDoesNotExist()
    }
    @Test fun selectionAndBackDuringGenerationKeepLatestThemeWithoutPublishingPreviousPlan() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm.selectTheme(DailyRecommendationTheme.BALANCED)
            assertTrue("Generation must be in progress for this regression", vm.uiState.value.loading)
            vm.showThemes()
            assertNull(vm.uiState.value.selectedTheme)
            vm.selectTheme(DailyRecommendationTheme.DIET)
            assertEquals(DailyRecommendationTheme.DIET, vm.uiState.value.selectedTheme)
            vm.selectTheme(DailyRecommendationTheme.CHEAT)
            assertEquals(DailyRecommendationTheme.CHEAT, vm.uiState.value.selectedTheme)
            assertNull(vm.uiState.value.plan)
        }
        compose.waitUntil(20_000) {
            val current = vm.uiState.value
            assertTrue("Previous theme must never appear under the latest selection",
                current.plan == null || current.plan?.theme == current.selectedTheme)
            !current.loading && current.plan != null
        }
        assertEquals(DailyRecommendationTheme.CHEAT, vm.uiState.value.selectedTheme)
        assertEquals(DailyRecommendationTheme.CHEAT, vm.uiState.value.plan?.theme)
        assertEquals(1850, vm.uiState.value.plan?.targetKcal)
        assertTrue(runBlocking { db.mealRecordDao().getMealsByDate(day).first() }.isEmpty())
    }
    @Test fun homeIsCompactSummaryWithoutFoodCardsOrVerticalScroll() {
        compose.setContent {
            val native=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(native.density,1.3f)) {
                HealthCareTheme(darkTheme=false,appFontScale=1.3f) {
                    Box(Modifier.width(360.dp).height(760.dp)) {
                        DashboardContent(selectedDate=LocalDate.now(),meals=emptyList(),totalCalories=0,targetCalories=1850,
                            statusText="목표까지 1,850 kcal",energyState=DashboardEnergyUiState(),
                            coachState=TodayCoachUiState(dailyThemeLabel="다이어트",dailyPlanKcal=1830,dailyPlanTarget=1850),
                            onPreviousDay={},onNextDay={},onAddRecord={},onOpenEnergySettings={},onOpenRecommendations={})
                    }
                }
            }
        }
        compose.onNodeWithText("다이어트 식단").assertIsDisplayed()
        compose.onNodeWithText("1,830 / 1,850 kcal").assertIsDisplayed()
        compose.onNodeWithText("오늘 식단 보기").assertIsDisplayed()
        assertFalse(compose.onNodeWithTag("dashboard-root").fetchSemanticsNode().config.contains(SemanticsActions.ScrollBy))
        compose.onNodeWithTag("dashboard-recommendation-1").assertDoesNotExist()
    }
    @Test fun dietGeneratesFourMealsWithRealTarget() { show(); select(DailyRecommendationTheme.DIET); assertPlan(DailyRecommendationTheme.DIET) }
    @Test fun bulkKeepsTargetAndKnownProtein() { show(); select(DailyRecommendationTheme.BULK); assertPlan(DailyRecommendationTheme.BULK); assertTrue(vm.uiState.value.plan!!.meals.all { it.nutrition.proteinGrams==20.0 }) }
    @Test fun balancedUsesRealMacros() { show(); select(DailyRecommendationTheme.BALANCED); assertPlan(DailyRecommendationTheme.BALANCED); assertTrue(vm.uiState.value.plan!!.macroComplete) }
    @Test fun healthyUsesExplicitInformationRule() { show(); select(DailyRecommendationTheme.HEALTHY); assertPlan(DailyRecommendationTheme.HEALTHY) }
    @Test fun cheatKeepsTargetAndDislikesWhileApplyingPreference() {
        runBlocking { coach.savePreference(coach.ensureDefaultPreference().copy(preferredFoods="|STYLE:KOREAN|")); coach.addExcludedFood("오이","DISLIKE") }
        show(); select(DailyRecommendationTheme.CHEAT); assertPlan(DailyRecommendationTheme.CHEAT)
        assertTrue(vm.uiState.value.plan!!.meals.none { it.name.contains("오이") }); assertTrue(vm.uiState.value.plan!!.reasons.any { it.contains("취향") })
    }
    @Test fun slowStyleShowsAccurateLimitationAndNoFood() {
        show(); select(DailyRecommendationTheme.SLOW_AGING_STYLE); scroll("daily-plan-message")
        compose.onNodeWithTag("daily-plan-message").assertTextEquals(DailyMealThemePolicy.SLOW_STYLE_LIMITATION)
        compose.onNodeWithTag("daily-meal-BREAKFAST").assertDoesNotExist()
    }
    @Test fun lightAndHeartyUseTheirRealRelativePools() {
        show(); select(DailyRecommendationTheme.LIGHT); assertPlan(DailyRecommendationTheme.LIGHT)
        compose.runOnIdle { vm.showThemes() }; select(DailyRecommendationTheme.HEARTY); assertPlan(DailyRecommendationTheme.HEARTY)
    }
    @Test fun onlyLunchChangesAndTotalIsRecomputed() {
        show(); select(DailyRecommendationTheme.DIET); val before=vm.uiState.value.plan!!
        scroll("daily-replace-LUNCH"); compose.onNodeWithTag("daily-replace-LUNCH").assertIsDisplayed().performClick()
        compose.waitUntil(20_000) { !vm.uiState.value.loading && vm.uiState.value.plan!!.signature!=before.signature }
        val after=vm.uiState.value.plan!!
        assertEquals(before.meals.filterNot { it.mealType==MealType.LUNCH },after.meals.filterNot { it.mealType==MealType.LUNCH })
        assertNotEquals(before.meals[1].templateId,after.meals[1].templateId); assertEquals(after.meals.sumOf { it.kcal },after.totalKcal)
    }
    @Test fun alternateKeepsThemeAndAvoidsPreviousPlan() {
        show(); select(DailyRecommendationTheme.DIET); val before=vm.uiState.value.plan!!
        scroll("daily-plan-alternate"); compose.onNodeWithTag("daily-plan-alternate").assertIsDisplayed().performClick()
        compose.waitUntil(20_000) { !vm.uiState.value.loading && vm.uiState.value.plan!!.signature!=before.signature }
        assertEquals(before.theme,vm.uiState.value.plan!!.theme)
    }
    @Test fun alreadyRecordedBreakfastIsLockedAndSubtracted() {
        runBlocking { db.mealRecordDao().insertMeal(MealRecord(id=99,date=day,time="08:00",mealType=MealType.BREAKFAST,foodName="실제 아침",calories=450)) }
        show(); select(DailyRecommendationTheme.DIET)
        val p=vm.uiState.value.plan!!; assertTrue(p.meals.first().recorded); assertEquals(450,p.recordedKcal); assertEquals(1400,p.remainingBudgetKcal)
        scroll("daily-recorded-BREAKFAST"); compose.onNodeWithTag("daily-recorded-BREAKFAST").assertIsDisplayed()
        compose.onNodeWithTag("daily-replace-BREAKFAST").assertDoesNotExist()
        assertEquals(1,runBlocking { db.mealRecordDao().getMealsByDate(day).first() }.size)
    }
    @Test fun persistedPlanSurvivesNewStoreAndSettingsRepairOnlyInvalidSlot() {
        val initial=runBlocking { requireNotNull(repo.create(DailyRecommendationTheme.DIET)) }
        val wrapper=object:ContextWrapper(context) { override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("daily-plan-test-$name",mode) }
        val restarted=TodayMealPlanStore(wrapper)
        val repo2=TodayMealPlanRepository(restarted,coach,GoalRepository(db.calorieGoalDao()),EnergyProfileRepository(db.energyProfileDao()),MealRepository(db.mealRecordDao()))
        val reopened=runBlocking { requireNotNull(repo2.create(DailyRecommendationTheme.DIET)) }
        assertEquals(initial.signature,reopened.signature); assertEquals(1.0,restarted.state.value!!.meals.first().portion,0.0)
        val lunch=initial.meals[1]
        runBlocking { coach.addExcludedFood(lunch.name,"DISLIKE") }
        val repaired=runBlocking { requireNotNull(repo2.create(DailyRecommendationTheme.DIET)) }
        assertNotEquals(lunch.templateId,repaired.meals[1].templateId)
        assertEquals(initial.meals.filterNot { it.mealType==MealType.LUNCH },repaired.meals.filterNot { it.mealType==MealType.LUNCH })
        runBlocking { db.calorieGoalDao().insertGoal(CalorieGoal(targetCalories=2300,startDate=day)) }
        assertEquals(2300,runBlocking { requireNotNull(repo2.create(DailyRecommendationTheme.DIET)).targetKcal })
    }
    @Test fun noGoalPromptsEnergySettingsAndNeverUses2000() {
        runBlocking { db.openHelper.writableDatabase.execSQL("DELETE FROM calorie_goals") }
        show(); select(DailyRecommendationTheme.DIET); scroll("daily-plan-message")
        compose.onNodeWithTag("daily-plan-message").assertTextEquals("하루 목표 칼로리를 먼저 설정해주세요.")
        compose.onNodeWithText("에너지 목표 설정").assertIsDisplayed(); assertNull(vm.uiState.value.plan)
    }
    @Test fun dayPlanDetailUsesExactIdPortionAndAllergyThenConsumptionRecordsOnce() {
        runBlocking { coach.addExcludedFood("대두","ALLERGY") }
        val p=runBlocking { requireNotNull(repo.create(DailyRecommendationTheme.DIET)) }
        val meal=p.meals.first()
        lateinit var detail:MealPlanViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            detail=MealPlanViewModel(coach,NutritionRepository(db.foodItemDao())); models.put("detail",detail)
            detail.loadDailyDetail(meal.templateId!!,meal.mealType,meal.kcal,p.targetKcal)
        }
        compose.waitUntil(20_000) { detail.uiState.value.selectedMeal!=null }
        val selected=detail.uiState.value.selectedMeal!!
        assertEquals(meal.templateId,selected.templateId); assertEquals(meal.kcal,selected.totalCalories)
        assertEquals(100.0,selected.ingredients.single().amount,0.0); assertTrue("대두" in selected.matchedAllergens)
        assertEquals(0,runBlocking { db.mealRecordDao().getMealsByDate(day).first() }.size)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { detail.requestConsumptionConfirmation(); detail.confirmConsumed(); detail.confirmConsumed() }
        compose.waitUntil(20_000) { detail.uiState.value.saved }
        assertEquals(1,runBlocking { db.mealRecordDao().getMealsByDate(day).first() }.size)
    }
    @Test fun snackFromFourSlotPlanCanBeRecordedWhenLegacyScheduleDisabledSnack() {
        runBlocking { coach.savePreference(coach.ensureDefaultPreference().copy(snackEnabled=false,breakfastRatio=30,lunchRatio=35,dinnerRatio=35,snackRatio=0)) }
        val plan=runBlocking { requireNotNull(repo.create(DailyRecommendationTheme.DIET)) }
        val meal=plan.meals.last()
        lateinit var detail:MealPlanViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            detail=MealPlanViewModel(coach,NutritionRepository(db.foodItemDao())); models.put("snack",detail)
            detail.loadDailyDetail(meal.templateId!!,MealType.SNACK,meal.kcal,plan.targetKcal)
        }
        compose.waitUntil(20_000) { detail.uiState.value.selectedMeal!=null }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { detail.requestConsumptionConfirmation(); detail.confirmConsumed() }
        compose.waitUntil(20_000) { detail.uiState.value.saved || detail.uiState.value.message!=null }
        assertTrue(detail.uiState.value.message,detail.uiState.value.saved)
        val rows=runBlocking { db.mealRecordDao().getMealsByDate(day).first() }
        assertEquals(1,rows.size); assertEquals(MealType.SNACK,rows.single().mealType)
        assertFalse(runBlocking { coach.ensureDefaultPreference() }.snackEnabled)
    }
    @Test fun smallGoalChangeKeepsSamePlanAndUpdatesDisplayedTarget() {
        val before=runBlocking { requireNotNull(repo.create(DailyRecommendationTheme.DIET)) }
        runBlocking { db.calorieGoalDao().insertGoal(CalorieGoal(targetCalories=1870,startDate=day)) }
        val after=runBlocking { requireNotNull(repo.create(DailyRecommendationTheme.DIET)) }
        assertEquals(before.signature,after.signature); assertEquals(1870,after.targetKcal)
    }
    @Test fun realBundled292DataWithIsolatedUserSettingsReportsDeviceGenerationTimes() = runBlocking {
        val actual=AppDatabase.getDatabase(context)
        val originalRecords=actual.mealRecordDao().getAllMeals().first()
        val readonlyCoach=MealCoachRepository(actual,actual.mealCoachDao(),actual.foodItemDao(),actual.mealRecordDao(),InMemoryRecommendationCycleStore())
        assertEquals(292,actual.mealCoachDao().templateCount())
        val originalPreference=actual.mealCoachDao().getPreference()
        val originalExclusions=actual.mealCoachDao().getExcludedFoods()
        val originalGoals=actual.calorieGoalDao().getAllGoals().first()
        val originalProfiles=actual.energyProfileDao().getAllProfiles().first()
        val seeds=readonlyCoach.dailyPlanSeeds()
        assertEquals(292,seeds.size)
        // Other full-suite fixtures may leave goals, records or restrictive preferences active.
        // Copy real nutrition/catalog data into a separate database, never repair the user's DB.
        val isolated=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val ingredients=seeds.flatMap { actual.mealCoachDao().getIngredients(it.template.id) }
            val foods=ingredients.map { it.foodItemId }.distinct().map { requireNotNull(actual.foodItemDao().findById(it)) }
            isolated.foodItemDao().upsertAll(foods)
            isolated.mealCoachDao().upsertTemplates(seeds.map { it.template })
            isolated.mealCoachDao().upsertIngredients(ingredients)
            val isolatedCoach=MealCoachRepository(isolated,isolated.mealCoachDao(),isolated.foodItemDao(),isolated.mealRecordDao(),InMemoryRecommendationCycleStore())
            isolatedCoach.ensureDefaultPreference()
            isolated.calorieGoalDao().insertGoal(CalorieGoal(targetCalories=1850,startDate=day))
            val live=TodayMealPlanRepository(InMemoryTodayMealPlanStore(),isolatedCoach,GoalRepository(isolated.calorieGoalDao()),
                EnergyProfileRepository(isolated.energyProfileDao()),MealRepository(isolated.mealRecordDao()),FoodRepository(isolated.frequentFoodDao()))
            assertEquals(292,isolated.mealCoachDao().templateCount())
            assertEquals(1850,live.currentTarget())
            for(theme in DailyRecommendationTheme.entries.filterNot { it==DailyRecommendationTheme.SLOW_AGING_STYLE }) {
                val p=requireNotNull(live.create(theme)) { "Real catalog failed for ${theme.name}" }
                assertEquals(1850,p.targetKcal); assertEquals(4,p.meals.size)
                assertEquals(4,p.meals.map { it.templateId }.distinct().size)
                assertEquals(p.meals.sumOf { it.kcal },p.totalKcal)
                android.util.Log.i("DailyPlanQA","${theme.name} ${p.generationMillis}ms ${p.totalKcal}/${p.targetKcal}kcal (isolated goal; real 292 catalog)")
            }
            assertNull(live.create(DailyRecommendationTheme.SLOW_AGING_STYLE))
            assertTrue(isolated.mealRecordDao().getAllMeals().first().isEmpty())
        } finally {
            isolated.close()
        }
        assertEquals(originalRecords,actual.mealRecordDao().getAllMeals().first())
        assertEquals(originalPreference,actual.mealCoachDao().getPreference())
        assertEquals(originalExclusions,actual.mealCoachDao().getExcludedFoods())
        assertEquals(originalGoals,actual.calorieGoalDao().getAllGoals().first())
        assertEquals(originalProfiles,actual.energyProfileDao().getAllProfiles().first())
    }
}
