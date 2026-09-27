package com.example.healthcare.data.model

import com.example.healthcare.domain.Macronutrients

/** MealRecord 스냅샷 또는 안전하게 연결된 FoodItem에서 계산한 한 기록의 영양정보입니다. */
data class MealNutritionRow(
    val mealId: Long,
    val carbohydrateGrams: Double?,
    val proteinGrams: Double?,
    val fatGrams: Double?
) {
    fun asMacronutrients() = Macronutrients(carbohydrateGrams, proteinGrams, fatGrams)
}
