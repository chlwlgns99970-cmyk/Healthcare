package com.example.healthcare.data.repository

import com.example.healthcare.data.dao.CalorieGoalDao
import com.example.healthcare.data.entity.CalorieGoal
import kotlinx.coroutines.flow.Flow

/**
 * 칼로리 목표 관련 데이터 처리를 담당하는 레포지토리
 */
class GoalRepository(private val calorieGoalDao: CalorieGoalDao) {
    val latestGoal: Flow<CalorieGoal?> = calorieGoalDao.getLatestGoal()
    val allGoals: Flow<List<CalorieGoal>> = calorieGoalDao.getAllGoals()

    fun getGoalForDate(date: String): Flow<CalorieGoal?> = calorieGoalDao.getGoalForDate(date)

    suspend fun insertGoal(goal: CalorieGoal) {
        calorieGoalDao.insertGoal(goal)
    }
}
