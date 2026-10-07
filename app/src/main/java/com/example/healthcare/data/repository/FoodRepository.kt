package com.example.healthcare.data.repository

import com.example.healthcare.data.dao.FrequentFoodDao
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.RecommendationLearningStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 자주 먹는 음식 관련 데이터 처리를 담당하는 레포지토리
 */
open class FoodRepository(private val frequentFoodDao: FrequentFoodDao,
    private val recommendationLearningStore: RecommendationLearningStore? = null) {
    open val allFoods: Flow<List<FrequentFood>> = frequentFoodDao.getAllFoods()
        .map { foods -> foods.filter(FrequentFood::isFrequent) }
    open val favoriteFoods: Flow<List<FrequentFood>> = frequentFoodDao.getFavoriteFoods()

    open fun searchFoods(query: String): Flow<List<FrequentFood>> = frequentFoodDao.searchFoods(query)
        .map { foods -> foods.filter(FrequentFood::isFrequent) }

    open suspend fun insertFood(food: FrequentFood) {
        frequentFoodDao.insertFood(food)
    }

    open suspend fun insertFoodIfAbsent(food: FrequentFood): Boolean {
        val duplicate = frequentFoodDao.getAllFoods().first().firstOrNull { saved ->
            if (food.foodItemId != null) {
                saved.foodItemId == food.foodItemId
            } else {
                saved.foodItemId == null &&
                    saved.foodName.trim().equals(food.foodName.trim(), ignoreCase = true) &&
                    saved.defaultServing.trim().equals(food.defaultServing.trim(), ignoreCase = true) &&
                    saved.calories == food.calories
            }
        }
        if (duplicate != null) {
            val enriched = duplicate.copy(
                isFavorite = duplicate.isFavorite || food.isFavorite,
                isFrequent = duplicate.isFrequent || food.isFrequent,
                carbohydrateGrams = duplicate.carbohydrateGrams ?: food.carbohydrateGrams,
                proteinGrams = duplicate.proteinGrams ?: food.proteinGrams,
                fatGrams = duplicate.fatGrams ?: food.fatGrams,
                foodItemId = duplicate.foodItemId ?: food.foodItemId,
                sourceType = duplicate.sourceType ?: food.sourceType,
                sourceFoodCode = duplicate.sourceFoodCode ?: food.sourceFoodCode,
                brand = duplicate.brand ?: food.brand
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
        recommendationLearningStore?.favoriteChanged(food, false)
    }

    /** 즐겨찾기는 자주 먹는 음식과 독립적으로 유지하며 FoodItem 안정 ID로 중복을 막습니다. */
    open suspend fun setFavorite(snapshot: FrequentFood, favorite: Boolean): Boolean {
        val saved = frequentFoodDao.getAllFoods().first().firstOrNull { existing ->
            snapshot.foodItemId?.let { existing.foodItemId == it } == true ||
                (snapshot.foodItemId == null && existing.id == snapshot.id && snapshot.id != 0L)
        }
        if (favorite) {
            if (saved == null) {
                frequentFoodDao.insertFood(snapshot.copy(id = 0, isFavorite = true, isFrequent = false))
            } else if (!saved.isFavorite) {
                frequentFoodDao.updateFood(saved.copy(isFavorite = true))
            }
            return true
        }

        if (saved == null) return false
        if (saved.isFrequent) frequentFoodDao.updateFood(saved.copy(isFavorite = false))
        else frequentFoodDao.deleteFood(saved)
        recommendationLearningStore?.favoriteChanged(saved, false)
        return false
    }
}
