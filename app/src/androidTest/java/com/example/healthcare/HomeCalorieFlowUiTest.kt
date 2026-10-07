package com.example.healthcare

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.ui.TopLevelDestination
import com.example.healthcare.ui.components.FloatingNavigationDock
import com.example.healthcare.ui.screens.DashboardContent
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.DashboardEnergyUiState
import com.example.healthcare.ui.viewmodel.TodayCoachUiState
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the home flow with the real navigation dock and the device's window insets. */
class HomeCalorieFlowUiTest {
    @get:Rule val compose = createComposeRule()

    private data class CalorieCase(val intake: Int, val label: String)

    private data class Fixture(
        val height: Dp = 800.dp,
        val systemFontScale: Float = 1.3f,
        val hasRecommendation: Boolean = true,
        val calories: CalorieCase = calorieCases.first(),
        val appFontScale: Float = 1.30f
    )

    @Test
    fun remainingAndExceededKeepTheirFullTextAt360DpWithLargeFontsAndBothRecommendationStates() {
        verifyFixtures(buildList {
            for (height in listOf(800.dp, 740.dp)) {
                for (systemFontScale in listOf(1f, 1.3f)) {
                    for (hasRecommendation in listOf(false, true)) {
                        for (calories in calorieCases) {
                            add(Fixture(height, systemFontScale, hasRecommendation, calories))
                        }
                    }
                }
            }
        })
    }

    @Test
    fun requestedAmountsFitAt360DpWithRecommendationAndDefaultOrLargeFonts() {
        val requested = listOf(
            CalorieCase(1_991, "남은 9 kcal"),
            CalorieCase(1_530, "남은 470 kcal"),
            CalorieCase(800, "남은 1,200 kcal"),
            CalorieCase(2_080, "80 kcal 초과"),
            CalorieCase(3_000, "1,000 kcal 초과")
        )
        verifyFixtures(requested.map { Fixture(height = 740.dp, calories = it) } + listOf(
            Fixture(height = 740.dp, systemFontScale = 1f, calories = requested[2], appFontScale = 1f),
            Fixture(height = 740.dp, systemFontScale = 1f, calories = requested[2]),
            Fixture(height = 740.dp, calories = requested[4], appFontScale = 1f)
        ))
    }

    private fun verifyFixtures(fixtures: List<Fixture>) {
        var fixture by mutableStateOf(fixtures.first())
        compose.setContent {
            val nativeDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(nativeDensity, fixture.systemFontScale)) {
                HealthCareTheme(darkTheme = false, appFontScale = fixture.appFontScale) {
                    assertEquals(fixture.systemFontScale * fixture.appFontScale, LocalDensity.current.fontScale, 0.001f)
                    Box(Modifier.width(360.dp).height(fixture.height).testTag("home-flow-viewport")) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            bottomBar = { FloatingNavigationDock(TopLevelDestination.DASHBOARD) {} }
                        ) { dockPadding ->
                            Box(Modifier.fillMaxSize().padding(dockPadding)) {
                                DashboardContent(
                                    selectedDate = LocalDate.now(),
                                    meals = emptyList(),
                                    totalCalories = fixture.calories.intake,
                                    targetCalories = 2_000,
                                    statusText = fixture.calories.label,
                                    energyState = DashboardEnergyUiState(),
                                    nutrition = Macronutrients(103.0, 35.0, 27.0),
                                    coachState = if (fixture.hasRecommendation) {
                                        TodayCoachUiState(
                                            dailyThemeLabel = "저속노화식",
                                            dailyPlanKcal = 1_975,
                                            dailyPlanTarget = 2_000
                                        )
                                    } else TodayCoachUiState(),
                                    onPreviousDay = {},
                                    onNextDay = {},
                                    onAddRecord = {},
                                    onOpenEnergySettings = {},
                                    onOpenRecommendations = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        for (case in fixtures) {
            compose.runOnIdle { fixture = case }
            compose.waitForIdle()
            try { assertHomeFlow(fixture) } catch (failure: AssertionError) {
                capture("home-calorie-flow-failure.png")
                throw failure
            }
            if (fixture.height == 740.dp && fixture.systemFontScale == 1.3f) {
                capture("home-calorie-flow-360-740-${if (fixture.hasRecommendation) "plan" else "empty"}.png")
            }
        }
    }

    private fun assertHomeFlow(fixture: Fixture) {
        val context = "height=${fixture.height}, system=${fixture.systemFontScale}, " +
            "app=${fixture.appFontScale}, recommendation=${fixture.hasRecommendation}, ${fixture.calories.label}"
        compose.onNodeWithTag("dashboard-calorie-remaining", useUnmergedTree = true)
            .assertTextEquals(fixture.calories.label).assertIsDisplayed()
        assertTextFits(fixture.calories.label, context)

        assertTrue("Home must stay non-scroll: $context", compose.onAllNodes(
            hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ).fetchSemanticsNodes().isEmpty())

        val viewport = bounds("home-flow-viewport")
        val home = bounds("dashboard-root")
        val intake = bounds("dashboard-calorie-target")
        val chart = bounds("dashboard-calorie-chart")
        val remaining = bounds("dashboard-calorie-remaining")
        val macros = bounds("dashboard-macros")
        val recommendation = bounds("dashboard-recommendation-card")
        listOf(home, intake, chart, remaining, macros, recommendation).forEach {
            assertInside(it, viewport, context)
        }
        assertInside(chart, intake, context)
        assertInside(remaining, intake, context)
        assertInside(macros, intake, context)
        assertTrue("Chart must stay round: $context / $chart", kotlin.math.abs((chart.right - chart.left).value - (chart.bottom - chart.top).value) <= 1f)
        assertTrue("Chart/remaining order: $context", chart.bottom <= remaining.top)
        assertTrue("Remaining overlaps macros: $context", remaining.right <= macros.left)
        assertTrue("Remaining overlaps recommendation: $context", remaining.bottom <= recommendation.top)
        assertTrue("Intake overlaps recommendation: $context", intake.bottom <= recommendation.top)

        listOf("breakfast", "snack", "lunch", "dinner").forEach { meal ->
            compose.onNodeWithTag("dashboard-meal-$meal").assertIsDisplayed()
            val mealBounds = bounds("dashboard-meal-$meal")
            assertInside(mealBounds, home, context)
            assertTrue("Meal overlaps intake: $context / $meal", mealBounds.bottom <= intake.top)
        }
        listOf("탄수화물", "단백질", "지방", "103g", "35g", "27g").forEach { text ->
            compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            val macroText = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertInside(macroText, macros, context)
            assertTextFits(text, context)
        }
        val recommendationTexts = if (fixture.hasRecommendation) {
            listOf("저속노화식 식단", "1,975 / 2,000 kcal", "오늘 식단 보기")
        } else listOf("오늘 식사 스타일을 골라보세요", "추천 고르기")
        recommendationTexts.forEach { text ->
            compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            assertTextFits(text, context)
            assertInside(compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot(), recommendation, context)
        }
    }

    private fun bounds(tag: String): DpRect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertInside(child: DpRect, parent: DpRect, context: String) {
        assertTrue("Content outside its parent: $context / $child / $parent",
            child.left >= parent.left - 1.dp && child.top >= parent.top - 1.dp &&
                child.right <= parent.right + 1.dp && child.bottom <= parent.bottom + 1.dp)
    }

    private fun assertTextFits(text: String, context: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Missing text layout: $text / $context", layouts.isNotEmpty())
        layouts.forEach { layout ->
            // Android's paragraph metrics use fractional pixels while Compose reports an integer size.
            // Check the actual line extents with the same one-pixel rounding tolerance as height.
            assertFalse("Text height overflows: $text / $context / size=${layout.size}, intake=${bounds("dashboard-calorie-target")}", layout.didOverflowHeight)
            repeat(layout.lineCount) { line ->
                assertFalse("Text is ellipsized: $text / $context", layout.isLineEllipsized(line))
                assertTrue("Line width is clipped: $text / $context / right=${layout.getLineRight(line)}, size=${layout.size}",
                    layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f)
                assertTrue("Line height is clipped: $text / $context",
                    layout.getLineTop(line) >= -1f && layout.getLineBottom(line) <= layout.size.height + 1f)
            }
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = File(requireNotNull(context.getExternalFilesDir(null)), name)
        compose.onNodeWithTag("home-flow-viewport").captureToImage().asAndroidBitmap().let { bitmap ->
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private companion object {
        val calorieCases = listOf(
            CalorieCase(1_625, "남은 375 kcal"),
            CalorieCase(625, "남은 1,375 kcal"),
            CalorieCase(2_375, "375 kcal 초과"),
            CalorieCase(3_375, "1,375 kcal 초과")
        )
    }
}
