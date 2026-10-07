package com.example.healthcare

import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.RecordInsights
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RecordInsightsTest {
    private val today = LocalDate.of(2026, 10, 2)
    private fun row(id: Long, daysAgo: Long, kcal: Int = 500, meal: MealType = MealType.LUNCH,
        protein: Double? = 20.0) = MealRecord(id = id, date = today.minusDays(daysAgo).toString(), time = "12:00",
        mealType = meal, foodName = "음식$id", calories = kcal,
        carbohydrateGrams = 60.0, proteinGrams = protein, fatGrams = 10.0)

    @Test fun sevenDaysCountsOnlyRecordedDatesAndAverageNeverIncludesMissingDays() {
        val records = listOf(row(1, 0, 1000), row(2, 0, 500, MealType.DINNER), row(3, 3, 1900), row(4, 7, 9000))
        val summary = RecordInsights.period(records, today, 7)
        assertEquals(2, summary.recordedDays)
        assertEquals(2, summary.calorieDays)
        assertEquals(1700, summary.averageCalories)
        assertEquals(listOf(MealType.LUNCH), summary.mostRecordedMeals)
        assertTrue(summary.lines.any { it.contains("7일 중 2일") })
    }
    @Test fun thirtyDaysUsesTheSameRecordedDayPolicyAndInclusiveWindow() {
        val summary = RecordInsights.period(listOf(row(1, 0, 1200), row(2, 29, 1800), row(3, 30, 9000)), today, 30)
        assertEquals(2, summary.recordedDays); assertEquals(1500, summary.averageCalories)
        assertTrue(summary.lines.any { it.contains("30일 중 2일") })
    }
    @Test fun unknownCaloriesDoNotBecomeZeroCalorieDays() {
        val summary = RecordInsights.period(listOf(row(1, 0, 0), row(2, 1, 1200)), today, 7)
        assertEquals(2, summary.recordedDays); assertEquals(1, summary.calorieDays)
        assertEquals(1200, summary.averageCalories); assertTrue(summary.partial)
    }
    @Test fun allUnknownCaloriesHaveNoInventedAverage() {
        val summary = RecordInsights.period(listOf(row(1, 0, 0)), today, 7)
        assertEquals(1, summary.recordedDays); assertEquals(0, summary.calorieDays); assertNull(summary.averageCalories)
    }
    @Test fun partialMacrosRemainPartialAndKnownValuesAreSummedOnly() {
        val summary = RecordInsights.period(listOf(row(1, 0, protein = null), row(2, 1)), today, 7)
        assertTrue(summary.partial); assertEquals(20.0, summary.nutrition.proteinGrams!!, 0.0)
        assertEquals(120.0, summary.nutrition.carbohydrateGrams!!, 0.0)
        assertTrue(summary.lines.any { it.contains("확인 가능한 기록") })
    }
    @Test fun missingAllProteinStaysNullAndEmptyHistoryHasNoAverage() {
        assertNull(RecordInsights.period(listOf(row(1, 0, protein = null)), today, 7).nutrition.proteinGrams)
        val empty = RecordInsights.period(emptyList(), today, 7)
        assertEquals(0, empty.recordedDays); assertNull(empty.averageCalories)
        assertEquals(listOf("아직 요약할 기록이 충분하지 않아요."), empty.lines)
    }
    @Test fun mostCommonMealsTiesAreTruthfulAndStable() {
        val summary = RecordInsights.period(listOf(row(1, 0, meal = MealType.BREAKFAST), row(2, 1, meal = MealType.LUNCH)), today, 7)
        assertEquals(listOf(MealType.BREAKFAST, MealType.LUNCH), summary.mostRecordedMeals)
    }
    @Test fun invalidAndFutureDatesDoNotEnterPeriodSummary() {
        val records = listOf(row(1, 0).copy(date = "invalid"), row(2, -1), row(3, 0))
        assertEquals(1, RecordInsights.period(records, today, 7).recordedDays)
    }
    @Test fun remainingAndDinnerRangeReusePlanAllocationAfterTwoRecordedMeals() {
        val next = RecordInsights.nextMeal(listOf(row(1, 0, 450, MealType.BREAKFAST), row(2, 0, 670)), 1800)
        assertEquals(1120, next.intakeCalories); assertEquals(1800, next.targetCalories)
        assertEquals(680, next.remainingCalories); assertEquals(MealType.DINNER, next.nextMeal)
        assertEquals(377..529, next.range)
        assertTrue(next.message.contains("저녁은 약 377~529 kcal"))
    }
    @Test fun unknownRecordIsMarkedAndStillExcludesAlreadyRecordedMeal() {
        val next = RecordInsights.nextMeal(listOf(row(1, 0, 0, MealType.BREAKFAST), row(2, 0, 600)), 1800)
        assertEquals(600, next.intakeCalories); assertEquals(1200, next.remainingCalories)
        assertEquals(MealType.DINNER, next.nextMeal); assertTrue(next.partial)
        assertTrue(next.message.startsWith("확인 가능한 기록 기준"))
    }
    @Test fun exceededTargetDoesNotGenerateNegativeRangeOrFastingAdvice() {
        val next = RecordInsights.nextMeal(listOf(row(1, 0, 1920, MealType.BREAKFAST)), 1800)
        assertEquals(0, next.remainingCalories); assertNull(next.range)
        assertTrue(next.message.contains("약 120 kcal 높아요"))
        assertFalse(next.message.contains("굶"))
    }
    @Test fun missingInvalidOrReachedTargetNeverCreatesDefaultBudget() {
        listOf(null, 0, -1).forEach { target ->
            val next = RecordInsights.nextMeal(emptyList(), target)
            assertNull(next.targetCalories); assertNull(next.remainingCalories); assertNull(next.range)
        }
        val reached = RecordInsights.nextMeal(listOf(row(1, 0, 1800)), 1800)
        assertEquals(0, reached.remainingCalories); assertNull(reached.range)
    }
    @Test fun allFourRecordedMealsHaveNoNextMealEvenWithRemainingBudget() {
        val records = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)
            .mapIndexed { index, meal -> row(index.toLong(), 0, 100, meal) }
        val next = RecordInsights.nextMeal(records, 1800)
        assertNull(next.nextMeal); assertNull(next.range); assertEquals(1400, next.remainingCalories)
    }
}
