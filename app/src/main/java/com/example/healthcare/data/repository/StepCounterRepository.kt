package com.example.healthcare.data.repository

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.healthcare.data.StepCountPersistence
import com.example.healthcare.data.StepCountStore
import com.example.healthcare.domain.StepCountAccumulator
import com.example.healthcare.domain.StepCounterAccessPolicy
import com.example.healthcare.domain.StepCounterAction
import com.example.healthcare.domain.StepCounterStatus
import com.example.healthcare.domain.StepCounterUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Event-based TYPE_STEP_COUNTER bridge. It runs only while the dashboard is visible. */
class StepCounterRepository(
    context: Context,
    private val persistence: StepCountPersistence = StepCountStore(context),
    private val dateProvider: () -> LocalDate = LocalDate::now,
    private val timeProvider: () -> Long = System::currentTimeMillis
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var listening = false

    private val _uiState = MutableStateFlow(
        StepCounterAccessPolicy.resolve(
            sensorAvailable = sensor != null,
            permissionGranted = false,
            permissionRequested = persistence.wasPermissionRequested(),
            canRequestAgain = false
        )
    )
    val uiState: StateFlow<StepCounterUiState> = _uiState.asStateFlow()

    val isSensorAvailable: Boolean get() = sensor != null
    val wasPermissionRequested: Boolean get() = persistence.wasPermissionRequested()

    fun markPermissionRequested() = persistence.markPermissionRequested()

    @Synchronized
    fun refreshAccess(permissionGranted: Boolean, canRequestAgain: Boolean) {
        if (sensor == null) {
            stopListening()
            _uiState.value = StepCounterUiState(StepCounterStatus.SENSOR_UNAVAILABLE)
            return
        }
        if (!permissionGranted) {
            stopListening()
            // Do not attribute steps observed while permission was unavailable to the current day.
            persistence.clearBaselinePreservingToday()
            _uiState.value = StepCounterAccessPolicy.resolve(
                sensorAvailable = true,
                permissionGranted = false,
                permissionRequested = persistence.wasPermissionRequested(),
                canRequestAgain = canRequestAgain
            )
            return
        }
        startListening()
    }

    @Synchronized
    private fun startListening() {
        if (listening) return
        val stepSensor = sensor ?: run {
            _uiState.value = StepCounterUiState(StepCounterStatus.SENSOR_UNAVAILABLE)
            return
        }
        val stored = persistence.read()
        val today = dateProvider()
        _uiState.value = if (stored.date == today && stored.lastSensorTotal != null) {
            StepCounterUiState(
                status = StepCounterStatus.AVAILABLE,
                todaySteps = stored.todaySteps,
                lastUpdatedAtMillis = stored.lastUpdatedAtMillis
            )
        } else {
            StepCounterUiState(
                status = StepCounterStatus.NO_BASELINE,
                todaySteps = stored.todaySteps.takeIf { stored.date == today } ?: 0L,
                message = "첫 센서 값을 기준으로 지금부터 측정해요."
            )
        }
        listening = runCatching {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }.getOrDefault(false)
        if (!listening) {
            _uiState.value = StepCounterUiState(
                status = StepCounterStatus.ERROR,
                message = "걸음 수 센서를 시작하지 못했어요."
            )
        }
    }

    @Synchronized
    fun stopListening() {
        if (!listening) return
        sensorManager.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!listening || event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val rawTotal = event.values.firstOrNull()?.toDouble() ?: return
        val update = StepCountAccumulator.applyReading(
            previous = persistence.read(),
            sensorTotal = rawTotal,
            today = dateProvider(),
            nowMillis = timeProvider()
        )
        if (update == null) {
            _uiState.value = StepCounterUiState(
                status = StepCounterStatus.ERROR,
                message = "걸음 수 센서 값이 올바르지 않아요."
            )
            return
        }
        persistence.write(update.snapshot)
        val message = when {
            update.dateChanged -> "오늘 첫 센서 값부터 측정해요."
            update.sensorReset -> "센서가 다시 시작되어 기존 오늘 걸음에 이어 측정해요."
            update.baselineStarted -> "지금부터 걸음 수를 측정해요."
            else -> null
        }
        _uiState.value = StepCounterUiState(
            status = StepCounterStatus.AVAILABLE,
            todaySteps = update.snapshot.todaySteps,
            lastUpdatedAtMillis = update.snapshot.lastUpdatedAtMillis,
            action = StepCounterAction.NONE,
            message = message
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
