package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.MacronutrientFormatter
import com.example.healthcare.domain.Macronutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacronutrientsTest {
    private val food = FoodItem(
        id = "food", sourceType = "TEST", sourceFoodCode = "food", name = "테스트 음식",
        normalizedName = "테스트음식", referenceAmount = 100.0, unit = "g", energyKcal = 400.0,
        carbohydrateGrams = 80.0, proteinGrams = 10.0, fatGrams = 16.0,
        servingDescription = "100g 기준", dataVersion = "test", createdAt = 0, updatedAt = 0
    )

    @Test fun portionScalesAllMacrosByTheSameRatio() {
        val half = Macronutrients.forFood(food, 50.0)
        assertEquals(40.0, half.carbohydrateGrams!!, 0.0001)
        assertEquals(5.0, half.proteinGrams!!, 0.0001)
        assertEquals(8.0, half.fatGrams!!, 0.0001)
        val double = Macronutrients.forFood(food, 200.0)
        assertEquals(160.0, double.carbohydrateGrams!!, 0.0001)
        assertEquals(20.0, double.proteinGrams!!, 0.0001)
        assertEquals(32.0, double.fatGrams!!, 0.0001)
    }

    @Test fun strictTotalNeverTurnsMissingHistoricalDataIntoZero() {
        val total = Macronutrients.strictSum(listOf(
            Macronutrients(40.0, 5.0, 8.0),
            Macronutrients(null, 3.0, null)
        ))
        assertNull(total.carbohydrateGrams)
        assertEquals(8.0, total.proteinGrams!!, 0.0001)
        assertNull(total.fatGrams)
        assertEquals("정보 없음", MacronutrientFormatter.grams(total.carbohydrateGrams))
    }

    @Test fun displayUsesAtMostOneDecimalPlace() {
        assertEquals("24g", MacronutrientFormatter.grams(24.04))
        assertEquals("24.1g", MacronutrientFormatter.grams(24.06))
    }
}
