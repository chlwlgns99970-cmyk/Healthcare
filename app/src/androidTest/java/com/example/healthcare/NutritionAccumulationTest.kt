package com.example.healthcare

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.ui.screens.DashboardContent
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.DashboardEnergyUiState
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionAccumulationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bundledFoodSnapshotsAccumulateAndReactToUpdateDeleteWithoutInventingMissingValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        assertEquals("com.example.healthcare.qa", context.packageName)
        val database = AppDatabase.getDatabase(context)
        val food = withTimeout(180_000) {
            var result = database.foodItemDao().findById(STABLE_FOOD_ID)
            while (result == null) {
                delay(100)
                result = database.foodItemDao().findById(STABLE_FOOD_ID)
            }
            requireNotNull(result)
        }
        val dao = database.mealRecordDao()
        val date = "qa-nutrition-${System.nanoTime()}"
        val completeNutrition = Macronutrients.forFood(food, 150.0)
        val complete = MealRecord(
            id = System.currentTimeMillis(), date = date, time = "12:00", mealType = MealType.LUNCH,
            foodName = food.name, calories = (food.energyKcal * 1.5).toInt(),
            carbohydrateGrams = completeNutrition.carbohydrateGrams,
            proteinGrams = completeNutrition.proteinGrams,
            fatGrams = completeNutrition.fatGrams,
            servingAmount = 150.0, servingUnit = food.unit, foodItemId = food.id
        )
        val partial = MealRecord(
            id = complete.id + 1, date = date, time = "13:00", mealType = MealType.LUNCH,
            foodName = "일부 영양정보 기록", calories = 100, proteinGrams = 3.0
        )
        var updated = complete
        try {
            dao.insertMeal(complete)
            assertNutrition(knownTotal(dao, date), 30.39, 10.5, 10.83)

            dao.insertMeal(partial)
            assertNutrition(knownTotal(dao, date), 30.39, 13.5, 10.83)

            val doubled = Macronutrients.forFood(food, 300.0)
            updated = complete.copy(
                calories = (food.energyKcal * 3.0).toInt(), servingAmount = 300.0,
                carbohydrateGrams = doubled.carbohydrateGrams,
                proteinGrams = doubled.proteinGrams,
                fatGrams = doubled.fatGrams
            )
            dao.updateMeal(updated)
            assertNutrition(knownTotal(dao, date), 60.78, 24.0, 21.66)

            dao.deleteMeal(updated)
            val partialOnly = knownTotal(dao, date)
            assertNull(partialOnly.carbohydrateGrams)
            assertEquals(3.0, partialOnly.proteinGrams!!, TOLERANCE)
            assertNull(partialOnly.fatGrams)

            dao.deleteMeal(partial)
            assertEquals(Macronutrients.Unknown, knownTotal(dao, date))
        } finally {
            runCatching { dao.deleteMeal(updated) }
            runCatching { dao.deleteMeal(complete) }
            runCatching { dao.deleteMeal(partial) }
        }
    }

    @Test
    fun dashboardBindsCarbohydrateProteinAndFatWithoutAnUnsupportedSugarField() {
        composeRule.setContent {
            HealthCareTheme(darkTheme = false) {
                DashboardContent(
                    selectedDate = LocalDate.now(), meals = emptyList(), totalCalories = 300,
                    targetCalories = 2000, statusText = "목표까지 1,700kcal",
                    energyState = DashboardEnergyUiState(), onPreviousDay = {}, onNextDay = {},
                    onAddRecord = {}, onOpenEnergySettings = {},
                    nutrition = Macronutrients(30.0, 10.0, 5.0)
                )
            }
        }

        listOf("탄수화물", "단백질", "지방", "30g", "10g", "5g").forEach { text ->
            composeRule.onNodeWithText(text).assertExists()
        }
        composeRule.onNodeWithText("당류").assertDoesNotExist()
    }

    private suspend fun knownTotal(
        dao: com.example.healthcare.data.dao.MealRecordDao,
        date: String
    ): Macronutrients = Macronutrients.knownSum(
        dao.getNutritionByDate(date).first().map { row ->
            Macronutrients(row.carbohydrateGrams, row.proteinGrams, row.fatGrams)
        }
    )

    private fun assertNutrition(
        actual: Macronutrients,
        carbohydrate: Double,
        protein: Double,
        fat: Double
    ) {
        assertEquals(carbohydrate, actual.carbohydrateGrams!!, TOLERANCE)
        assertEquals(protein, actual.proteinGrams!!, TOLERANCE)
        assertEquals(fat, actual.fatGrams!!, TOLERANCE)
    }

    private companion object {
        const val STABLE_FOOD_ID = "kfind-d101-007450000-0001"
        const val TOLERANCE = 0.0001
    }
}
