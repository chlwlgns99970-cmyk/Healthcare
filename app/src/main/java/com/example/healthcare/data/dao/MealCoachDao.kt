package com.example.healthcare.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.healthcare.data.entity.DailyMealPlan
import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.entity.MealTemplateIngredient
import com.example.healthcare.data.entity.PlannedMeal
import com.example.healthcare.data.entity.UserExcludedFood
import com.example.healthcare.data.entity.UserMealPreference
import kotlinx.coroutines.flow.Flow

@Dao
interface MealCoachDao {
    @Query("SELECT * FROM user_meal_preferences WHERE id = 1 LIMIT 1")
    fun observePreference(): Flow<UserMealPreference?>

    @Query("SELECT * FROM user_meal_preferences WHERE id = 1 LIMIT 1")
    suspend fun getPreference(): UserMealPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreference(preference: UserMealPreference)

    @Query("SELECT * FROM user_excluded_foods ORDER BY exclusionType, normalizedFoodName")
    fun observeExcludedFoods(): Flow<List<UserExcludedFood>>

    @Query("SELECT * FROM user_excluded_foods ORDER BY exclusionType, normalizedFoodName")
    suspend fun getExcludedFoods(): List<UserExcludedFood>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addExcludedFood(food: UserExcludedFood): Long

    @Delete
    suspend fun deleteExcludedFood(food: UserExcludedFood)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplates(templates: List<MealTemplate>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIngredients(ingredients: List<MealTemplateIngredient>)

    @Query("DELETE FROM meal_template_ingredients WHERE mealTemplateId IN (:templateIds)")
    suspend fun deleteIngredientsForTemplates(templateIds: List<String>)

    @Query("SELECT * FROM meal_templates WHERE supportedMealTypes LIKE '%|' || :mealType || '|%'")
    suspend fun getTemplatesForMeal(mealType: String): List<MealTemplate>

    @Query("SELECT * FROM meal_templates WHERE id = :templateId LIMIT 1")
    suspend fun getTemplate(templateId: String): MealTemplate?

    @Query("SELECT * FROM meal_template_ingredients WHERE mealTemplateId = :templateId ORDER BY id")
    suspend fun getIngredients(templateId: String): List<MealTemplateIngredient>

    @Query("SELECT COUNT(*) FROM meal_templates")
    suspend fun templateCount(): Int

    @Query("SELECT COUNT(*) FROM meal_templates")
    fun observeTemplateCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDailyPlan(plan: DailyMealPlan): Long

    @Query("SELECT * FROM daily_meal_plans WHERE localDate = :localDate LIMIT 1")
    suspend fun getDailyPlan(localDate: String): DailyMealPlan?

    @Update
    suspend fun updateDailyPlan(plan: DailyMealPlan)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlannedMeals(meals: List<PlannedMeal>)

    @Query("SELECT * FROM planned_meals WHERE dailyMealPlanId = :planId ORDER BY id")
    fun observePlannedMeals(planId: Long): Flow<List<PlannedMeal>>

    @Query("SELECT * FROM planned_meals WHERE dailyMealPlanId = :planId ORDER BY id")
    suspend fun getPlannedMeals(planId: Long): List<PlannedMeal>

    @Query("SELECT * FROM planned_meals WHERE id = :id LIMIT 1")
    suspend fun getPlannedMeal(id: Long): PlannedMeal?

    @Query(
        "SELECT selectedTemplateId FROM planned_meals " +
            "WHERE status = 'CONSUMED' AND selectedTemplateId IS NOT NULL " +
            "ORDER BY consumedAt DESC LIMIT :limit"
    )
    suspend fun getRecentConsumedTemplateIds(limit: Int = 30): List<String>

    @Update
    suspend fun updatePlannedMeal(meal: PlannedMeal)
}
