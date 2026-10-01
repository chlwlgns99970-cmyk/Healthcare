package com.example.healthcare

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.AppFontSize
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodySex
import com.example.healthcare.domain.StepCounterStatus
import com.example.healthcare.domain.StepCounterUiState
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.ui.screens.BodyProfileSetupScreen
import com.example.healthcare.ui.screens.DashboardContent
import com.example.healthcare.ui.screens.SettingsMenuContent
import com.example.healthcare.ui.screens.SettingsSection
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.BodyProfileUiState
import com.example.healthcare.ui.viewmodel.DashboardEnergyUiState
import com.example.healthcare.ui.viewmodel.EnergySettingsUiState
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingMenuUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun firstSetupShowsAllRequiredFieldsAt360DpAndLargeText() {
        var saveCount = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme(darkTheme = false, appFontScale = 1.3f) {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        BodyProfileSetupScreen(
                            state = BodyProfileUiState(
                                sex = BodySex.FEMALE,
                                ageInput = "35",
                                heightInput = "165.5",
                                weightInput = "58.2",
                                estimatedBmrKcal = 1_302
                            ),
                            onSexSelected = {}, onAgeChange = {}, onHeightChange = {}, onWeightChange = {},
                            onSave = { saveCount++ }
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("건강한\n변화를 함께\n시작해요").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-fixed-hero").assertIsDisplayed()
        capture("onboarding-sharp-native.png")
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("시작하기"))
        composeRule.onNodeWithText("시작하기").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, saveCount) }
        capture("onboarding-360-large.png")
    }

    @Test fun onboardingRendersAtSamsungNativeDensityWithoutRasterizedCopy() {
        composeRule.setContent {
            HealthCareTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    BodyProfileSetupScreen(
                        state = BodyProfileUiState(
                            sex = BodySex.FEMALE,
                            ageInput = "35",
                            heightInput = "165.5",
                            weightInput = "58.2",
                            estimatedBmrKcal = 1_302
                        ),
                        onSexSelected = {}, onAgeChange = {}, onHeightChange = {}, onWeightChange = {},
                        onSave = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText("건강한\n변화를 함께\n시작해요").assertIsDisplayed()
        capture("onboarding-samsung-native.png")
    }

    @Test fun settingsHubOffersDirectSectionsWithoutLongForm() {
        var selected: SettingsSection? = null
        composeRule.setContent {
            HealthCareTheme(darkTheme = false) {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    SettingsMenuContent(
                        bodyProfileState = BodyProfileUiState(
                            heightInput = "175", weightInput = "70.4", estimatedBmrKcal = 1_632
                        ),
                        energyState = EnergySettingsUiState(maintenancePreviewKcal = 2_300),
                        targetCalories = 2_000,
                        appFontSize = AppFontSize.NORMAL,
                        onSectionSelected = { selected = it }
                    )
                }
            }
        }
        composeRule.onNodeWithText("내 신체정보").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(SettingsSection.BODY, selected) }
    }

    @Test fun homeCoreIntakeStepsEnergyAndRecordActionFitAt360Dp() {
        var recommendationOpenCount = 0
        var recordOpenCount = 0
        composeRule.setContent {
            HealthCareTheme(darkTheme = false) {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    DashboardContent(
                        selectedDate = LocalDate.now(),
                        meals = emptyList(),
                        totalCalories = 1_250,
                        targetCalories = 1_900,
                        statusText = "목표까지 650kcal",
                        energyState = DashboardEnergyUiState(),
                        onPreviousDay = {}, onNextDay = {}, onAddRecord = { recordOpenCount++ }, onOpenEnergySettings = {},
                        onOpenRecommendations = { recommendationOpenCount++ },
                        bodyProfile = BodyProfile(BodySex.MALE, 35, 175.0, 70.4),
                        nutrition = Macronutrients(120.0, 65.0, 38.0),
                        stepCounterState = StepCounterUiState(StepCounterStatus.AVAILABLE, 6_428)
                    )
                }
            }
        }
        composeRule.onNodeWithText("오늘 첫 식사를 기록해보세요.").assertIsDisplayed()
        composeRule.onNodeWithText("탄수화물").assertIsDisplayed()
        composeRule.onNodeWithText("120g").assertIsDisplayed()
        composeRule.onNodeWithText("아침").assertIsDisplayed()
        composeRule.onNodeWithText("오늘 활동").assertIsDisplayed()
        composeRule.onNodeWithText("6,428 걸음").assertIsDisplayed()
        composeRule.onNodeWithText("약 4.7 km · 약 170 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("오늘 식사 스타일을 골라보세요").assertIsDisplayed()
        composeRule.onNodeWithText("추천 고르기").assertIsDisplayed()
        assertHomeFitsVertically()
        composeRule.onNodeWithTag("dashboard-open-daily-plan").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("dashboard-meal-breakfast").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, recommendationOpenCount)
            assertEquals(1, recordOpenCount)
        }
        capture("home-overview-360.png")
    }

    @Test fun combinedSystemAndAppLargeTextKeepsWalkingEstimateReachable() {
        var recommendationOpenCount = 0
        var recordOpenCount = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme(darkTheme = false, appFontScale = 1.3f) {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        assertEquals(1.69f, LocalDensity.current.fontScale, 0.001f)
                        DashboardContent(
                            selectedDate = LocalDate.now(), meals = emptyList(), totalCalories = 0,
                            targetCalories = 2_000, statusText = "목표까지 2,000kcal",
                            energyState = DashboardEnergyUiState(), onPreviousDay = {}, onNextDay = {},
                            onAddRecord = { recordOpenCount++ }, onOpenEnergySettings = {},
                            onOpenRecommendations = { recommendationOpenCount++ },
                            bodyProfile = BodyProfile(BodySex.FEMALE, 42, 163.5, 58.2),
                            stepCounterState = StepCounterUiState(StepCounterStatus.AVAILABLE, 5_000)
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("탄수화물").assertIsDisplayed()
        composeRule.onNodeWithText("아침").assertIsDisplayed()
        composeRule.onNodeWithText("오늘 활동").assertIsDisplayed()
        composeRule.onNodeWithText("5,000 걸음").assertIsDisplayed()
        composeRule.onNodeWithText("약 3.4 km · 약 102 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("오늘 식사 스타일을 골라보세요").assertIsDisplayed()
        composeRule.onNodeWithText("추천 고르기").assertIsDisplayed()
        assertHomeFitsVertically()
        composeRule.onNodeWithTag("dashboard-open-daily-plan").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("dashboard-meal-breakfast").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, recommendationOpenCount)
            assertEquals(1, recordOpenCount)
        }
        capture("home-overview-360-large.png")
    }

    private fun assertHomeFitsVertically() {
        // The daily plan summary and all meal actions must fit without vertical scrolling.
        assertTrue(composeRule.onAllNodes(hasScrollAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes().isEmpty())
        val home = composeRule.onNodeWithTag("dashboard-root").getUnclippedBoundsInRoot()
        val firstMeal = composeRule.onNodeWithTag("dashboard-meal-breakfast").getUnclippedBoundsInRoot()
        val lastMeal = composeRule.onNodeWithTag("dashboard-meal-snack").getUnclippedBoundsInRoot()
        val intake = composeRule.onNodeWithTag("dashboard-calorie-target").getUnclippedBoundsInRoot()
        val recommendation = composeRule.onNodeWithTag("dashboard-recommendation-card").getUnclippedBoundsInRoot()
        val summaryAction = composeRule.onNodeWithTag("dashboard-open-daily-plan").getUnclippedBoundsInRoot()
        assertTrue(firstMeal.top >= home.top)
        assertTrue(lastMeal.bottom <= intake.top)
        assertTrue(intake.bottom <= recommendation.top)
        assertTrue(recommendation.bottom <= home.bottom)
        assertTrue(summaryAction.top >= recommendation.top && summaryAction.bottom <= recommendation.bottom)
        assertTrue(summaryAction.bottom - summaryAction.top >= 48.dp)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(requireNotNull(context.getExternalFilesDir(null)), name)
        composeRule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        assertTrue(file.length() > 0L)
    }
}
