package com.example.healthcare

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.ui.HealthcareApp
import com.example.healthcare.ui.screens.DashboardContent
import com.example.healthcare.ui.screens.HistoryDetailPane
import com.example.healthcare.ui.screens.HistoryEditPane
import com.example.healthcare.ui.screens.HistoryListPane
import com.example.healthcare.ui.screens.SettingsContent
import com.example.healthcare.ui.screens.MealPreferenceContent
import com.example.healthcare.ui.screens.WellnessManualRecordScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordUiState
import com.example.healthcare.ui.viewmodel.DashboardEnergyUiState
import com.example.healthcare.ui.viewmodel.EnergySettingsUiState
import com.example.healthcare.ui.viewmodel.MealEditUiState
import com.example.healthcare.ui.viewmodel.MealPreferenceUiState
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class CompactWidthUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dashboardAndSettingsRemainUsableAt360Dp() {
        var screen by mutableIntStateOf(0)
        var energyOpened = false
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    if (screen == 0) {
                        DashboardContent(
                            selectedDate = LocalDate.of(2026, 9, 17),
                            meals = emptyList(),
                            totalCalories = 2625,
                            targetCalories = 2325,
                            statusText = "목표 대비 +300kcal",
                            energyState = configuredEnergyState(),
                            onPreviousDay = {},
                            onNextDay = {},
                            onAddRecord = {},
                            onOpenEnergySettings = { energyOpened = true }
                        )
                    } else {
                        SettingsContent(
                            targetCalories = 2000,
                            energyState = EnergySettingsUiState(
                                bmrInput = "1500",
                                activityLevel = ActivityLevel.CUSTOM,
                                customPalInput = "2.40",
                                targetMode = TargetMode.MAINTENANCE,
                                maintenancePreviewKcal = 3600
                            ),
                            onGoalClick = {},
                            onBmrChange = {},
                            onActivityLevelSelected = {},
                            onCustomPalChange = {},
                            onTargetModeSelected = {},
                            onSaveEnergyProfile = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("2,625").assertIsDisplayed()
        composeRule.onNodeWithText("/ 2,325 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("300 kcal 초과").assertIsDisplayed()
        composeRule.onNodeWithText("아침").assertIsDisplayed()
        composeRule.onNodeWithText("오늘 활동").assertIsDisplayed()
        val mealTops = listOf("breakfast", "snack", "lunch", "dinner").map { meal ->
            composeRule.onNodeWithTag("dashboard-meal-$meal").fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(mealTops.sorted(), mealTops)
        composeRule.onNodeWithTag("dashboard-calorie-target").performClick()
        composeRule.runOnIdle { assert(energyOpened) }

        composeRule.runOnIdle { screen = 1 }
        scrollTo("3,600")
        composeRule.onNodeWithText("3,600").assertIsDisplayed()
        scrollTo("에너지 기준 저장")
        composeRule.onNodeWithText("에너지 기준 저장").assertIsDisplayed()
    }

    @Test
    fun dashboardDateUsesCompactCalendarPicker() {
        val initialDate = LocalDate.of(2026, 9, 17)
        var confirmedDate by mutableStateOf<LocalDate?>(null)
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    DashboardContent(
                        selectedDate = initialDate,
                        meals = emptyList(),
                        totalCalories = 0,
                        targetCalories = 2000,
                        statusText = "목표까지 2,000kcal",
                        energyState = DashboardEnergyUiState(),
                        onPreviousDay = {},
                        onNextDay = {},
                        onAddRecord = {},
                        onOpenEnergySettings = {},
                        onDateSelected = { confirmedDate = it }
                    )
                }
            }
        }

        composeRule.onNodeWithText("9월 17일 (목)").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("이전 날짜").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("다음 날짜").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("설정 열기").assertCountEquals(0)
        composeRule.onNodeWithTag("dashboard-date-picker").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("확인").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(initialDate, confirmedDate) }
    }

    @Test
    fun recordScreensAndPhotoReferenceRemainUsableAt360Dp() {
        val meal = longMeal()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = File(context.cacheDir, "compact-width-photo.jpg")
        FileOutputStream(photo).use { output ->
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.JPEG, 90, output)
        }
        var screen by mutableIntStateOf(0)
        try {
            composeRule.setContent {
                HealthCareTheme {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        when (screen) {
                            0 -> HistoryListPane(
                                selectedDate = LocalDate.of(2026, 9, 17),
                                meals = listOf(meal),
                                totalCalories = meal.calories,
                                targetCalories = 2000,
                                statusText = "목표까지 1,320kcal",
                                onDateSelected = {},
                                onItemClick = {}
                            )
                            1 -> HistoryDetailPane(meal, {}, {}, {})
                            2 -> HistoryEditPane(
                                state = editState(meal),
                                onFoodNameChange = {},
                                onCaloriesChange = {},
                                onMealTypeChange = {},
                                onDateChange = {},
                                onTimeChange = {},
                                onServingAmountChange = {},
                                onServingUnitChange = {},
                                onMemoChange = {},
                                onSave = {},
                                onBack = {}
                            )
                            else -> WellnessManualRecordScreen(
                                uiState = AddRecordUiState(),
                                frequentFoods = emptyList(),
                                favoriteFoods = emptyList(),
                                recentMeals = emptyList(),
                                photoPath = photo.absolutePath,
                                onBack = {},
                                onSaved = {},
                                onPhotoRecord = {},
                                viewModel = null
                            )
                        }
                    }
                }
            }

            scrollTo(meal.foodName)
            composeRule.onNodeWithText(meal.foodName).assertIsDisplayed()

            composeRule.runOnIdle { screen = 1 }
            scrollTo(meal.memo!!)
            composeRule.onNodeWithText(meal.memo!!).assertIsDisplayed()

            composeRule.runOnIdle { screen = 2 }
            scrollTo("수정 저장")
            composeRule.onNodeWithText("수정 저장").assertIsDisplayed()

            composeRule.runOnIdle { screen = 3 }
            composeRule.onNodeWithContentDescription("기록 작성에 참고할 음식 사진").assertIsDisplayed()
            scrollTo("기록 저장")
            composeRule.onNodeWithText("기록 저장").assertIsDisplayed()
        } finally {
            photo.delete()
        }
    }

    @Test
    fun todayHistoryHeaderAndFirstRecordAreVisibleAt360DpWithLargeText() {
        val meal = longMeal()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme(appFontScale = 1.3f) {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        HistoryListPane(
                            selectedDate = LocalDate.of(2026, 9, 17),
                            meals = listOf(meal),
                            totalCalories = meal.calories,
                            targetCalories = 2000,
                            statusText = "목표까지 1,320kcal",
                            onDateSelected = {},
                            onItemClick = {},
                            screenTitle = "식사 기록"
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag("history-today-header").assertIsDisplayed()
        composeRule.onNodeWithText("오늘의 기록").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(meal.foodName))
        composeRule.onNodeWithText(meal.foodName).assertIsDisplayed()
    }

    @Test
    fun topLevelNavigationFitsInside360Dp() {
        composeRule.setContent {
            Box(Modifier.width(360.dp).height(800.dp)) {
                HealthcareApp()
            }
        }

        listOf("홈", "추천", "기록", "통계", "설정").forEach { label ->
            composeRule.onAllNodesWithText(label).onFirst().assertIsDisplayed()
        }
    }

    @Test
    fun preferredFoodFieldRemainsVisibleWhenFocusedAt360Dp() {
        composeRule.setContent {
            HealthCareTheme {
                Box(Modifier.width(360.dp).height(800.dp)) {
                    MealPreferenceContent(
                        state = MealPreferenceUiState(),
                        onBack = {},
                        onMealEnabled = { _, _ -> },
                        onRatioChange = { _, _ -> },
                        onDietType = {},
                        onCookingMode = {},
                        onBudget = {},
                        onDiversity = {},
                        onAllergyInput = {},
                        onAddAllergy = {},
                        onDislikeInput = {},
                        onAddDislike = {},
                        onPreferredInput = {},
                        onRemoveExcluded = {},
                        onSave = {}
                    )
                }
            }
        }

        val preferredField = hasSetTextAction() and hasText("선호 음식", substring = true)
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(preferredField)
        composeRule.onNode(preferredField).performClick().performTextInput("사과")
        composeRule.onNode(preferredField).assertIsDisplayed()
    }

    private fun scrollTo(text: String) {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    }

    private fun configuredEnergyState() = DashboardEnergyUiState(
        profile = EnergyProfileHistory(
            basalMetabolicRateKcal = 1500,
            activityLevelCode = ActivityLevel.LIGHT,
            palMultiplier = 1.55,
            targetMode = TargetMode.MAINTENANCE,
            effectiveFromDate = "2026-09-17"
        ),
        manualTargetCalories = 2000,
        effectiveTargetCalories = 2325,
        intakeCalories = 2625,
        maintenanceCalories = 2325,
        dailySurplusCalories = 300,
        sevenDayEquivalentKg = 0.2727,
        thirtyDayEquivalentKg = 1.1688
    )

    private fun longMeal() = MealRecord(
        id = 1,
        date = "2026-09-17",
        time = "12:30",
        mealType = MealType.LUNCH,
        foodName = "닭가슴살과 현미밥을 곁들인 매콤한 구운 채소 샐러드",
        calories = 680,
        memo = "작은 화면에서 상세 내용과 긴 메모가 카드 밖으로 잘리지 않는지 확인하는 테스트 메모입니다.",
        servingAmount = 1.5,
        servingUnit = "인분"
    )

    private fun editState(meal: MealRecord) = MealEditUiState(
        original = meal,
        foodName = meal.foodName,
        calories = meal.calories.toString(),
        mealType = meal.mealType,
        date = LocalDate.parse(meal.date),
        time = LocalTime.parse(meal.time),
        servingAmount = "1.5",
        servingUnit = meal.servingUnit.orEmpty(),
        memo = meal.memo.orEmpty()
    )
}
