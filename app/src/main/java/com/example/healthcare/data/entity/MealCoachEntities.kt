package com.example.healthcare.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_meal_preferences")
data class UserMealPreference(
    @PrimaryKey val id: Int = 1,
    val mealScheduleType: String = "FOUR_MEALS",
    val breakfastEnabled: Boolean = true,
    val lunchEnabled: Boolean = true,
    val dinnerEnabled: Boolean = true,
    val snackEnabled: Boolean = true,
    val breakfastRatio: Int = 25,
    val lunchRatio: Int = 35,
    val dinnerRatio: Int = 30,
    val snackRatio: Int = 10,
    val dietType: String = "GENERAL",
    val maxPreparationMinutes: Int = 30,
    val cookingMode: String = "ANY",
    val budgetLevel: String = "ANY",
    /** 검색/추천용 정규화 이름을 |항목| 형태로 저장합니다. */
    val preferredFoods: String = "",
    val recommendationDiversity: String = "BALANCED",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "user_excluded_foods",
    indices = [Index(value = ["normalizedFoodName", "exclusionType"], unique = true)]
)
data class UserExcludedFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedFoodName: String,
    val exclusionType: String,
    val createdAt: Long
)

@Entity(tableName = "meal_templates")
data class MealTemplate(
    @PrimaryKey val id: String,
    val name: String,
    /** |BREAKFAST|LUNCH| 형식입니다. */
    val supportedMealTypes: String,
    val totalKcal: Int,
    val proteinGrams: Double? = null,
    val carbohydrateGrams: Double? = null,
    val fatGrams: Double? = null,
    val preparationMinutes: Int,
    val costLevel: String,
    val tags: String = "",
    val allergens: String = "",
    val excludedDietTypes: String = "",
    val cuisineType: String? = null,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "meal_template_ingredients",
    indices = [Index(value = ["mealTemplateId"]), Index(value = ["foodItemId"])]
)
data class MealTemplateIngredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealTemplateId: String,
    val foodItemId: String,
    val amount: Double,
    val unit: String,
    val adjustable: Boolean,
    val minimumAmount: Double? = null,
    val maximumAmount: Double? = null,
    val adjustmentStep: Double? = null
)

@Entity(
    tableName = "daily_meal_plans",
    indices = [Index(value = ["localDate"], unique = true)]
)
data class DailyMealPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localDate: String,
    val targetKcalSnapshot: Int,
    val planStatus: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "planned_meals",
    indices = [
        Index(value = ["dailyMealPlanId"]),
        Index(value = ["dailyMealPlanId", "mealType"], unique = true)
    ]
)
data class PlannedMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dailyMealPlanId: Long,
    val mealType: String,
    val plannedKcal: Int,
    val selectedTemplateId: String? = null,
    val status: String,
    val consumedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
