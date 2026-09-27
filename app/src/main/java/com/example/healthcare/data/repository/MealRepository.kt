package com.example.healthcare.data.repository

import com.example.healthcare.data.dao.MealRecordDao
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealNutritionRow
import kotlinx.coroutines.flow.Flow

/**
 * 식사 기록 관련 데이터 처리를 담당하는 레포지토리
 */
open class MealRepository(private val mealRecordDao: MealRecordDao) {
    open val allMeals: Flow<List<MealRecord>> = mealRecordDao.getAllMeals()

    open fun getMealsByDate(date: String): Flow<List<MealRecord>> = mealRecordDao.getMealsByDate(date)

    open fun getTotalCaloriesByDate(date: String): Flow<Int?> = mealRecordDao.getTotalCaloriesByDate(date)

    open fun getNutritionByDate(date: String): Flow<List<MealNutritionRow>> =
        mealRecordDao.getNutritionByDate(date)

    open suspend fun insertMeal(meal: MealRecord) {
        mealRecordDao.insertMeal(meal)
    }

    open suspend fun insertPhotoMeals(meals: List<MealRecord>): List<Long> {
        val insertedIds = mealRecordDao.insertPhotoMeals(meals)
        check(insertedIds.size == meals.size && insertedIds.none { it == -1L }) {
            "사진 분석 기록 전체를 저장하지 못했습니다."
        }
        return insertedIds
    }

    open suspend fun updateMeal(meal: MealRecord) {
        mealRecordDao.updateMeal(meal)
    }

    open suspend fun deleteMeal(meal: MealRecord) {
        mealRecordDao.deleteMeal(meal)
    }
}
