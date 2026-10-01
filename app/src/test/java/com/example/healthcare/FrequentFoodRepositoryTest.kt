package com.example.healthcare

import com.example.healthcare.data.dao.FrequentFoodDao
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    fun `same name basis and calories with different stable ids do not merge`() = runTest {
        val dao = FakeFrequentFoodDao()
        val repository = FoodRepository(dao)
        val first = FrequentFood(
            foodName = "참치김밥", defaultServing = "180g", calories = 420,
            foodItemId = "cu-tuna", sourceType = "K-FIND-PRODUCT", sourceFoodCode = "cu-tuna", brand = "CU"
        )
        val second = first.copy(foodItemId = "gs-tuna", sourceFoodCode = "gs-tuna", brand = "GS25")

        assertTrue(repository.insertFoodIfAbsent(first))
        assertTrue(repository.insertFoodIfAbsent(second))
        assertEquals(setOf("cu-tuna", "gs-tuna"), dao.values.value.mapNotNull { it.foodItemId }.toSet())
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

    @Test
    fun `favorite persists independently from frequent food and survives repository recreation`() = runTest {
        val dao = FakeFrequentFoodDao()
        val snapshot = favoriteSnapshot("food-cu-1", "참치김밥", "CU")
        val repository = FoodRepository(dao)

        assertTrue(repository.setFavorite(snapshot, true))
        assertEquals(1, repository.favoriteFoods.first().size)
        assertTrue(repository.allFoods.first().isEmpty())
        assertTrue(repository.searchFoods("참치").first().isEmpty())

        val recreated = FoodRepository(dao)
        assertEquals("food-cu-1", recreated.favoriteFoods.first().single().foodItemId)

        assertFalse(recreated.setFavorite(recreated.favoriteFoods.first().single(), false))
        assertTrue(recreated.favoriteFoods.first().isEmpty())
        assertTrue(dao.values.value.isEmpty())
    }

    @Test
    fun `same display name products remain separate favorites by stable food identity`() = runTest {
        val dao = FakeFrequentFoodDao()
        val repository = FoodRepository(dao)

        repository.setFavorite(favoriteSnapshot("food-cu-1", "참치김밥", "CU"), true)
        repository.setFavorite(favoriteSnapshot("food-gs-1", "참치김밥", "GS25"), true)
        repository.setFavorite(favoriteSnapshot("food-cu-1", "참치김밥", "CU"), true)

        val favorites = repository.favoriteFoods.first()
        assertEquals(2, favorites.size)
        assertEquals(setOf("food-cu-1", "food-gs-1"), favorites.mapNotNull { it.foodItemId }.toSet())
    }

    @Test
    fun `saving a favorite as frequent merges flags and removing favorite preserves frequent row`() = runTest {
        val dao = FakeFrequentFoodDao()
        val repository = FoodRepository(dao)
        val favorite = favoriteSnapshot("food-1", "현미밥", null)
        repository.setFavorite(favorite, true)

        assertFalse(repository.insertFoodIfAbsent(favorite.copy(isFavorite = false, isFrequent = true)))
        val merged = dao.values.value.single()
        assertTrue(merged.isFavorite)
        assertTrue(merged.isFrequent)
        assertEquals(1, repository.favoriteFoods.first().size)
        assertEquals(1, repository.allFoods.first().size)

        repository.setFavorite(merged, false)
        assertTrue(repository.favoriteFoods.first().isEmpty())
        assertEquals(1, repository.allFoods.first().size)
        assertTrue(dao.values.value.single().isFrequent)
    }

    private fun favoriteSnapshot(foodItemId: String, name: String, brand: String?) = FrequentFood(
        foodName = name,
        defaultServing = "100g",
        calories = 220,
        isFavorite = true,
        isFrequent = false,
        carbohydrateGrams = 35.0,
        proteinGrams = 9.0,
        fatGrams = 6.0,
        foodItemId = foodItemId,
        sourceType = "K-FIND-PRODUCT",
        sourceFoodCode = foodItemId,
        brand = brand
    )

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
