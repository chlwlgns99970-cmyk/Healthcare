package com.example.healthcare

import com.example.healthcare.domain.StepCountAccumulator
import com.example.healthcare.domain.StepCountSnapshot
import com.example.healthcare.domain.StepCounterAccessPolicy
import com.example.healthcare.domain.StepCounterAction
import com.example.healthcare.domain.StepCounterStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepCounterTest {
    private val today = LocalDate.of(2026, 9, 24)

    @Test fun firstReadingBecomesBaselineInsteadOfTodaysSteps() {
        val update = requireNotNull(
            StepCountAccumulator.applyReading(StepCountSnapshot(), 50_000.0, today, 1_000L)
        )
        assertTrue(update.baselineStarted)
        assertEquals(0L, update.snapshot.todaySteps)
        assertEquals(50_000L, update.snapshot.lastSensorTotal)
    }

    @Test fun sameDaySensorIncreaseAddsOnlyTheDelta() {
        val previous = StepCountSnapshot(today, 0L, 50_000L, 1_000L)
        val hundred = requireNotNull(
            StepCountAccumulator.applyReading(previous, 50_100.0, today, 2_000L)
        )
        val thousandMore = requireNotNull(
            StepCountAccumulator.applyReading(hundred.snapshot, 51_100.0, today, 3_000L)
        )
        assertEquals(100L, hundred.snapshot.todaySteps)
        assertEquals(1_100L, thousandMore.snapshot.todaySteps)
    }

    @Test fun sensorResetKeepsTodaysStepsAndStartsFromNewTotal() {
        val previous = StepCountSnapshot(today, 2_000L, 52_300L, 1_000L)
        val reset = requireNotNull(
            StepCountAccumulator.applyReading(previous, 320.0, today, 2_000L)
        )
        assertTrue(reset.sensorReset)
        assertEquals(2_000L, reset.snapshot.todaySteps)
        val continued = requireNotNull(
            StepCountAccumulator.applyReading(reset.snapshot, 420.0, today, 3_000L)
        )
        assertEquals(2_100L, continued.snapshot.todaySteps)
    }

    @Test fun unknownDeltaAcrossDateBoundaryIsNotAssignedToToday() {
        val previous = StepCountSnapshot(today.minusDays(1), 7_000L, 100_000L, 1_000L)
        val update = requireNotNull(
            StepCountAccumulator.applyReading(previous, 101_500.0, today, 2_000L)
        )
        assertTrue(update.dateChanged)
        assertTrue(update.baselineStarted)
        assertEquals(0L, update.snapshot.todaySteps)
        assertEquals(101_500L, update.snapshot.lastSensorTotal)
    }

    @Test fun permissionGapBaselinePreservesAlreadyMeasuredTodaySteps() {
        val previous = StepCountSnapshot(today, 2_000L, null, 1_000L)
        val update = requireNotNull(
            StepCountAccumulator.applyReading(previous, 88_000.0, today, 2_000L)
        )
        assertTrue(update.baselineStarted)
        assertEquals(2_000L, update.snapshot.todaySteps)
    }

    @Test fun invalidSensorValuesAreRejectedWithoutChangingState() {
        assertNull(StepCountAccumulator.applyReading(StepCountSnapshot(), Double.NaN, today, 1L))
        assertNull(StepCountAccumulator.applyReading(StepCountSnapshot(), -1.0, today, 1L))
        assertNull(StepCountAccumulator.applyReading(StepCountSnapshot(), Double.POSITIVE_INFINITY, today, 1L))
    }

    @Test fun malformedPersistedNumbersAreSanitized() {
        val corrupted = StepCountSnapshot(
            date = today,
            todaySteps = -10L,
            lastSensorTotal = -20L,
            lastUpdatedAtMillis = -30L
        ).sanitized()
        assertEquals(0L, corrupted.todaySteps)
        assertNull(corrupted.lastSensorTotal)
        assertNull(corrupted.lastUpdatedAtMillis)
    }

    @Test fun veryLargeTotalsDoNotOverflowTodaySteps() {
        val previous = StepCountSnapshot(today, Long.MAX_VALUE - 2L, 10L, 1L)
        val update = requireNotNull(
            StepCountAccumulator.applyReading(previous, 20.0, today, 2L)
        )
        assertEquals(Long.MAX_VALUE, update.snapshot.todaySteps)
    }

    @Test fun accessPolicySeparatesPermissionAndSensorStates() {
        val missing = StepCounterAccessPolicy.resolve(false, false, false, false)
        val firstUse = StepCounterAccessPolicy.resolve(true, false, false, false)
        val denied = StepCounterAccessPolicy.resolve(true, false, true, true)
        val permanent = StepCounterAccessPolicy.resolve(true, false, true, false)
        val granted = StepCounterAccessPolicy.resolve(true, true, true, false)

        assertEquals(StepCounterStatus.SENSOR_UNAVAILABLE, missing.status)
        assertEquals(StepCounterStatus.PERMISSION_REQUIRED, firstUse.status)
        assertEquals(StepCounterAction.REQUEST_PERMISSION, firstUse.action)
        assertEquals(StepCounterAction.REQUEST_PERMISSION, denied.action)
        assertEquals(StepCounterAction.OPEN_SETTINGS, permanent.action)
        assertEquals(StepCounterStatus.LOADING, granted.status)
        assertFalse(granted.action != StepCounterAction.NONE)
    }
}
