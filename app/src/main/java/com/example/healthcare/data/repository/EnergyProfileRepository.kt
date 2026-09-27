package com.example.healthcare.data.repository

import com.example.healthcare.data.dao.EnergyProfileDao
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.TargetMode
import kotlinx.coroutines.flow.Flow

open class EnergyProfileRepository(private val energyProfileDao: EnergyProfileDao) {
    open val allProfiles: Flow<List<EnergyProfileHistory>> = energyProfileDao.getAllProfiles()

    open fun getProfileForDate(date: String): Flow<EnergyProfileHistory?> =
        energyProfileDao.getProfileForDate(date)

    open suspend fun saveProfile(
        basalMetabolicRateKcal: Int,
        activityLevel: ActivityLevel,
        palMultiplier: Double,
        targetMode: TargetMode,
        effectiveFromDate: String,
        nowEpochMillis: Long = System.currentTimeMillis()
    ) {
        val existing = energyProfileDao.getProfileStartingOn(effectiveFromDate)
        energyProfileDao.upsertProfile(
            EnergyProfileHistory(
                profileId = existing?.profileId ?: 0,
                basalMetabolicRateKcal = basalMetabolicRateKcal,
                activityLevelCode = activityLevel,
                palMultiplier = palMultiplier,
                targetMode = targetMode,
                effectiveFromDate = effectiveFromDate,
                createdAt = existing?.createdAt ?: nowEpochMillis,
                updatedAt = nowEpochMillis
            )
        )
    }
}
