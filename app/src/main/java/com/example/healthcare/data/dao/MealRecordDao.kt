package com.example.healthcare.data.dao

import androidx.room.*
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealNutritionRow
import kotlinx.coroutines.flow.Flow

/**
 * 식사 기록 데이터 접근 객체
 */
@Dao
interface MealRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealRecord)

    // Room은 List 삽입을 하나의 트랜잭션으로 실행하며 ABORT는 일부 저장을 방지합니다.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhotoMeals(meals: List<MealRecord>): List<Long>

    @Update
    suspend fun updateMeal(meal: MealRecord)

    @Delete
    suspend fun deleteMeal(meal: MealRecord)

    @Query("SELECT * FROM meal_records ORDER BY date DESC, time DESC")
    fun getAllMeals(): Flow<List<MealRecord>>

    @Query("SELECT * FROM meal_records WHERE date = :date ORDER BY time ASC")
    fun getMealsByDate(date: String): Flow<List<MealRecord>>

    @Query("SELECT SUM(calories) FROM meal_records WHERE date = :date")
    fun getTotalCaloriesByDate(date: String): Flow<Int?>

    /**
     * 신규 기록은 저장 시점 스냅샷을 사용합니다. 스냅샷이 없는 과거 기록만 연결된 음식의
     * 현재 원본을 기록 kcal 비율로 환산하며, 연결할 수 없으면 null을 반환합니다.
     */
    @Query(
        """
        SELECT m.id AS mealId,
          COALESCE(m.carbohydrateGrams,
            CASE WHEN f.energyKcal > 0 AND f.carbohydrateGrams IS NOT NULL
              THEN f.carbohydrateGrams * m.calories / f.energyKcal END) AS carbohydrateGrams,
          COALESCE(m.proteinGrams,
            CASE WHEN f.energyKcal > 0 AND f.proteinGrams IS NOT NULL
              THEN f.proteinGrams * m.calories / f.energyKcal END) AS proteinGrams,
          COALESCE(m.fatGrams,
            CASE WHEN f.energyKcal > 0 AND f.fatGrams IS NOT NULL
              THEN f.fatGrams * m.calories / f.energyKcal END) AS fatGrams
        FROM meal_records m
        LEFT JOIN food_items f ON f.id = m.foodItemId
        WHERE m.date = :date
        ORDER BY m.time ASC
        """
    )
    fun getNutritionByDate(date: String): Flow<List<MealNutritionRow>>

}
