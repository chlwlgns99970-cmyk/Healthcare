package com.example.healthcare.domain

import kotlin.math.roundToInt

data class WalkingEnergyEstimate(
    val steps: Long,
    val estimatedStepLengthMeters: Double,
    val estimatedDistanceKm: Double,
    val estimatedNetKcal: Double
) {
    val estimatedKcalPerStep: Double
        get() = if (steps > 0L) estimatedNetKcal / steps else 0.0

    val roundedNetKcal: Int
        get() = estimatedNetKcal.roundToInt().coerceAtLeast(0)
}

/**
 * A deliberately simple walking estimate for days where GPS distance and pace are unavailable.
 *
 * Step length uses the height-based pedometer convention (height × 0.415 for men and × 0.413
 * for women) discussed in Guest et al., J Forensic Leg Med. 2017;52:46-55,
 * https://doi.org/10.1016/j.jflm.2017.08.006.
 *
 * Energy is the additional (net-of-resting) walking cost. Ortega et al. measured a mean net
 * cost of 2.17 J·kg⁻¹·m⁻¹ at 1.34 m/s in adults under 70 and showed substantial individual
 * variation: https://pmc.ncbi.nlm.nih.gov/articles/PMC5867372/.
 * It must therefore be presented as an estimate, never added to BMR × PAL or an intake target.
 */
object WalkingEnergyCalculator {
    private const val MALE_STEP_LENGTH_FACTOR = 0.415
    private const val FEMALE_STEP_LENGTH_FACTOR = 0.413
    private const val NET_WALKING_COST_J_PER_KG_M = 2.17
    private const val JOULES_PER_KILOCALORIE = 4_184.0

    fun estimate(steps: Long, profile: BodyProfile?): WalkingEnergyEstimate? {
        if (profile == null || steps < 0L) return null
        val factor = when (profile.sex) {
            BodySex.MALE -> MALE_STEP_LENGTH_FACTOR
            BodySex.FEMALE -> FEMALE_STEP_LENGTH_FACTOR
        }
        val stepLengthMeters = profile.heightCm * factor / 100.0
        val distanceMeters = steps.toDouble() * stepLengthMeters
        val netKcal = NET_WALKING_COST_J_PER_KG_M * profile.weightKg * distanceMeters /
            JOULES_PER_KILOCALORIE
        if (!stepLengthMeters.isFinite() || !distanceMeters.isFinite() || !netKcal.isFinite()) {
            return null
        }
        return WalkingEnergyEstimate(
            steps = steps,
            estimatedStepLengthMeters = stepLengthMeters,
            estimatedDistanceKm = distanceMeters / 1_000.0,
            estimatedNetKcal = netKcal.coerceAtLeast(0.0)
        )
    }
}
