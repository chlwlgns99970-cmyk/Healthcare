package com.example.healthcare

import com.example.healthcare.data.dao.FrequentFoodDao
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequentFoodRepositoryTest {
    @Test
    fun `same manual snapshot is saved once and can be deleted`() = runTest {
        val dao = FakeFrequentFoodDao()
        val repository = FoodRepository(dao)
        val apple = FrequentFood(foodName = "사과", defaultServing = "100g", calories = 52, isFavorite = true)

        assertTrue(repository.insertFoodIfAbsent(apple))
        assertFalse(repository.insertFoodIfAbsent(apple.copy(id = 0)))
        assertEquals(1, dao.values.value.size)

        repository.deleteFood(dao.values.value.single())
        assertTrue(dao.values.value.isEmpty())
    }

    @Test
    fun `same name with different basis or calories remains separately reusable`() = runTest {
        val dao = FakeFrequentFoodDao()
        val repository = FoodRepository(dao)

        assertTrue(repository.insertFoodIfAbsent(FrequentFood(foodName = "사과", defaultServing = "100g", calories = 52)))
        assertTrue(repository.insertFoodIfAbsent(FrequentFood(foodName = "사과", defaultServing = "200g", calories = 104)))
        assertEquals(2, dao.values.value.size)
    }

    @Test
    fun `existing saved food gains only previously missing macro snapshots`() = runTest {
        val dao = FakeFrequentFoodDao()
        val repository = FoodRepository(dao)
        val original = FrequentFood(
            foodName = "사과", defaultServing = "100g", calories = 52,
            carbohydrateGrams = null, proteinGrams = 0.3, fatGrams = null
        )
        repository.insertFood(original)

        assertFalse(repository.insertFoodIfAbsent(original.copy(
            carbohydrateGrams = 14.0, proteinGrams = 9.9, fatGrams = 0.2
        )))

        val enriched = dao.values.value.single()
        assertEquals(14.0, enriched.carbohydrateGrams!!, 0.0001)
        assertEquals(0.3, enriched.proteinGrams!!, 0.0001)
        assertEquals(0.2, enriched.fatGrams!!, 0.0001)
    }

    private class FakeFrequentFoodDao : FrequentFoodDao {
        val values = MutableStateFlow<List<FrequentFood>>(emptyList())
        private var nextId = 1L

        override suspend fun insertFood(food: FrequentFood) {
            values.value = values.value + food.copy(id = if (food.id == 0L) nextId++ else food.id)
        }

        override suspend fun updateFood(food: FrequentFood) {
            values.value = values.value.map { if (it.id == food.id) food else it }
        }

        override suspend fun deleteFood(food: FrequentFood) {
            values.value = values.value.filterNot { it.id == food.id }
        }

        override fun getAllFoods(): Flow<List<FrequentFood>> = values
        override fun getFavoriteFoods(): Flow<List<FrequentFood>> = values.map { foods -> foods.filter(FrequentFood::isFavorite) }
        override fun searchFoods(query: String): Flow<List<FrequentFood>> = values.map { foods ->
            foods.filter { it.foodName.contains(query, ignoreCase = true) }
        }
    }
}
