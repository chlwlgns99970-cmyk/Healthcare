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

    /** 저장 당시의 영양정보 스냅샷만 반환합니다. 과거 null 값은 현재 음식 DB로 재계산하지 않습니다. */
    @Query(
        """
        SELECT id AS mealId, carbohydrateGrams, proteinGrams, fatGrams
        FROM meal_records
        WHERE date = :date
        ORDER BY time ASC
        """
    )
    fun getNutritionByDate(date: String): Flow<List<MealNutritionRow>>

}
