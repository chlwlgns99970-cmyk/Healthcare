package com.example.healthcare.data.dao

import androidx.room.*
import com.example.healthcare.data.entity.CalorieGoal
import kotlinx.coroutines.flow.Flow

/**
 * 칼로리 목표 데이터 접근 객체
 */
@Dao
interface CalorieGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: CalorieGoal)

    @Query("SELECT * FROM calorie_goals ORDER BY startDate DESC, id DESC LIMIT 1")
    fun getLatestGoal(): Flow<CalorieGoal?>

    @Query("SELECT * FROM calorie_goals WHERE startDate <= :date ORDER BY startDate DESC, id DESC LIMIT 1")
    fun getGoalForDate(date: String): Flow<CalorieGoal?>

    @Query("SELECT * FROM calorie_goals ORDER BY startDate DESC, id DESC")
    fun getAllGoals(): Flow<List<CalorieGoal>>
}
