package com.example.healthcare

import com.example.healthcare.domain.NutritionLabelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionLabelParserTest {
    @Test
    fun oneHundredGramsAtThreeTwentyCalculatesSixtyGramsAsOneNinetyTwo() {
        val result = NutritionLabelParser.parse("영양정보 100 g당 320 kcal 총 내용량 60 g")
        val candidate = result.energyCandidates.single()

        assertEquals(100.0, candidate.referenceAmount, 0.0)
        assertEquals(320.0, candidate.energyKcal, 0.0)
        assertEquals(192, NutritionLabelParser.calculateCalories(candidate, 60.0))
    }

    @Test
    fun servingAndNutrientsAreKeptAsNullableRecognizedValues() {
        val result = NutritionLabelParser.parse(
            "1회 제공량 30 g당 96 kcal 총 2회 제공량 탄수화물 12 g 단백질 3 g 지방 4 g 나트륨 210 mg"
        )

        assertEquals(30.0, result.servingAmount ?: 0.0, 0.0)
        assertEquals(2.0, result.servingCount ?: 0.0, 0.0)
        assertEquals(12.0, result.carbohydrateGrams ?: 0.0, 0.0)
        assertEquals(210.0, result.sodiumMilligrams ?: 0.0, 0.0)
        assertTrue(result.energyCandidates.isNotEmpty())
    }

    @Test
    fun conflictingEnergyBasesRemainSeparateCandidates() {
        val result = NutritionLabelParser.parse("100 g당 320 kcal\n1회 제공량 30 g당 96 kcal")
        assertEquals(2, result.energyCandidates.size)
    }
}
