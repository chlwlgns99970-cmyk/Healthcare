package com.example.healthcare

import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.ui.viewmodel.recommendationMealsForDate
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardRecommendationPolicyTest {
    private val today = LocalDate.of(2026, 9, 26)
    private val preference = MealCoachRepository.defaultPreference(now = 1L)

    @Test
    fun lateNightStillUsesLastEnabledMealForImmediateHomeRecommendation() {
        assertEquals(
            listOf(MealType.DINNER),
            recommendationMealsForDate(today, today, LocalTime.of(23, 30), preference)
        )
    }

    @Test
    fun pastDateNeverPreparesTodaysAutomaticRecommendation() {
        assertTrue(
            recommendationMealsForDate(
                today.minusDays(1), today, LocalTime.NOON, preference
            ).isEmpty()
        )
    }
}
