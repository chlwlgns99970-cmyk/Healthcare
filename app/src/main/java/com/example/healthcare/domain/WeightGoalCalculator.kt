package com.example.healthcare.domain

import kotlin.math.abs
import kotlin.math.roundToInt

data class WeightGoalCalculation(
    val currentWeightKg: Double,
    val targetWeightKg: Double,
    val durationWeeks: Int,
    val lossKg: Double,
    val weeklyLossKg: Double,
    val dailyDeficitKcal: Int,
    val maintenanceKcal: Int,
    val proposedIntakeKcal: Int,
    val canApply: Boolean,
    val guidance: String
)

enum class IntakePaceStatus {
    DEFICIT,
    MAINTENANCE,
    SURPLUS,
    TOO_LOW
}

data class IntakePaceCalculation(
    val maintenanceKcal: Int,
    val bmrKcal: Int,
    val targetIntakeKcal: Int,
    /** 섭취 목표 - 유지 예상. 음수는 적자, 0은 유지, 양수는 잉여를 뜻합니다. */
    val dailyBalanceKcal: Int,
    val estimatedDaysForOneKg: Int?,
    val status: IntakePaceStatus,
    val canApply: Boolean,
    val guidance: String
)

/**
 * 7,700 kcal/kg는 결과를 보장하지 않는 정적 단순 환산입니다. 실제 체중 변화는 대사 적응 등으로
 * 달라질 수 있습니다(NIDDK Body Weight Planner). 일반 안내 기준은 CDC의 주당 약 1~2lb와
 * NHLBI의 일 500~1,000 kcal 적자 범위를 보수적으로 사용합니다.
 *
 * https://www.cdc.gov/healthy-weight-growth/losing-weight/index.html
 * https://www.nhlbi.nih.gov/files/docs/guidelines/ob_gdlns
 * https://www.niddk.nih.gov/research-funding/at-niddk/labs-branches/
 * laboratory-biological-modeling/integrative-physiology-section/research/body-weight-planner
 */
object WeightGoalCalculator {
    const val MAX_GENERAL_WEEKLY_LOSS_KG = 0.9
    const val MAX_GENERAL_DAILY_DEFICIT_KCAL = 1000

    fun calculate(
        currentWeightKg: Double,
        targetWeightKg: Double,
        durationWeeks: Int,
        maintenanceKcal: Int,
        bmrKcal: Int
    ): Result<WeightGoalCalculation> = runCatching {
        require(currentWeightKg.isFinite() && currentWeightKg > 0.0) { "현재 몸무게를 먼저 저장해 주세요." }
        require(targetWeightKg.isFinite() && targetWeightKg > 0.0) { "목표 몸무게를 올바르게 입력해 주세요." }
        require(targetWeightKg < currentWeightKg) { "감량 목표는 현재 몸무게보다 낮게 입력해 주세요." }
        require(durationWeeks in 1..520) { "목표 기간은 1주 이상으로 입력해 주세요." }
        require(maintenanceKcal > 0 && bmrKcal > 0) { "기초대사량과 활동 수준을 먼저 저장해 주세요." }

        val lossKg = currentWeightKg - targetWeightKg
        val dailyDeficitExact = lossKg * EnergyBalanceCalculator.ENERGY_EQUIVALENT_KCAL_PER_KG /
            (durationWeeks * 7.0)
        require(dailyDeficitExact.isFinite() && dailyDeficitExact <= Int.MAX_VALUE) {
            "계산 가능한 목표 범위를 벗어났습니다."
        }
        val deficit = dailyDeficitExact.roundToInt()
        val proposed = maintenanceKcal - deficit
        val weeklyLoss = lossKg / durationWeeks
        val paceTooFast = weeklyLoss > MAX_GENERAL_WEEKLY_LOSS_KG
        val deficitTooLarge = deficit > MAX_GENERAL_DAILY_DEFICIT_KCAL
        val belowBmr = proposed < bmrKcal
        val canApply = !paceTooFast && !deficitTooLarge && proposed > 0 && !belowBmr
        val guidance = when {
            proposed <= 0 -> "이 기간에는 계산상 섭취 목표가 0 이하가 됩니다. 목표 기간을 크게 늘려 주세요."
            belowBmr -> "계산된 섭취량이 기초대사량보다 낮습니다. 이 앱에서는 적용할 수 없으니 목표 기간을 늘려 주세요."
            paceTooFast || deficitTooLarge -> "이 기간에는 필요한 에너지 적자가 매우 큽니다. 더 긴 기간으로 설정하면 하루 목표를 현실적으로 조정할 수 있습니다."
            else -> "일반적인 점진적 감량 범위 안의 단순 계산입니다. 실제 변화는 개인마다 다를 수 있습니다."
        }
        WeightGoalCalculation(
            currentWeightKg = currentWeightKg,
            targetWeightKg = targetWeightKg,
            durationWeeks = durationWeeks,
            lossKg = lossKg,
            weeklyLossKg = weeklyLoss,
            dailyDeficitKcal = deficit,
            maintenanceKcal = maintenanceKcal,
            proposedIntakeKcal = proposed,
            canApply = canApply,
            guidance = guidance
        )
    }

    /**
     * 사용자가 정한 하루 섭취량을 현재 유지 예상과 비교해 1kg 단순 환산 기간을 계산합니다.
     * 실제 체중 변화 예측이 아니라 기존 7,700 kcal/kg 기준을 반대 방향으로 적용한 값입니다.
     */
    fun calculateIntakePace(
        maintenanceKcal: Int,
        bmrKcal: Int,
        targetIntakeKcal: Int
    ): Result<IntakePaceCalculation> = runCatching {
        require(maintenanceKcal > 0 && bmrKcal > 0) { "기초대사량과 활동 수준을 먼저 저장해 주세요." }
        require(targetIntakeKcal > 0) { "하루 섭취 목표를 올바르게 입력해 주세요." }

        val dailyBalance = targetIntakeKcal - maintenanceKcal
        val belowBmr = targetIntakeKcal < bmrKcal
        val deficitTooLarge = dailyBalance < -MAX_GENERAL_DAILY_DEFICIT_KCAL
        val status = when {
            belowBmr || deficitTooLarge -> IntakePaceStatus.TOO_LOW
            dailyBalance < 0 -> IntakePaceStatus.DEFICIT
            dailyBalance == 0 -> IntakePaceStatus.MAINTENANCE
            else -> IntakePaceStatus.SURPLUS
        }
        val days = if (status == IntakePaceStatus.DEFICIT || status == IntakePaceStatus.SURPLUS) {
            (EnergyBalanceCalculator.ENERGY_EQUIVALENT_KCAL_PER_KG / abs(dailyBalance)).roundToInt()
                .coerceAtLeast(1)
        } else null
        val guidance = when (status) {
            IntakePaceStatus.DEFICIT ->
                "현재 유지 예상 기준으로 하루 약 ${abs(dailyBalance)} kcal가 적은 단순 계산입니다. 실제 체중 변화는 달라질 수 있습니다."
            IntakePaceStatus.MAINTENANCE ->
                "현재 설정은 유지 예상과 같아요. 단순 계산상 체중 변화 방향을 만드는 에너지 차이가 없습니다."
            IntakePaceStatus.SURPLUS ->
                "현재 유지 예상 기준으로 하루 약 ${dailyBalance} kcal가 많아요. 실제 체중 변화는 달라질 수 있습니다."
            IntakePaceStatus.TOO_LOW ->
                "현재 신체정보 기준으로 너무 낮은 섭취 목표예요. 이 값은 목표로 적용할 수 없습니다."
        }
        IntakePaceCalculation(
            maintenanceKcal = maintenanceKcal,
            bmrKcal = bmrKcal,
            targetIntakeKcal = targetIntakeKcal,
            dailyBalanceKcal = dailyBalance,
            estimatedDaysForOneKg = days,
            status = status,
            canApply = status != IntakePaceStatus.TOO_LOW,
            guidance = guidance
        )
    }
}
