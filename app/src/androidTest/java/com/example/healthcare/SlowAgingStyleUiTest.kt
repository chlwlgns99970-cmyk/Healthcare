package com.example.healthcare

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.InMemoryTodayMealPlanStore
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.repository.*
import com.example.healthcare.data.seed.BundledFoodDataSeeder
import com.example.healthcare.domain.*
import com.example.healthcare.ui.screens.TodayRecommendationScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.TodayMealPlanViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

/** G: real source data, production theme selection and generation; user Room/prefs are isolated. */
class SlowAgingStyleUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var coach: MealCoachRepository
    private val models = ViewModelStore()
    private val fixturePrefs = "slow-style-fixture-seed"

    @Before fun isolatedRealBundle() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.healthcare.qa", context.packageName)
        runBlocking { (context.applicationContext as HealthcareApplication).foodMetadataStore.ensureLoaded() }
        context.getSharedPreferences(fixturePrefs, 0).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences(fixturePrefs, mode)
        }
        coach = MealCoachRepository(db, db.mealCoachDao(), db.foodItemDao(), db.mealRecordDao())
        runBlocking {
            assertTrue(BundledFoodDataSeeder(isolated, db).seedIfAvailable() is BundledFoodDataSeeder.SeedResult.Seeded)
            coach.ensureDefaultPreference()
            db.calorieGoalDao().insertGoal(CalorieGoal(targetCalories = 1500, startDate = LocalDate.now().toString()))
        }
    }

    @After fun closeFixture() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() }
        db.close()
        context.getSharedPreferences(fixturePrefs, 0).edit().clear().commit()
    }

    private fun compact(content: @Composable () -> Unit) = compose.setContent {
        val native = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(native.density, 1f)) {
            HealthCareTheme(darkTheme = false, appFontScale = 1.30f) {
                androidx.compose.foundation.layout.Column(Modifier.width(360.dp).height(780.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                    com.example.healthcare.ui.components.FloatingNavigationDock(com.example.healthcare.ui.TopLevelDestination.RECOMMENDATIONS) {}
                }
            }
        }
    }

    private fun scroll(matcher: SemanticsMatcher) = compose.onNodeWithTag("daily-plan-list").performScrollToNode(matcher)

    private fun readable(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue((bounds.right - bounds.left).value <= 360f)
        val layout = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        // Compose rounds measured size to integer px; compare actual line bounds with 1px rounding tolerance.
        assertTrue("Enlarged text: ${layout.map { "text=${it.layoutInput.text.text} size=${it.size} lines=${it.lineCount} maxRight=${(0 until it.lineCount).maxOf(it::getLineRight)} lastBottom=${it.getLineBottom(it.lineCount-1)}" }}",
            layout.isNotEmpty() && layout.all { result ->
                !result.multiParagraph.didExceedMaxLines && result.getLineBottom(result.lineCount - 1) <= result.size.height + 1f &&
                    (0 until result.lineCount).all { line -> !result.isLineEllipsized(line) &&
                        result.getLineLeft(line) >= -1f && result.getLineRight(line) <= result.size.width + 1f }
            })
    }

    @Test fun selectingStyleGeneratesRealFourMealPlanWithFactualExplanationAt360AndAppFont130() {
        val repository = TodayMealPlanRepository(InMemoryTodayMealPlanStore(), coach,
            GoalRepository(db.calorieGoalDao()), EnergyProfileRepository(db.energyProfileDao()),
            MealRepository(db.mealRecordDao()))
        lateinit var vm: TodayMealPlanViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = TodayMealPlanViewModel(repository); models.put("today-style", vm)
        }
        compact { TodayRecommendationScreen(vm, {}, {}, {}, { _, _ -> }) }
        compose.waitUntil(15_000) { vm.uiState.value.target == 1500 }
        com.example.healthcare.ui.TopLevelDestination.entries.forEach { tab ->
            compose.onNodeWithTag("bottom-tab-${tab.name}").assertIsDisplayed()
            readable(compose.onNodeWithText(tab.iconTextId))
        }
        scroll(hasTestTag("daily-theme-SLOW_AGING_STYLE"))
        compose.onNodeWithTag("daily-theme-SLOW_AGING_STYLE").assertIsDisplayed().performClick()
        compose.waitUntil(45_000) { vm.uiState.value.plan != null ||
            (vm.uiState.value.selectedTheme != null && !vm.uiState.value.loading && vm.uiState.value.message != null) }
        assertNull(vm.uiState.value.message)
        val plan = requireNotNull(vm.uiState.value.plan)
        val seeds = runBlocking { coach.dailyPlanSeeds() }
        assertEquals(292, seeds.size)
        assertEquals(DailyRecommendationTheme.SLOW_AGING_STYLE, plan.theme)
        assertEquals(1500, plan.targetKcal)
        assertEquals(DailyMealPlanEngine.slots, plan.meals.map { it.mealType })
        assertEquals(4, plan.meals.map { it.templateId }.distinct().size)
        assertTrue(plan.meals.flatMap { it.foodGroups }.toSet().containsAll(setOf("MIXED_GRAIN", "LEGUME_SOY", "VEGETABLE")))
        scroll(hasText(SlowAgingStylePolicy.DESCRIPTION))
        readable(compose.onNodeWithText(SlowAgingStylePolicy.DESCRIPTION))
        scroll(hasText(SlowAgingStylePolicy.VARIATION_NOTE))
        readable(compose.onNodeWithText(SlowAgingStylePolicy.VARIATION_NOTE))
        plan.meals.forEach { meal ->
            val seed = seeds.single { it.template.id == meal.templateId }
            assertTrue(SlowAgingStylePolicy.eligible(seed))
            assertNotNull(RecommendationServingPolicy.selected(seed, meal.mealType, meal.portion))
            scroll(hasTestTag("daily-name-${meal.mealType.name}"))
            readable(compose.onNodeWithTag("daily-name-${meal.mealType.name}"))
            scroll(hasTestTag("daily-amount-${meal.mealType.name}"))
            readable(compose.onNodeWithTag("daily-amount-${meal.mealType.name}"))
            assertTrue(meal.amountLabels.isNotEmpty())
        }
        val signatures = mutableSetOf(plan.signature)
        val snackIds = mutableSetOf(requireNotNull(plan.meals.last().templateId))
        val mealHistory = plan.meals.associate { it.mealType to mutableSetOf(requireNotNull(it.templateId)) }
        repeat(4) { index ->
            val previous = requireNotNull(vm.uiState.value.plan).signature
            scroll(hasTestTag("daily-plan-alternate"))
            compose.onNodeWithTag("daily-plan-alternate").assertIsDisplayed().performClick()
            compose.waitUntil(45_000) { vm.uiState.value.plan?.signature != previous ||
                (!vm.uiState.value.loading && vm.uiState.value.message != null) }
            assertNull(vm.uiState.value.message)
            val next = requireNotNull(vm.uiState.value.plan)
            assertTrue(signatures.add(next.signature))
            assertEquals(1500, next.targetKcal)
            assertTrue("Target ±5%: ${next.totalKcal}", kotlin.math.abs(next.totalKcal - 1500) <= 75)
            assertEquals(4, next.meals.map { it.templateId }.distinct().size)
            assertTrue(next.meals.last().kcal <= 225)
            assertTrue(snackIds.add(requireNotNull(next.meals.last().templateId)))
            next.meals.forEach { meal ->
                assertTrue(mealHistory.getValue(meal.mealType).add(requireNotNull(meal.templateId)))
                val seed = seeds.single { it.template.id == meal.templateId }
                assertTrue(SlowAgingStylePolicy.eligible(seed))
                val selected = requireNotNull(RecommendationServingPolicy.selected(seed, meal.mealType, meal.portion))
                assertEquals(selected.kcal, meal.kcal)
                assertEquals(selected.labels, meal.amountLabels)
                scroll(hasTestTag("daily-name-${meal.mealType.name}"))
                readable(compose.onNodeWithTag("daily-name-${meal.mealType.name}"))
                scroll(hasTestTag("daily-amount-${meal.mealType.name}"))
                readable(compose.onNodeWithTag("daily-amount-${meal.mealType.name}"))
            }
            println("STYLE_ACTUAL_ALTERNATE ${index + 1} ${next.totalKcal}/${next.targetKcal} kcal " +
                next.meals.joinToString { "${it.mealType}:${it.name}:${it.amountLabels.joinToString()}:${it.kcal}kcal" })
        }
        assertEquals(5, signatures.size)
        assertEquals(5, snackIds.size)
        assertTrue(runBlocking { db.mealRecordDao().getAllMeals().first().isEmpty() })
        assertEquals(1500, runBlocking { db.calorieGoalDao().getAllGoals().first().single().targetCalories })
        println("STYLE_ACTUAL_PLAN ${plan.totalKcal}/${plan.targetKcal} kcal " + plan.meals.joinToString {
            "${it.mealType}:${it.name}:${it.amountLabels.joinToString()}:${it.kcal}kcal" })
    }
}
