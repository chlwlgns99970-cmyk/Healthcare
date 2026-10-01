package com.example.healthcare

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.BodyProfileStore
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodySex
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopLevelNavigationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun ensureCompletedProfileForNavigationTest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (BodyProfileStore(context).read() == null) {
            BodyProfileStore(context).save(
                BodyProfile(BodySex.MALE, ageYears = 35, heightCm = 175.0, weightKg = 70.0)
            )
            composeRule.activityRule.scenario.recreate()
        }
    }

    @Test
    fun homeTabReturnsFromNestedPreferenceScreen() {
        assertEquals("com.example.healthcare.qa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)

        composeRule.onNodeWithText("설정").performClick()
        composeRule.onNodeWithText("추천 설정").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("식사 추천 설정"))
        composeRule.onNodeWithText("식사 추천 설정").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("추천에 반영할 취향").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("홈").performClick()
        composeRule.waitUntil(10_000) {
            runCatching { composeRule.onNodeWithTag("dashboard-root").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithTag("dashboard-root").assertIsDisplayed()
    }

    @Test
    fun systemBackReturnsFromSettingsSectionToSettingsHub() {
        composeRule.onNodeWithText("설정").performClick()
        composeRule.onNodeWithText("내 신체정보").performClick()
        composeRule.onNodeWithText("성별, 나이, 키와 몸무게").assertIsDisplayed()

        Espresso.pressBack()

        composeRule.onNodeWithText("앱 설정").assertIsDisplayed()
        composeRule.onNodeWithText("에너지 목표").assertIsDisplayed()
    }

    @Test
    fun homeCalorieTargetOpensTodayReport() {
        composeRule.onNodeWithTag("dashboard-calorie-target")
            .assertIsDisplayed().assertHasClickAction().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("오늘 식사 리포트").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today-report").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithTag("dashboard-root").assertIsDisplayed()
    }

    @Test
    fun recommendationCardWholeSurfaceUsesRecommendationTabAndBackReturnsHome() {
        assertEquals("com.example.healthcare.qa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)

        composeRule.onNodeWithTag("dashboard-open-daily-plan")
            .assertIsDisplayed()
            .assertHasClickAction()

        listOf(0.12f, 0.50f, 0.90f).forEach { horizontalFraction ->
            composeRule.onNodeWithTag("dashboard-recommendation-card").performTouchInput {
                click(Offset(width * horizontalFraction, height / 2f))
            }
            composeRule.waitUntil(10_000) {
                runCatching {
                    composeRule.onAllNodesWithText("추천").fetchSemanticsNodes()
                        .any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Selected) }
                }.getOrDefault(false)
            }
            composeRule.onNodeWithText("추천").assertIsSelected()
            composeRule.waitUntil(10_000) {
                runCatching { composeRule.onNodeWithTag("daily-plan-title").fetchSemanticsNode() }.isSuccess &&
                    runCatching { composeRule.onNodeWithTag("daily-plan-loading").fetchSemanticsNode() }.isFailure
            }
            // Home opens an existing day plan when available. Its first Back returns to themes.
            if (composeRule.onAllNodesWithText("오늘은 어떻게 먹고 싶나요?").fetchSemanticsNodes().isEmpty()) {
                Espresso.pressBack()
            }
            composeRule.onNodeWithText("오늘은 어떻게 먹고 싶나요?").assertIsDisplayed()
            composeRule.onNodeWithTag("daily-theme-LIGHT").assertIsDisplayed().assertHasClickAction()
            composeRule.onNodeWithTag("daily-plan-total").assertDoesNotExist()

            Espresso.pressBack()
            composeRule.waitUntil(10_000) {
                runCatching { composeRule.onNodeWithTag("dashboard-root").fetchSemanticsNode() }.isSuccess
            }
            composeRule.onNodeWithTag("dashboard-root").assertIsDisplayed()
        }

        composeRule.onNodeWithText("추천").performClick()
        composeRule.onNodeWithText("추천").assertIsSelected()
        composeRule.onNodeWithText("오늘은 어떻게 먹고 싶나요?").assertIsDisplayed()
        composeRule.onNodeWithTag("daily-plan-total").assertDoesNotExist()
        composeRule.onNodeWithText("홈").performClick()
        composeRule.onNodeWithTag("dashboard-root").assertIsDisplayed()
    }
}
