package com.example.healthcare.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.healthcare.HealthcareApplication
import java.util.concurrent.TimeUnit

class FoodDataUpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? HealthcareApplication ?: return Result.failure()
        val state = app.foodDataUpdateCoordinator.checkNow()
        return if (state.status == FoodDataUpdateStatus.FAILED) Result.retry() else Result.success()
    }
}

object FoodDataUpdateScheduler {
    private const val PERIODIC_NAME = "official-food-data-periodic-check"
    private const val STARTUP_NAME = "official-food-data-startup-check"
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context, lastCheckedAt: Long?) {
        val manager = WorkManager.getInstance(context)
        val periodic = PeriodicWorkRequestBuilder<FoodDataUpdateWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        manager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic)
        if (lastCheckedAt == null || System.currentTimeMillis() - lastCheckedAt >= STALE_AFTER_MILLIS) {
            manager.enqueueUniqueWork(
                STARTUP_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FoodDataUpdateWorker>().setConstraints(constraints).build()
            )
        }
    }

    private const val STALE_AFTER_MILLIS = 24L * 60L * 60L * 1000L
}
