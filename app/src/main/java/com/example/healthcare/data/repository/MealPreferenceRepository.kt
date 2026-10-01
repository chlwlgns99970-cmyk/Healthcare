package com.example.healthcare.data.repository

import com.example.healthcare.data.entity.UserExcludedFood
import com.example.healthcare.data.entity.UserMealPreference
import kotlinx.coroutines.flow.Flow

/** The existing meal-coach preference row and exclusions used by setup and Settings. */
interface MealPreferenceRepository {
    val preference: Flow<UserMealPreference>
    val excludedFoods: Flow<List<UserExcludedFood>>
    suspend fun ensureDefaultPreference(): UserMealPreference
    suspend fun savePreference(preference: UserMealPreference)
    suspend fun addExcludedFood(name: String, type: String): Boolean
    suspend fun deleteExcludedFood(food: UserExcludedFood)
}
