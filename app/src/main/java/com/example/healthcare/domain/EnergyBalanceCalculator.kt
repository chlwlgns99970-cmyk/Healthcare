package com.example.healthcare.domain

import com.example.healthcare.data.model.TargetMode
import kotlin.math.roundToInt

/** 에너지 기준과 화면용 단순 체중 환산을 계산하는 순수 도메인 로직입니다. */
object EnergyBalanceCalculator {
    const val ENERGY_EQUIVALENT_KCAL_PER_KG = 7700.0
    const val MIN_CUSTOM_PAL = 1.40
    const val MAX_CUSTOM_PAL = 2.40
    const val MAX_BMR_DIGITS = 5

    fun calculateMaintenanceKcal(bmrKcal: Double, palMultiplier: Double): Double {
        requireValidNonNegative(bmrKcal, "기초대사량")
        requireValidNonNegative(palMultiplier, "PAL")
        require(bmrKcal > 0.0) { "기초대사량은 0보다 커야 합니다." }
        require(palMultiplier > 0.0) { "PAL은 0보다 커야 합니다." }
        val result = bmrKcal * palMultiplier
        require(result.isFinite() && result <= Int.MAX_VALUE.toDouble()) {
            "계산 가능한 범위를 벗어났습니다."
        }
        return result
    }

    fun roundKcal(value: Double): Int {
        requireValidNonNegative(value, "칼로리")
        require(value <= Int.MAX_VALUE.toDouble()) { "계산 가능한 범위를 벗어났습니다." }
        return value.roundToInt()
    }

    fun calculateDailySurplus(intakeKcal: Double, maintenanceKcal: Double): Double {
        requireValidNonNegative(intakeKcal, "섭취량")
        requireValidNonNegative(maintenanceKcal, "예상 유지 칼로리")
        return (intakeKcal - maintenanceKcal).coerceAtLeast(0.0)
    }

    fun calculateSimpleWeightEquivalentKg(dailySurplusKcal: Double, days: Int): Double {
        requireValidNonNegative(dailySurplusKcal, "초과 칼로리")
        require(days > 0) { "기간은 1일 이상이어야 합니다." }
        val result = dailySurplusKcal * days / ENERGY_EQUIVALENT_KCAL_PER_KG
        require(result.isFinite()) { "계산 가능한 범위를 벗어났습니다." }
        return result
    }

    fun resolveDailyTargetKcal(
        manualTargetKcal: Int,
        bmrKcal: Int,
        palMultiplier: Double,
        targetMode: TargetMode
    ): Int = when (targetMode) {
        TargetMode.MANUAL -> manualTargetKcal
        TargetMode.BMR -> bmrKcal
        TargetMode.MAINTENANCE -> roundKcal(
            calculateMaintenanceKcal(bmrKcal.toDouble(), palMultiplier)
        )
    }

    fun isValidCustomPal(value: Double): Boolean =
        value.isFinite() && value in MIN_CUSTOM_PAL..MAX_CUSTOM_PAL

    /** 의료 판정이 아니라 1~2자리 또는 5자리 입력의 오타 가능성만 안내합니다. */
    fun shouldReviewBmrInput(value: Int): Boolean = value in 1..99 || value >= 10_000

    private fun requireValidNonNegative(value: Double, label: String) {
        require(value.isFinite() && value >= 0.0) { "$label 값이 올바르지 않습니다." }
    }
}
