package com.example.healthcare

import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.DashboardMealOrder
import com.example.healthcare.domain.DashboardSummaryPolicy
import com.example.healthcare.domain.Macronutrients
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSummaryTest {
    @Test fun mealOrderIsBreakfastLunchDinnerSnack() {
        assertEquals(
            listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK),
            DashboardMealOrder
        )
    }

    @Test fun singleAndMultipleMealsShowNamesAndKnownCalories() {
        val summaries = DashboardSummaryPolicy.mealSummaries(
            listOf(
                meal(1, MealType.BREAKFAST, "바나나", 93),
                meal(2, MealType.LUNCH, "순두부찌개", 560),
                meal(3, MealType.LUNCH, "현미밥", 160),
                meal(4, MealType.LUNCH, "김치", 0)
            )
        )
        assertEquals("바나나 · 93 kcal", summaries.first().displayText)
        assertEquals("순두부찌개 외 2개 · 확인 가능한 기록 720 kcal", summaries[1].displayText)
        assertEquals("기록 없음", summaries[2].displayText)
    }

    @Test fun missingCaloriesAreNeverRenderedAsZero() {
        val breakfast = DashboardSummaryPolicy.mealSummaries(
            listOf(meal(1, MealType.BREAKFAST, "직접 입력 음식", 0))
        ).first()
        assertEquals("직접 입력 음식 · 열량 정보 없음", breakfast.displayText)
        assertFalse("0 kcal" in breakfast.displayText)
    }

    @Test fun reportTotalsMealsFoodsAndPartialNutritionWithoutInventingValues() {
        val report = DashboardSummaryPolicy.report(
            meals = listOf(
                meal(1, MealType.BREAKFAST, "바나나", 93),
                meal(2, MealType.LUNCH, "참치김밥", 420),
                meal(3, MealType.SNACK, "영양정보 없는 기록", 0)
            ),
            targetCalories = 2_000,
            nutrition = Macronutrients(80.0, 30.0, null)
        )
        assertEquals(513, report.totalCalories)
        assertEquals(1_487, report.remainingCalories)
        assertEquals("참치김밥", report.foods.first().name)
        assertEquals(null, report.foods.last().calories)
        assertTrue(report.hasPartialNutrition)
    }

    @Test fun reportExplainsTargetOverageWithoutJudgingFood() {
        val report = DashboardSummaryPolicy.report(
            listOf(meal(1, MealType.DINNER, "저녁", 2_180)),
            2_000,
            Macronutrients.Unknown
        )
        assertEquals(-180, report.remainingCalories)
        assertTrue("약 180 kcal 높아요" in report.message)
        assertFalse("나쁜" in report.message)
    }

    @Test fun situationMessagesUseRealStateAndStayStableForSameVariant() {
        val today = LocalDate.of(2026, 9, 30)
        val morning = DashboardSummaryPolicy.situationMessage(
            today, today, LocalTime.of(8, 0), emptyList(), 2_000, 0
        )
        val repeated = DashboardSummaryPolicy.situationMessage(
            today, today, LocalTime.of(8, 0), emptyList(), 2_000, 0
        )
        val alternative = DashboardSummaryPolicy.situationMessage(
            today, today, LocalTime.of(8, 0), emptyList(), 2_000, 1
        )
        assertEquals(morning, repeated)
        assertTrue(morning != alternative)
        assertTrue("아침" in morning)
    }

    @Test fun situationMessagesCoverRemainingOverAndNoGoalStates() {
        val today = LocalDate.of(2026, 9, 30)
        val oneMeal = listOf(meal(1, MealType.LUNCH, "점심", 600))
        assertTrue("1400" in DashboardSummaryPolicy.situationMessage(
            today, today, LocalTime.NOON, oneMeal, 2_000, 0
        ))
        assertTrue("높아요" in DashboardSummaryPolicy.situationMessage(
            today, today, LocalTime.NOON, listOf(meal(1, MealType.LUNCH, "점심", 2_100)), 2_000, 0
        ))
        assertTrue(DashboardSummaryPolicy.situationMessage(
            today, today, LocalTime.NOON, oneMeal, null, 0
        ).isNotBlank())
    }

    private fun meal(id: Long, type: MealType, name: String, calories: Int) = MealRecord(
        id = id,
        date = "2026-09-30",
        time = "12:00",
        mealType = type,
        foodName = name,
        calories = calories
    )
}
