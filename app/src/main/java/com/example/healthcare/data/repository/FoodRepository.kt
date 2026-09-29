package com.example.healthcare.data.repository

import com.example.healthcare.data.dao.FrequentFoodDao
import com.example.healthcare.data.entity.FrequentFood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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

    open suspend fun insertFoodIfAbsent(food: FrequentFood): Boolean {
        val duplicate = searchFoods(food.foodName).first().firstOrNull { saved ->
            saved.foodName.trim().equals(food.foodName.trim(), ignoreCase = true) &&
                saved.defaultServing.trim().equals(food.defaultServing.trim(), ignoreCase = true) &&
                saved.calories == food.calories
        }
        if (duplicate != null) {
            val enriched = duplicate.copy(
                carbohydrateGrams = duplicate.carbohydrateGrams ?: food.carbohydrateGrams,
                proteinGrams = duplicate.proteinGrams ?: food.proteinGrams,
                fatGrams = duplicate.fatGrams ?: food.fatGrams
            )
            if (enriched != duplicate) updateFood(enriched)
            return false
        }
        insertFood(food)
        return true
    }

    open suspend fun updateFood(food: FrequentFood) {
        frequentFoodDao.updateFood(food)
    }

    open suspend fun deleteFood(food: FrequentFood) {
        frequentFoodDao.deleteFood(food)
    }
}
