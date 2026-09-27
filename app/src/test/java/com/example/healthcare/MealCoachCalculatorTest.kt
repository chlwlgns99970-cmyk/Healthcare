package com.example.healthcare

import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.domain.MealCoachCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealCoachCalculatorTest {
    private val preference = MealCoachRepository.defaultPreference(now = 1L)

    @Test
    fun defaultRatiosAllocateTwoThousandExactly() {
        val result = MealCoachCalculator.allocate(2000, preference).associate { it.mealType to it.calories }

        assertEquals(500, result[MealType.BREAKFAST])
        assertEquals(700, result[MealType.LUNCH])
        assertEquals(600, result[MealType.DINNER])
        assertEquals(200, result[MealType.SNACK])
        assertEquals(2000, result.values.sum())
    }

    @Test
    fun remainingCaloriesAreRedistributedByRemainingRatios() {
        val result = MealCoachCalculator.redistribute(
            targetCalories = 2000,
            currentIntakeCalories = 700,
            remainingMeals = listOf(MealType.LUNCH, MealType.DINNER, MealType.SNACK),
            preference = preference
        )

        assertEquals(1300, result.remainingCalories)
        assertEquals(1300, result.budgets.sumOf { it.calories })
        assertEquals(listOf(MealType.LUNCH, MealType.DINNER, MealType.SNACK), result.budgets.map { it.mealType })
    }

    @Test
    fun finalMealCorrectsRoundingDifference() {
        val result = MealCoachCalculator.allocate(1999, preference)
        assertEquals(1999, result.sumOf { it.calories })
        assertTrue(result.all { it.calories >= 0 })
    }

    @Test
    fun exceededTargetDoesNotCreateZeroCalorieMealRecommendations() {
        val result = MealCoachCalculator.redistribute(
            targetCalories = 2000,
            currentIntakeCalories = 2200,
            remainingMeals = listOf(MealType.DINNER),
            preference = preference
        )

        assertTrue(result.targetExceeded)
        assertEquals(0, result.remainingCalories)
        assertTrue(result.budgets.isEmpty())
    }

    @Test
    fun dinnerDoesNotInheritUnrecordedMeals() {
        assertEquals(600, MealCoachCalculator.nextMealBudget(2000, 0, MealType.DINNER, preference))
        assertEquals(600, MealCoachCalculator.nextMealBudget(2000, 700, MealType.DINNER, preference))
        assertEquals(300, MealCoachCalculator.nextMealBudget(2000, 1700, MealType.DINNER, preference))
    }

    @Test
    fun dinnerOnlyPreferenceCanAllocateEntireDailyTarget() {
        val dinnerOnly = preference.copy(breakfastEnabled = false, lunchEnabled = false,
            snackEnabled = false, dinnerRatio = 100)
        assertEquals(2000, MealCoachCalculator.nextMealBudget(2000, 0, MealType.DINNER, dinnerOnly))
    }
}
