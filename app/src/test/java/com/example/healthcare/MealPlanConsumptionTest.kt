package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.ui.viewmodel.RecommendedIngredientUi
import com.example.healthcare.ui.viewmodel.SelectedMealUi
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.domain.FoodAmountPolicy
import com.example.healthcare.domain.RecordedAmountSnapshot
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlanConsumptionTest {
    private fun ingredient(id: Long, calories: Int, ratio: Double = 1.0) = RecommendedIngredientUi(
        ingredientId = id,
        foodItem = FoodItem(
            id = "food-$id", sourceType = "K-FIND", sourceFoodCode = "$id", name = "검증 음식 $id",
            normalizedName = "검증음식$id", referenceAmount = 100.0, unit = "g", energyKcal = calories.toDouble(),
            carbohydrateGrams = if (id == 2L) null else 30.0,
            proteinGrams = 10.0,
            fatGrams = if (id == 2L) null else 5.0,
            servingDescription = "100g 기준", dataVersion = "test", createdAt = 0, updatedAt = 0
        ),
        baseAmount = 100.0, amount = 100.0, unit = "g", calories = calories,
        adjustable = true, minimumAmount = null, maximumAmount = null, adjustmentStep = null,
        consumedRatio = ratio
    )

    @Test fun overallAndPerFoodIntakeRatiosRecalculateActualMealCalories() {
        val planned = SelectedMealUi("template", "검증 식단", "", listOf(ingredient(1, 400), ingredient(2, 200)), 600)
        assertEquals(600, planned.consumedCalories)
        assertEquals(300, planned.copy(ingredients = planned.ingredients.map { it.copy(consumedRatio = 0.5) }).consumedCalories)
        assertEquals(500, planned.copy(ingredients = listOf(ingredient(1, 400), ingredient(2, 200, 0.5))).consumedCalories)
        assertEquals(400, planned.copy(ingredients = listOf(ingredient(1, 400), ingredient(2, 200).copy(included = false))).consumedCalories)
    }

    @Test fun consumedNutritionKeepsKnownMacrosWhenOneIngredientIsMissingSomeValues() {
        val planned = SelectedMealUi(
            "template", "검증 식단", "", listOf(ingredient(1, 400), ingredient(2, 200)), 600
        )
        assertEquals(30.0, planned.consumedNutrition.carbohydrateGrams!!, 0.0001)
        assertEquals(20.0, planned.consumedNutrition.proteinGrams!!, 0.0001)
        assertEquals(5.0, planned.consumedNutrition.fatGrams!!, 0.0001)
    }

    @Test fun verifiedHumanUnitKeepsNutritionBasisAndConsumptionRatio() {
        val original = ingredient(1, 137).let { it.copy(foodItem = it.foodItem.copy(
            name = "한줄김밥", servingDescription = "공식 1줄 216g"), amount = 216.0, calories = 296) }
        assertEquals("줄", original.displayedUnit)
        assertEquals("1", original.displayedQuantity)
        val half = original.withQuantity("0.5")
        assertEquals(108.0, half.amount, 0.0001)
        assertEquals(148, half.calories)
        assertEquals(74, half.copy(consumedRatio = 0.5).consumedAmount!!.calories)
        assertEquals(54.0, half.copy(consumedRatio = 0.5).consumedSnapshot()!!.quantity * 216.0, 0.0001)
    }

    @Test fun switchingUnitsPreservesTheSameFoodAmount() {
        val original = ingredient(1, 137).let { it.copy(foodItem = it.foodItem.copy(
            servingDescription = "공식 1줄 216g"), amount = 108.0, calories = 148) }
        val grams = original.withQuantityUnit("g")
        assertEquals("108", grams.displayedQuantity)
        assertEquals(108.0, grams.amount, 0.0001)
        assertEquals(148, grams.calories)
        val roll = grams.withQuantityUnit("줄")
        assertEquals("0.5", roll.displayedQuantity)
        assertEquals(grams.amount, roll.amount, 0.0001)
    }

    @Test fun invalidQuantityCannotProduceConsumableCaloriesOrSnapshot() {
        listOf("", "0", "-1", "NaN", "1e3", "10001").forEach { value ->
            val changed = ingredient(1, 400).withQuantity(value)
            assertFalse(value, changed.amountValid)
            assertNull(changed.consumedAmount)
            assertNull(changed.consumedSnapshot())
        }
        assertTrue(ingredient(1, 400).withQuantity("2,5").amountValid)
    }

    @Test fun consumedCaloriesRoundOnlyOnceAfterApplyingActualAmountAndRatio() {
        val changed = ingredient(1, 100).let {
            it.copy(foodItem = it.foodItem.copy(energyKcal = 100.5), calories = 101, consumedRatio = 0.5)
        }
        assertEquals(50, changed.consumedAmount!!.calories)
        assertEquals(50, SelectedMealUi("template", "식사", "", listOf(changed), 101).consumedCalories)
    }

    @Test fun partialNutritionAndUnknownUnitsAreNeverInvented() {
        val partial = ingredient(2, 200).withQuantity("25")
        assertNull(partial.nutrition.carbohydrateGrams)
        assertNull(partial.nutrition.fatGrams)
        assertEquals(2.5, partial.nutrition.proteinGrams!!, 0.0001)
        val unsupported = partial.copy(unit = "ml")
        assertFalse(unsupported.amountValid)
        assertNull(unsupported.consumedAmount)
    }

    @Test fun recommendationCalorieHelperUsesSharedAmountPolicy() {
        val food = ingredient(1, 137).foodItem.copy(servingDescription = "공식 1줄 216g")
        assertEquals(FoodAmountPolicy.calculate(food, 0.5, "줄")!!.calories,
            NutritionRepository.calculateCalories(food, 108.0))
        assertEquals(0, NutritionRepository.calculateCalories(food, -1.0))
    }

    @Test fun savedRecommendationAmountCanBeReadWithoutCurrentFoodMetadata() {
        val chosen = ingredient(1, 137).let { it.copy(foodItem = it.foodItem.copy(
            servingDescription = "공식 1줄 216g"), amount = 216.0, calories = 296, consumedRatio = 0.5) }
        val snapshot = chosen.consumedSnapshot()!!
        val record = MealRecord(date = "2026-10-02", time = "12:00", mealType = MealType.LUNCH,
            foodName = "김밥", calories = 148, foodItemId = chosen.foodItem.id,
            servingAmount = 108.0, servingUnit = "g", portionPresetId = snapshot.presetId,
            portionDisplayLabel = snapshot.label)
        val recovered = RecordedAmountSnapshot.from(record)!!
        assertEquals("0.5줄", recovered.label)
        assertEquals(216.0, recovered.basisPerUnit, 0.0001)
    }
}
