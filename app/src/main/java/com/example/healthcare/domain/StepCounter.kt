package com.example.healthcare.domain

import java.time.LocalDate

enum class StepCounterStatus {
    LOADING,
    AVAILABLE,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    SENSOR_UNAVAILABLE,
    NO_BASELINE,
    ERROR
}

enum class StepCounterAction {
    NONE,
    REQUEST_PERMISSION,
    OPEN_SETTINGS
}

data class StepCounterUiState(
    val status: StepCounterStatus = StepCounterStatus.LOADING,
    val todaySteps: Long = 0L,
    val lastUpdatedAtMillis: Long? = null,
    val action: StepCounterAction = StepCounterAction.NONE,
    val message: String? = null
)

data class StepCountSnapshot(
    val date: LocalDate? = null,
    val todaySteps: Long = 0L,
    val lastSensorTotal: Long? = null,
    val lastUpdatedAtMillis: Long? = null
) {
    fun sanitized(): StepCountSnapshot {
        val safeDate = date
        return copy(
            todaySteps = todaySteps.coerceAtLeast(0L),
            lastSensorTotal = lastSensorTotal?.takeIf { it >= 0L && safeDate != null },
            lastUpdatedAtMillis = lastUpdatedAtMillis?.takeIf { it >= 0L && safeDate != null }
        )
    }
}

data class StepCountUpdate(
    val snapshot: StepCountSnapshot,
    val baselineStarted: Boolean = false,
    val dateChanged: Boolean = false,
    val sensorReset: Boolean = false
)

/**
 * Converts the boot-relative sensor total into the locally observed steps for one day.
 * A delta that crosses an unobserved date boundary is deliberately not assigned to today.
 */
object StepCountAccumulator {
    fun applyReading(
        previous: StepCountSnapshot,
        sensorTotal: Double,
        today: LocalDate,
        nowMillis: Long
    ): StepCountUpdate? {
        if (!sensorTotal.isFinite() || sensorTotal < 0.0 || sensorTotal > Long.MAX_VALUE.toDouble()) {
            return null
        }
        val total = sensorTotal.toLong()
        val safePrevious = previous.sanitized()
        val dateChanged = safePrevious.date != null && safePrevious.date != today
        if (safePrevious.date != today) {
            return StepCountUpdate(
                snapshot = StepCountSnapshot(today, 0L, total, nowMillis.coerceAtLeast(0L)),
                baselineStarted = true,
                dateChanged = dateChanged
            )
        }

        val lastTotal = safePrevious.lastSensorTotal
        if (lastTotal == null) {
            return StepCountUpdate(
                snapshot = safePrevious.copy(
                    date = today,
                    lastSensorTotal = total,
                    lastUpdatedAtMillis = nowMillis.coerceAtLeast(0L)
                ),
                baselineStarted = true
            )
        }

        if (total < lastTotal) {
            return StepCountUpdate(
                snapshot = safePrevious.copy(
                    lastSensorTotal = total,
                    lastUpdatedAtMillis = nowMillis.coerceAtLeast(0L)
                ),
                sensorReset = true
            )
        }

        val delta = total - lastTotal
        val updatedSteps = if (Long.MAX_VALUE - safePrevious.todaySteps < delta) {
            Long.MAX_VALUE
        } else {
            safePrevious.todaySteps + delta
        }
        return StepCountUpdate(
            snapshot = safePrevious.copy(
                todaySteps = updatedSteps,
                lastSensorTotal = total,
                lastUpdatedAtMillis = nowMillis.coerceAtLeast(0L)
            )
        )
    }
}

object StepCounterAccessPolicy {
    fun resolve(
        sensorAvailable: Boolean,
        permissionGranted: Boolean,
        permissionRequested: Boolean,
        canRequestAgain: Boolean
    ): StepCounterUiState = when {
        !sensorAvailable -> StepCounterUiState(StepCounterStatus.SENSOR_UNAVAILABLE)
        permissionGranted -> StepCounterUiState(StepCounterStatus.LOADING)
        !permissionRequested -> StepCounterUiState(
            status = StepCounterStatus.PERMISSION_REQUIRED,
            action = StepCounterAction.REQUEST_PERMISSION
        )
        canRequestAgain -> StepCounterUiState(
            status = StepCounterStatus.PERMISSION_DENIED,
            action = StepCounterAction.REQUEST_PERMISSION
        )
        else -> StepCounterUiState(
            status = StepCounterStatus.PERMISSION_DENIED,
            action = StepCounterAction.OPEN_SETTINGS
        )
    }
}
