package com.example.healthcare

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.update.FoodDataUpdateCandidate
import com.example.healthcare.data.update.FoodDataUpdateCoordinator
import com.example.healthcare.data.update.FoodDataUpdateSource
import com.example.healthcare.data.update.FoodDataUpdateStateStore
import com.example.healthcare.data.update.FoodDataUpdateStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodDataUpdateCoordinatorTest {
    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        assertEquals("com.example.healthcare.qa", context.packageName)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun validatedUpdateIsAtomicAndKeepsHistoricalMealSnapshot() = runBlocking {
        database.foodItemDao().upsertAll((1..10).map { food(it, "2026-08-28", 350.0) })
        database.mealRecordDao().insertMeal(
            MealRecord(
                date = "2026-09-27",
                time = "12:00",
                mealType = MealType.LUNCH,
                foodName = "기존 음식",
                calories = 350,
                carbohydrateGrams = 40.0,
                proteinGrams = 20.0,
                fatGrams = 10.0,
                foodItemId = "official-1"
            )
        )
        val updatedRows = (1..10).map { food(it, "2026-09-27", 360.0) }
        val coordinator = coordinator(
            FoodDataUpdateCandidate("TEST", "2026-09-27", updatedRows)
        )

        val result = coordinator.checkNow()

        assertEquals(FoodDataUpdateStatus.UPDATED, result.status)
        assertEquals("2026-09-27", result.activeVersion)
        assertEquals(360.0, database.foodItemDao().findById("official-1")!!.energyKcal, 0.0)
        val historical = database.mealRecordDao().getMealsByDate("2026-09-27").first().single()
        assertEquals(350, historical.calories)
        assertEquals(40.0, historical.carbohydrateGrams!!, 0.0)
        assertEquals(20.0, historical.proteinGrams!!, 0.0)
        assertEquals(10.0, historical.fatGrams!!, 0.0)
    }

    @Test
    fun rejectedCandidateRollsBackAndKeepsExistingSearchRows() = runBlocking {
        database.foodItemDao().upsertAll((1..10).map { food(it, "2026-08-28", 350.0) })
        val duplicate = food(1, "2026-09-27", 999.0)
        val coordinator = coordinator(
            FoodDataUpdateCandidate("TEST", "2026-09-27", List(10) { duplicate })
        )

        val result = coordinator.checkNow()

        assertEquals(FoodDataUpdateStatus.FAILED, result.status)
        assertEquals(10, database.foodItemDao().count())
        val existing = database.foodItemDao().findById("official-1")
        assertNotNull(existing)
        assertEquals(350.0, existing!!.energyKcal, 0.0)
        assertEquals("2026-08-28", existing.dataVersion)
    }

    private fun coordinator(candidate: FoodDataUpdateCandidate): FoodDataUpdateCoordinator {
        val source = object : FoodDataUpdateSource {
            override suspend fun check(currentVersion: String): FoodDataUpdateCandidate = candidate
        }
        return FoodDataUpdateCoordinator(
            database = database,
            store = FoodDataUpdateStateStore(context, "food_data_update_test_${System.nanoTime()}"),
            source = source
        )
    }

    private fun food(index: Int, version: String, kcal: Double) = FoodItem(
        id = "official-$index",
        sourceType = "K-FIND",
        sourceFoodCode = "CODE-$index",
        name = "음식 $index",
        normalizedName = "음식$index",
        referenceAmount = 100.0,
        unit = "g",
        energyKcal = kcal,
        carbohydrateGrams = 40.0,
        proteinGrams = 20.0,
        fatGrams = 10.0,
        servingDescription = "100g 기준",
        dataVersion = version,
        createdAt = 1L,
        updatedAt = 1L
    )
}
