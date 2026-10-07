package com.example.healthcare

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.example.healthcare.ui.screens.MealPreferenceScreen
import com.example.healthcare.ui.screens.RecordPeriodSummaryCard
import com.example.healthcare.ui.screens.TodayRecommendationContent
import com.example.healthcare.ui.screens.TodayReportContent
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.MealPreferenceViewModel
import com.example.healthcare.ui.viewmodel.TodayMealPlanUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

/** Only isolated Room rows and a dedicated fixture preferences file are changed. */
@RunWith(AndroidJUnit4::class)
class LearningAndRecordGuidanceUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var isolatedContext: Context
    private lateinit var db: AppDatabase
    private lateinit var coach: MealCoachRepository
    private lateinit var learning: RecommendationLearningStore
    private val models = ViewModelStore()
    private val day get() = LocalDate.now().toString()
    private val prefs = "learning-guidance-fixture-recommendation_cycle_v2"

    @Before fun isolatedFixture() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.healthcare.qa", context.packageName)
        context.getSharedPreferences(prefs, 0).edit().clear().commit()
        isolatedContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefs, mode)
        }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        learning = RecommendationLearningStore(SharedPreferencesRecommendationLearningPersistence(isolatedContext))
        coach = MealCoachRepository(db, db.mealCoachDao(), db.foodItemDao(), db.mealRecordDao(),
            InMemoryRecommendationCycleStore(), learning)
        runBlocking {
            coach.ensureDefaultPreference()
            db.calorieGoalDao().insertGoal(CalorieGoal(targetCalories = 1800, startDate = day))
            val templates = mutableListOf<MealTemplate>()
            val ingredients = mutableListOf<MealTemplateIngredient>()
            val specifications = listOf(Triple("a", MealType.LUNCH, 500), Triple("b", MealType.LUNCH, 500),
                Triple("c", MealType.LUNCH, 500), Triple("breakfast", MealType.BREAKFAST, 450),
                Triple("dinner", MealType.DINNER, 500), Triple("snack", MealType.SNACK, 180))
            specifications.forEach { (id, meal, kcal) ->
                val name = "검증 메뉴 $id"
                db.foodItemDao().upsertAll(listOf(FoodItem("food-$id", "QA", id, name,
                    // Synthetic measured fixtures have no verified rice serving identity.
                    MealRecommendationEngine.normalizeFoodName(name), category = "QA 검증 메뉴", referenceAmount = 100.0,
                    unit = "g", energyKcal = kcal.toDouble(), carbohydrateGrams = 65.0, proteinGrams = 20.0,
                    fatGrams = 10.0, servingDescription = "100g", dataVersion = "QA", createdAt = 0, updatedAt = 0)))
                templates += MealTemplate(id, name, "|${meal.name}|", kcal, 20.0, 65.0, 10.0, 10,
                    "LOW", "|COOK|INGREDIENTS_COMPLETE|", "", "", "KOREAN", "QA verified", 0, 0)
                ingredients += MealTemplateIngredient(mealTemplateId = id, foodItemId = "food-$id", amount = 100.0,
                    unit = "g", adjustable = true, minimumAmount = 50.0, maximumAmount = 150.0, adjustmentStep = 10.0)
            }
            db.mealCoachDao().upsertTemplates(templates)
            db.mealCoachDao().upsertIngredients(ingredients)
        }
    }
    @After fun closeFixture() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() }
        db.close()
        context.getSharedPreferences(prefs, 0).edit().clear().commit()
    }
    private fun compact(content: @Composable () -> Unit) = compose.setContent {
        val native = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(native.density, 1f)) {
            HealthCareTheme(darkTheme = false, appFontScale = 1.30f) {
                Box(Modifier.width(360.dp).height(760.dp).testTag("guidance-viewport")) { content() }
            }
        }
    }
    private fun record(id: Long, meal: MealType, kcal: Int, name: String = "기록 $id", daysAgo: Long = 0) =
        MealRecord(id = id, date = LocalDate.now().minusDays(daysAgo).toString(), time = "12:00", mealType = meal,
            foodName = name, calories = kcal, carbohydrateGrams = 60.0, proteinGrams = 20.0, fatGrams = 10.0)

    @Test fun explicitReplacementIsWeakPersistedAndConfirmedResetPreservesAllRealFixtureData() {
        val seeds = runBlocking { coach.dailyPlanSeeds() }
        val first = runBlocking { coach.searchRecommendations(MealType.LUNCH, 500, RecommendationStage.EXACT, limit = 1) }
        assertEquals("a", first.recommendations.single().recommendation.template.id)
        coach.recordRecommendationReplacement("a")
        val rejected = runBlocking { coach.learnedScores(seeds) }
        assertEquals(-0.75, rejected.getValue("a"), 0.001)
        assertEquals(-0.75, RecommendationLearningPolicy.scores(seeds, emptyList(), emptyList(),
            RecommendationLearningStore(SharedPreferencesRecommendationLearningPersistence(isolatedContext)).snapshot(),
            System.currentTimeMillis()).getValue("a"), 0.001)
        val other = runBlocking { coach.searchRecommendations(MealType.LUNCH, 500, RecommendationStage.EXACT, limit = 1) }
        assertNotEquals("a", other.recommendations.single().recommendation.template.id)
        val before = runBlocking {
            coach.savePreference(coach.ensureDefaultPreference().copy(preferredFoods = "|검증 메뉴 a|"))
            coach.addExcludedFood("돼지고기", "DISLIKE"); coach.addExcludedFood("땅콩", "ALLERGY")
            db.mealRecordDao().insertMeal(record(41, MealType.LUNCH, 500, "검증 메뉴 a"))
            db.frequentFoodDao().insertFood(FrequentFood(id = 9, foodName = "검증 메뉴 a", defaultServing = "100g",
                calories = 500, isFavorite = true, foodItemId = "food-a"))
            listOf(coach.ensureDefaultPreference(), coach.excludedFoods.first(), db.mealRecordDao().getAllMeals().first(),
                db.frequentFoodDao().getAllFoods().first(), db.calorieGoalDao().getAllGoals().first())
        }
        assertTrue(runBlocking { coach.learnedScores(seeds).getValue("a") } > 0)
        lateinit var vm: MealPreferenceViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = MealPreferenceViewModel(coach); models.put("preference", vm)
        }
        compact { MealPreferenceScreen(vm, {}) }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("recommendation-learning-reset"))
        compose.onNodeWithTag("recommendation-learning-reset").performClick()
        compose.onNodeWithText("추천 학습을 초기화할까요?").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        assertNull(learning.snapshot().resetAt)
        compose.onNodeWithTag("recommendation-learning-reset").performClick()
        compose.onNodeWithTag("learning-reset-confirm").performClick()
        compose.waitUntil(10_000) { learning.snapshot().resetAt != null && !vm.uiState.value.isResettingLearning }
        assertTrue(runBlocking { coach.learnedScores(seeds) }.values.all { it == 0.0 })
        val after = runBlocking { listOf(coach.ensureDefaultPreference(), coach.excludedFoods.first(),
            db.mealRecordDao().getAllMeals().first(), db.frequentFoodDao().getAllFoods().first(), db.calorieGoalDao().getAllGoals().first()) }
        assertEquals(before, after)
    }

    @Test fun nextDinnerGuidanceCarriesMealAndActualRemainingBudgetIntoExistingDayPlanAt360Dp() {
        val rows = listOf(record(1, MealType.BREAKFAST, 450), record(2, MealType.LUNCH, 670))
        runBlocking { rows.forEach { db.mealRecordDao().insertMeal(it) } }
        val repo = TodayMealPlanRepository(InMemoryTodayMealPlanStore(), coach, GoalRepository(db.calorieGoalDao()),
            EnergyProfileRepository(db.energyProfileDao()), MealRepository(db.mealRecordDao()))
        val plan = requireNotNull(runBlocking { repo.create(DailyRecommendationTheme.BALANCED) })
        assertEquals(1120, plan.recordedKcal); assertEquals(680, plan.remainingBudgetKcal)
        assertEquals(1800, plan.totalKcal)
        assertTrue(plan.meals.first { it.mealType == MealType.BREAKFAST }.recorded)
        assertTrue(plan.meals.first { it.mealType == MealType.LUNCH }.recorded)
        val next = RecordInsights.nextMeal(rows, 1800)
        val report = DashboardSummaryPolicy.report(rows, 1800, Macronutrients.knownSum(rows.map {
            Macronutrients(it.carbohydrateGrams, it.proteinGrams, it.fatGrams) }))
        var selectedMeal by mutableStateOf<MealType?>(null)
        var openedMeal: MealType? = null
        compact {
            if (selectedMeal == null) TodayReportContent(LocalDate.now(), report, {}, {}, next,
                onNextMealRecommendation = { selectedMeal = it })
            else TodayRecommendationContent(TodayMealPlanUiState(DailyRecommendationTheme.BALANCED, plan, 1800),
                {}, {}, {}, {}, {}, {}, {}, { meal, target -> openedMeal = meal.mealType; assertEquals(1800, target) },
                focusedMeal = selectedMeal)
        }
        compose.onNodeWithText("1,120 kcal").assertIsDisplayed()
        compose.onNodeWithText("680 kcal").assertIsDisplayed()
        compose.onNodeWithTag("today-report").performScrollToNode(hasTestTag("next-meal-recommendation"))
        compose.onNodeWithTag("next-meal-guidance-message").assertTextEquals("저녁은 약 377~529 kcal 범위로 고르면 오늘 목표에 가까워요.")
        compose.onNodeWithTag("next-meal-recommendation").performClick()
        assertEquals(MealType.DINNER, selectedMeal)
        compose.waitForIdle()
        compose.onNodeWithTag("daily-focused-DINNER").assertIsDisplayed()
        compose.onNodeWithTag("daily-detail-DINNER").performClick()
        assertEquals(MealType.DINNER, openedMeal)
        assertEquals(rows, runBlocking { db.mealRecordDao().getMealsByDate(day).first() })
    }

    @Test fun sevenAndThirtyDayCardsKeepMissingDatesOutOfAverageAt360DpAndLargeText() {
        val rows = (0L..4L).map { record(it + 1, MealType.LUNCH, 1500, daysAgo = it) } +
            record(10, MealType.BREAKFAST, 400) + record(11, MealType.LUNCH, 1600, daysAgo = 20)
        var period by mutableIntStateOf(7)
        compact {
            Column {
                TextButton(onClick = { period = 30 }, modifier = Modifier.testTag("summary-select-30")) { Text("30일") }
                RecordPeriodSummaryCard(RecordInsights.period(rows, LocalDate.now(), period))
            }
        }
        compose.onNodeWithText("최근 7일 중 5일 기록했어요.").assertIsDisplayed()
        compose.onNodeWithText("기록한 날의 평균 섭취량은 약 1580 kcal예요.").assertIsDisplayed()
        compose.onNodeWithText("점심 기록이 가장 많아요.").assertIsDisplayed()
        compose.onNodeWithTag("summary-select-30").performClick()
        compose.onNodeWithText("최근 30일 중 6일 기록했어요.").assertIsDisplayed()
        compose.onNodeWithText("기록한 날의 평균 섭취량은 약 1583 kcal예요.").assertIsDisplayed()
        assertTrue(runBlocking { db.mealRecordDao().getAllMeals().first() }.isEmpty())
    }
}
