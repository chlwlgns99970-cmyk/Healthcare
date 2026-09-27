package com.example.healthcare.domain

import kotlin.math.ceil

/** https://pacompendium.com/adult-compendium/ 의 대표 강도값을 사용한 참고용 추정. */
enum class ExerciseActivity(
    val displayName: String,
    val intensity: String,
    val description: String,
    val met: Double
) {
    WALK("걷기", "가볍게", "평지에서 편안하게 걷는 정도예요.", 3.5),
    BRISK_WALK("빠르게 걷기", "보통", "대화는 가능하지만 평소보다 빠르게 걷는 정도예요.", 4.8),
    LIGHT_JOG("가벼운 조깅", "다소 높음", "자신에게 편한 속도로 천천히 뛰는 정도예요.", 7.5),
    BICYCLE("자전거", "가볍게", "무리하지 않는 느긋한 속도로 자전거를 타는 정도예요.", 3.5),
    STAIRS("계단 오르기", "보통", "천천히 계단을 오르는 정도예요. 균형과 호흡에 주의하세요.", 4.5),
    HOME_EXERCISE("집에서 하는 가벼운 운동", "가볍게", "집에서 편한 속도로 몸을 움직이는 정도예요.", 3.8)
}

data class ExerciseEstimate(val minutes: Long, val estimatedKcal: Int)

object ExerciseCoachCalculator {
    fun validateWeight(input: String): Double? {
        val weight = input.trim().toDoubleOrNull()
        return weight?.takeIf { it.isFinite() && it in 20.0..300.0 }
    }

    fun targetExcess(intakeCalories: Int, targetCalories: Int): Int =
        (intakeCalories.toLong() - targetCalories.toLong()).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    fun estimate(targetCalories: Int, weightKg: Double, activity: ExerciseActivity): ExerciseEstimate? {
        if (targetCalories <= 0 || !weightKg.isFinite() || weightKg !in 20.0..300.0) return null

        // Compendium의 표준 1 MET ≈ 1 kcal/kg/시간. 안정 시 1 MET를 빼 추가 소모량을 추정한다.
        val kcalPerMinute = (activity.met - 1.0) * weightKg / 60.0
        if (!kcalPerMinute.isFinite() || kcalPerMinute <= 0.0) return null
        val rawMinutes = targetCalories.toDouble() / kcalPerMinute
        if (!rawMinutes.isFinite() || rawMinutes <= 0.0) return null
        val roundedMinutes = (ceil(rawMinutes / 5.0) * 5.0)
            .coerceAtMost(Long.MAX_VALUE.toDouble())
            .toLong()
            .coerceAtLeast(5L)
        return ExerciseEstimate(roundedMinutes, targetCalories)
    }
}
