package com.example.healthcare.data

import android.content.Context
import androidx.core.content.edit
import com.example.healthcare.domain.StepCountSnapshot
import java.time.LocalDate

interface StepCountPersistence {
    fun read(): StepCountSnapshot
    fun write(snapshot: StepCountSnapshot)
    fun wasPermissionRequested(): Boolean
    fun markPermissionRequested()
    fun clearBaselinePreservingToday()
}

/** Room과 독립된 센서 기준값 저장소입니다. 위치나 원시 센서 이력은 저장하지 않습니다. */
class StepCountStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME
) : StepCountPersistence {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun read(): StepCountSnapshot = runCatching {
        val epochDay = preferences.getLong(KEY_DATE, NO_VALUE)
        val date = epochDay.takeIf { it != NO_VALUE }
            ?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
        StepCountSnapshot(
            date = date,
            todaySteps = preferences.getLong(KEY_TODAY_STEPS, 0L),
            lastSensorTotal = preferences.getLong(KEY_LAST_SENSOR_TOTAL, NO_VALUE)
                .takeIf { it != NO_VALUE },
            lastUpdatedAtMillis = preferences.getLong(KEY_LAST_UPDATED, NO_VALUE)
                .takeIf { it != NO_VALUE }
        ).sanitized()
    }.getOrElse { StepCountSnapshot() }

    override fun write(snapshot: StepCountSnapshot) {
        val safe = snapshot.sanitized()
        preferences.edit(commit = true) {
            if (safe.date == null) remove(KEY_DATE) else putLong(KEY_DATE, safe.date.toEpochDay())
            putLong(KEY_TODAY_STEPS, safe.todaySteps)
            if (safe.lastSensorTotal == null) remove(KEY_LAST_SENSOR_TOTAL)
            else putLong(KEY_LAST_SENSOR_TOTAL, safe.lastSensorTotal)
            if (safe.lastUpdatedAtMillis == null) remove(KEY_LAST_UPDATED)
            else putLong(KEY_LAST_UPDATED, safe.lastUpdatedAtMillis)
        }
    }

    override fun wasPermissionRequested(): Boolean =
        runCatching { preferences.getBoolean(KEY_PERMISSION_REQUESTED, false) }.getOrDefault(false)

    override fun markPermissionRequested() {
        preferences.edit(commit = true) { putBoolean(KEY_PERMISSION_REQUESTED, true) }
    }

    override fun clearBaselinePreservingToday() {
        val current = read()
        if (current.lastSensorTotal != null) write(current.copy(lastSensorTotal = null))
    }

    private companion object {
        const val PREFERENCES_NAME = "step_counter_state"
        const val KEY_DATE = "date_epoch_day"
        const val KEY_TODAY_STEPS = "today_steps"
        const val KEY_LAST_SENSOR_TOTAL = "last_sensor_total"
        const val KEY_LAST_UPDATED = "last_updated_at"
        const val KEY_PERMISSION_REQUESTED = "permission_requested"
        const val NO_VALUE = -1L
    }
}
