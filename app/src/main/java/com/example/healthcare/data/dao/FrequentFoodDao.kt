package com.example.healthcare.data.dao

import androidx.room.*
import com.example.healthcare.data.entity.FrequentFood
import kotlinx.coroutines.flow.Flow

/**
 * 자주 먹는 음식 데이터 접근 객체
 */
@Dao
interface FrequentFoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FrequentFood)

    @Update
    suspend fun updateFood(food: FrequentFood)

    @Delete
    suspend fun deleteFood(food: FrequentFood)

    @Query("SELECT * FROM frequent_foods ORDER BY foodName ASC")
    fun getAllFoods(): Flow<List<FrequentFood>>

    @Query("SELECT * FROM frequent_foods WHERE isFavorite = 1 ORDER BY foodName ASC")
    fun getFavoriteFoods(): Flow<List<FrequentFood>>

    @Query("SELECT * FROM frequent_foods WHERE foodName LIKE '%' || :query || '%'")
    fun searchFoods(query: String): Flow<List<FrequentFood>>
}
