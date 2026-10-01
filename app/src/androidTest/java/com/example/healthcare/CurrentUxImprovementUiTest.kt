package com.example.healthcare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.healthcare.data.InMemoryRecommendationCycleStore
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.entity.UserExcludedFood
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.domain.DashboardSummaryPolicy
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.domain.RecommendationStage
import com.example.healthcare.ui.screens.DashboardContent
import com.example.healthcare.ui.screens.MealPreferenceContent
import com.example.healthcare.ui.screens.TodayReportContent
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.DashboardEnergyUiState
import com.example.healthcare.ui.viewmodel.MealPreferenceUiState
import com.example.healthcare.ui.viewmodel.TodayCoachUiState
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrentUxImprovementUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun officialBasicFoodsAndCanonicalTunaGimbapGroupAreVisible() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        assertEquals("com.example.healthcare.qa", context.packageName)
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT) delay(100)
        }
        val repository = NutritionRepository(database.foodItemDao())

        listOf("두부", "달걀", "계란", "삶은 달걀", "삶은 계란").forEach { query ->
            val results = repository.search(query).first()
            assertTrue("$query 기본 음식이 검색되지 않았습니다.", results.isNotEmpty())
            assertTrue("$query 검색에서 일반 음식이 제품에 묻혔습니다.", !FoodSearchPolicy.isProduct(results.first()))
        }
        val eggIds = repository.search("계란").first().map { it.id }.toSet()
        val koreanEggIds = repository.search("달걀").first().map { it.id }.toSet()
        assertTrue(eggIds.intersect(koreanEggIds).isNotEmpty())

        val tunaRows = repository.search("참치김밥").first()
        val groups = FoodSearchPolicy.groupSearchResults(tunaRows, "참치김밥")
        val tunaGroup = groups.first { it.key == FoodSearchPolicy.normalize("참치김밥") }
        assertEquals("참치김밥", FoodSearchPolicy.displayName(tunaGroup.representative))
        assertTrue(tunaGroup.alternatives.isNotEmpty())
        assertEquals(tunaRows.size, groups.sumOf { it.size })
        assertEquals(
            tunaGroup.representative.id,
            FoodSearchPolicy.groupSearchResults(tunaRows.reversed(), "참치 김밥")
                .first { it.key == FoodSearchPolicy.normalize("참치김밥") }.representative.id
        )
    }

    @Test
    fun dashboardAt360AndLargeTextShowsOrderedMealsReportAndDailyPlanSummary() {
        val today = LocalDate.now()
        val meals = listOf(
            record(1, MealType.BREAKFAST, "바나나", 93),
            record(2, MealType.LUNCH, "순두부찌개", 560),
            record(3, MealType.LUNCH, "현미밥", 160),
            record(4, MealType.SNACK, "두유", 120)
        )
        var reportOpened = false
        var recommendationOpenCount = 0
        var selectedRecommendation: String? = "unopened"
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        DashboardContent(
                            selectedDate = today,
                            meals = meals,
                            totalCalories = 933,
                            targetCalories = 2_000,
                            statusText = "목표까지 1,067kcal",
                            energyState = DashboardEnergyUiState(intakeCalories = 933),
                            coachState = TodayCoachUiState(
                                dailyThemeLabel = "균형 있게",
                                dailyPlanKcal = 1_980,
                                dailyPlanTarget = 2_000
                            ),
                            situationMessage = "오늘 목표까지 약 1,067 kcal 남았어요.",
                            nutrition = Macronutrients(122.0, 58.0, 31.0),
                            onPreviousDay = {},
                            onNextDay = {},
                            onAddRecord = {},
                            onOpenEnergySettings = {},
                            onOpenRecommendations = {
                                recommendationOpenCount++
                                selectedRecommendation = it
                            },
                            onOpenHistory = { reportOpened = true }
                        )
                    }
                }
            }
        }

        val orderedTags = listOf("breakfast", "lunch", "dinner", "snack")
        val tops = orderedTags.map {
            composeRule.onNodeWithTag("dashboard-meal-$it").fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
        composeRule.onNodeWithText("바나나 · 93 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("순두부찌개 외 1개 · 총 720 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("기록 없음").assertIsDisplayed()
        composeRule.onNodeWithText("두유 · 120 kcal").assertIsDisplayed()
        assertFalse(
            composeRule.onNodeWithTag("dashboard-root").fetchSemanticsNode().config
                .contains(SemanticsActions.ScrollBy)
        )

        composeRule.onNodeWithTag("dashboard-calorie-target").performClick()
        composeRule.runOnIdle { assertTrue(reportOpened) }
        listOf("오늘의 추천", "균형 있게 식단", "1,980 / 2,000 kcal", "오늘 식단 보기").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        val home = composeRule.onNodeWithTag("dashboard-root").getUnclippedBoundsInRoot()
        val intake = composeRule.onNodeWithTag("dashboard-calorie-target").getUnclippedBoundsInRoot()
        val summary = composeRule.onNodeWithTag("dashboard-recommendation-card").getUnclippedBoundsInRoot()
        val summaryAction = composeRule.onNodeWithTag("dashboard-open-daily-plan").getUnclippedBoundsInRoot()
        assertTrue(intake.bottom <= summary.top)
        assertTrue(summary.bottom <= home.bottom)
        assertTrue(summaryAction.top >= summary.top && summaryAction.bottom <= summary.bottom)
        assertTrue(summaryAction.bottom - summaryAction.top >= 48.dp)
        composeRule.onNodeWithTag("dashboard-open-daily-plan").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, recommendationOpenCount)
            assertEquals(null, selectedRecommendation)
        }
    }

    @Test
    fun todayReportRemainsUsableAt360AndLargeText() {
        val meals = listOf(
            record(1, MealType.LUNCH, "참치김밥", 420),
            record(2, MealType.SNACK, "바나나", 93)
        )
        val report = DashboardSummaryPolicy.report(meals, 2_000, Macronutrients(91.0, 24.0, null))
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        TodayReportContent(LocalDate.now(), report, onBack = {}, onAddRecord = {})
                    }
                }
            }
        }
        composeRule.onNodeWithTag("today-report").assertIsDisplayed()
        composeRule.onNodeWithText("513 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("확인 가능한 기록 기준으로 계산했어요.").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("참치김밥"))
        composeRule.onNodeWithText("참치김밥").assertIsDisplayed()

    }

    @Test
    fun dislikePreferenceRemainsUsableAt360AndLargeText() {
        var removed = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        MealPreferenceContent(
                            state = MealPreferenceUiState(
                                excludedFoods = listOf(UserExcludedFood(1, "오이", "DISLIKE", 0))
                            ),
                            onBack = {}, onMealEnabled = { _, _ -> }, onRatioChange = { _, _ -> },
                            onDietType = {}, onCookingMode = {}, onBudget = {}, onDiversity = {},
                            onAllergyInput = {}, onAddAllergy = {}, onDislikeInput = {}, onAddDislike = {},
                            onPreferredInput = {}, onRemoveExcluded = { removed = true }, onSave = {}
                        )
                    }
                }
            }
        }
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("피하고 싶은 음식·재료"))
        composeRule.onNodeWithText("피하고 싶은 음식·재료").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("오이"))
        composeRule.onNodeWithText("오이").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(removed) }
    }

    @Test
    fun dislikeReplacesOnlyIneligibleStableRecommendationAndKeepsAllergyAsWarning() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT ||
                database.mealCoachDao().templateCount() < EXPECTED_TEMPLATE_COUNT) delay(100)
        }
        val store = InMemoryRecommendationCycleStore()
        val repository = MealCoachRepository(
            database, database.mealCoachDao(), database.foodItemDao(), database.mealRecordDao(), store
        )
        database.mealCoachDao().getExcludedFoods()
            .filter { it.normalizedFoodName == "오이" && it.exclusionType == "DISLIKE" }
            .forEach { repository.deleteExcludedFood(it) }

        val candidates = repository.searchRecommendations(
            MealType.LUNCH, 600, RecommendationStage.CLOSEST_VERIFIED, limit = EXPECTED_TEMPLATE_COUNT
        ).recommendations
        val cucumber = candidates.first { bundle ->
            bundle.ingredientNames.any { FoodSearchPolicy.normalize(it).contains("오이") } ||
                FoodSearchPolicy.normalize(bundle.recommendation.template.name).contains("오이")
        }
        val unaffected = candidates.filterNot { it.recommendation.template.id == cucumber.recommendation.template.id }
            .take(2)
        val date = LocalDate.of(2099, 9, 30)
        val scope = "HOME_${date}_LUNCH"
        val originalIds = listOf(cucumber.recommendation.template.id) +
            unaffected.map { it.recommendation.template.id }
        store.writeStableSelection(scope, originalIds)

        assertTrue(repository.addExcludedFood("  오이  ", "DISLIKE"))
        assertFalse(repository.addExcludedFood("오이", "DISLIKE"))
        val refreshed = repository.getOrCreateTodayRecommendations(date, MealType.LUNCH, 600, limit = 3)
        val refreshedIds = refreshed.recommendations.map { it.recommendation.template.id }
        assertFalse(cucumber.recommendation.template.id in refreshedIds)
        assertTrue(unaffected.all { it.recommendation.template.id in refreshedIds })
        assertTrue(refreshed.recommendations.all { bundle ->
            (bundle.ingredientNames + bundle.recommendation.template.name).none {
                FoodSearchPolicy.normalize(it).contains("오이")
            }
        })
        assertEquals(refreshedIds, repository.getOrCreateTodayRecommendations(date, MealType.LUNCH, 600, 3)
            .recommendations.map { it.recommendation.template.id })

        repository.deleteExcludedFood(
            database.mealCoachDao().getExcludedFoods().first {
                it.normalizedFoodName == "오이" && it.exclusionType == "DISLIKE"
            }
        )
    }

    private fun record(id: Long, type: MealType, name: String, calories: Int) = MealRecord(
        id = id,
        date = LocalDate.now().toString(),
        time = "12:00",
        mealType = type,
        foodName = name,
        calories = calories
    )

    private companion object {
        const val EXPECTED_TOTAL_FOOD_COUNT = 31_582
        const val EXPECTED_TEMPLATE_COUNT = 292
    }
}
