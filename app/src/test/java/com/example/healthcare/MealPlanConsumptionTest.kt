package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.ui.viewmodel.RecommendedIngredientUi
import com.example.healthcare.ui.viewmodel.SelectedMealUi
import org.junit.Assert.assertEquals
import org.junit.Test

class MealPlanConsumptionTest {
    private fun ingredient(id: Long, calories: Int, ratio: Double = 1.0) = RecommendedIngredientUi(
        ingredientId = id,
        foodItem = FoodItem(
            id = "food-$id", sourceType = "K-FIND", sourceFoodCode = "$id", name = "검증 음식 $id",
            normalizedName = "검증음식$id", referenceAmount = 100.0, unit = "g", energyKcal = calories.toDouble(),
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
}
