package com.example.healthcare.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.TargetMode

/**
 * 특정 날짜부터 적용되는 에너지 기준입니다.
 * 날짜별 한 건만 유지해 같은 날 연속 저장으로 이력이 중복되지 않게 합니다.
 */
@Entity(
    tableName = "energy_profile_history",
    indices = [Index(value = ["effectiveFromDate"], unique = true)]
)
data class EnergyProfileHistory(
    @PrimaryKey(autoGenerate = true) val profileId: Long = 0,
    val basalMetabolicRateKcal: Int,
    val activityLevelCode: ActivityLevel,
    val palMultiplier: Double,
    val targetMode: TargetMode,
    val effectiveFromDate: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
