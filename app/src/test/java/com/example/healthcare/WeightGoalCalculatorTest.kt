package com.example.healthcare

import com.example.healthcare.domain.WeightGoalCalculator
import com.example.healthcare.domain.IntakePaceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightGoalCalculatorTest {
    @Test fun oneKilogramInOneWeekIsCalculatedButNotApplied() {
        val result = WeightGoalCalculator.calculate(70.0, 69.0, 1, 2325, 1500).getOrThrow()
        assertEquals(1100, result.dailyDeficitKcal)
        assertEquals(1225, result.proposedIntakeKcal)
        assertFalse(result.canApply)
    }

    @Test fun oneKilogramInFourWeeksIsASeparatelyApplicableExplicitGoal() {
        val result = WeightGoalCalculator.calculate(70.0, 69.0, 4, 2325, 1500).getOrThrow()
        assertEquals(275, result.dailyDeficitKcal)
        assertEquals(2050, result.proposedIntakeKcal)
        assertTrue(result.canApply)
    }

    @Test fun multipleKilogramsAndInvalidInputsAreHandled() {
        val result = WeightGoalCalculator.calculate(80.0, 76.0, 12, 2500, 1600).getOrThrow()
        assertEquals(367, result.dailyDeficitKcal)
        assertTrue(result.canApply)
        assertTrue(WeightGoalCalculator.calculate(70.0, 70.0, 4, 2300, 1500).isFailure)
        assertTrue(WeightGoalCalculator.calculate(70.0, 71.0, 4, 2300, 1500).isFailure)
        assertTrue(WeightGoalCalculator.calculate(70.0, 69.0, 0, 2300, 1500).isFailure)
    }

    @Test fun resultBelowBmrCannotBeApplied() {
        val result = WeightGoalCalculator.calculate(70.0, 68.0, 4, 1900, 1500).getOrThrow()
        assertFalse(result.canApply)
        assertTrue(result.guidance.contains("기초대사량"))
    }

    @Test fun extremeTwelveKilogramsInFourWeeksKeepsDeficitButCannotApply() {
        val result = WeightGoalCalculator.calculate(81.0, 69.0, 4, 1550, 1400).getOrThrow()
        assertEquals(3300, result.dailyDeficitKcal)
        assertEquals(-1750, result.proposedIntakeKcal)
        assertFalse(result.canApply)
    }

    @Test fun intakePaceShowsTwentySixDaysForThreeHundredKcalDeficit() {
        val result = WeightGoalCalculator.calculateIntakePace(2200, 1500, 1900).getOrThrow()
        assertEquals(-300, result.dailyBalanceKcal)
        assertEquals(26, result.estimatedDaysForOneKg)
        assertEquals(IntakePaceStatus.DEFICIT, result.status)
        assertTrue(result.canApply)
    }

    @Test fun intakePaceShowsNineteenDaysForFourHundredKcalDeficit() {
        val result = WeightGoalCalculator.calculateIntakePace(2200, 1500, 1800).getOrThrow()
        assertEquals(-400, result.dailyBalanceKcal)
        assertEquals(19, result.estimatedDaysForOneKg)
    }

    @Test fun maintenanceDoesNotShowDurationAndSurplusUsesTheSameEnergyEquivalent() {
        val maintenance = WeightGoalCalculator.calculateIntakePace(2200, 1500, 2200).getOrThrow()
        val threeHundredSurplus = WeightGoalCalculator.calculateIntakePace(2200, 1500, 2500).getOrThrow()
        val fourHundredSurplus = WeightGoalCalculator.calculateIntakePace(2200, 1500, 2600).getOrThrow()
        assertEquals(IntakePaceStatus.MAINTENANCE, maintenance.status)
        assertEquals(0, maintenance.dailyBalanceKcal)
        assertEquals(null, maintenance.estimatedDaysForOneKg)
        assertEquals(IntakePaceStatus.SURPLUS, threeHundredSurplus.status)
        assertEquals(300, threeHundredSurplus.dailyBalanceKcal)
        assertEquals(26, threeHundredSurplus.estimatedDaysForOneKg)
        assertEquals(IntakePaceStatus.SURPLUS, fourHundredSurplus.status)
        assertEquals(400, fourHundredSurplus.dailyBalanceKcal)
        assertEquals(19, fourHundredSurplus.estimatedDaysForOneKg)
    }

    @Test fun belowBmrIntakeIsNotAppliedOrPresentedAsFastSuccess() {
        val result = WeightGoalCalculator.calculateIntakePace(2300, 1500, 900).getOrThrow()
        assertEquals(IntakePaceStatus.TOO_LOW, result.status)
        assertEquals(null, result.estimatedDaysForOneKg)
        assertFalse(result.canApply)
    }
}
