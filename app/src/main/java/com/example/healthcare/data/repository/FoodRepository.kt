package com.example.healthcare.data.repository

import com.example.healthcare.data.dao.FrequentFoodDao
import com.example.healthcare.data.entity.FrequentFood
import kotlinx.coroutines.flow.Flow

/**
 * 자주 먹는 음식 관련 데이터 처리를 담당하는 레포지토리
 */
open class FoodRepository(private val frequentFoodDao: FrequentFoodDao) {
    open val allFoods: Flow<List<FrequentFood>> = frequentFoodDao.getAllFoods()
    open val favoriteFoods: Flow<List<FrequentFood>> = frequentFoodDao.getFavoriteFoods()

    open fun searchFoods(query: String): Flow<List<FrequentFood>> = frequentFoodDao.searchFoods(query)

    open suspend fun insertFood(food: FrequentFood) {
        frequentFoodDao.insertFood(food)
    }

    open suspend fun updateFood(food: FrequentFood) {
        frequentFoodDao.updateFood(food)
    }

    open suspend fun deleteFood(food: FrequentFood) {
        frequentFoodDao.deleteFood(food)
    }
}
