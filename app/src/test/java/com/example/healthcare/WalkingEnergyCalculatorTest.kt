package com.example.healthcare

import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodySex
import com.example.healthcare.domain.WalkingEnergyCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkingEnergyCalculatorTest {
    private val profile = BodyProfile(BodySex.MALE, 35, 175.0, 70.0)

    @Test fun `zero steps produce zero distance and energy`() {
        val result = requireNotNull(WalkingEnergyCalculator.estimate(0, profile))
        assertEquals(0.0, result.estimatedDistanceKm, 0.0)
        assertEquals(0.0, result.estimatedNetKcal, 0.0)
        assertEquals(0.0, result.estimatedKcalPerStep, 0.0)
    }

    @Test fun `one thousand steps use height derived step length and net energy cost`() {
        val result = requireNotNull(WalkingEnergyCalculator.estimate(1_000, profile))
        assertEquals(0.72625, result.estimatedDistanceKm, 0.00001)
        assertEquals(26.37, result.estimatedNetKcal, 0.02)
        assertEquals(0.026, result.estimatedKcalPerStep, 0.001)
    }

    @Test fun `energy scales linearly without a universal calorie per step constant`() {
        val thousand = requireNotNull(WalkingEnergyCalculator.estimate(1_000, profile))
        val fiveThousand = requireNotNull(WalkingEnergyCalculator.estimate(5_000, profile))
        val tenThousand = requireNotNull(WalkingEnergyCalculator.estimate(10_000, profile))
        assertEquals(thousand.estimatedNetKcal * 5, fiveThousand.estimatedNetKcal, 0.001)
        assertEquals(thousand.estimatedNetKcal * 10, tenThousand.estimatedNetKcal, 0.001)
    }

    @Test fun `new body weight immediately changes todays estimate`() {
        val lighter = requireNotNull(WalkingEnergyCalculator.estimate(5_000, profile.copy(weightKg = 60.0)))
        val heavier = requireNotNull(WalkingEnergyCalculator.estimate(5_000, profile.copy(weightKg = 90.0)))
        assertTrue(heavier.estimatedNetKcal > lighter.estimatedNetKcal)
        assertEquals(1.5, heavier.estimatedNetKcal / lighter.estimatedNetKcal, 0.001)
    }

    @Test fun `height and current sex model affect estimated distance`() {
        val male = requireNotNull(WalkingEnergyCalculator.estimate(5_000, profile))
        val female = requireNotNull(WalkingEnergyCalculator.estimate(
            5_000, profile.copy(sex = BodySex.FEMALE, heightCm = 160.0)
        ))
        assertTrue(male.estimatedDistanceKm > female.estimatedDistanceKm)
    }

    @Test fun `missing profile and invalid steps do not invent an estimate`() {
        assertNull(WalkingEnergyCalculator.estimate(1_000, null))
        assertNull(WalkingEnergyCalculator.estimate(-1, profile))
    }
}
