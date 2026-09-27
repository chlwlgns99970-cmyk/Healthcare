package com.example.healthcare.domain

import com.example.healthcare.data.entity.UserMealPreference
import com.example.healthcare.data.model.MealType
import java.time.LocalTime
import kotlin.math.floor
import kotlin.math.max

data class MealBudget(
    val mealType: MealType,
    val calories: Int
)

data class MealRedistribution(
    val remainingCalories: Int,
    val budgets: List<MealBudget>,
    val targetExceeded: Boolean
)

/** 사용자 설정 비율을 사용해 목표를 정수 kcal로 배분합니다. */
object MealCoachCalculator {
    fun enabledRatios(preference: UserMealPreference): LinkedHashMap<MealType, Int> = linkedMapOf<MealType, Int>().apply {
        if (preference.breakfastEnabled) put(MealType.BREAKFAST, preference.breakfastRatio)
        if (preference.lunchEnabled) put(MealType.LUNCH, preference.lunchRatio)
        if (preference.dinnerEnabled) put(MealType.DINNER, preference.dinnerRatio)
        if (preference.snackEnabled) put(MealType.SNACK, preference.snackRatio)
    }

    fun validateRatios(preference: UserMealPreference): Boolean {
        val ratios = enabledRatios(preference)
        return ratios.isNotEmpty() && ratios.values.all { it >= 0 } && ratios.values.sum() == 100
    }

    fun allocate(targetCalories: Int, preference: UserMealPreference): List<MealBudget> {
        require(targetCalories >= 0) { "targetCalories must be non-negative" }
        val ratios = enabledRatios(preference)
        require(ratios.isNotEmpty()) { "At least one meal must be enabled" }
        require(ratios.values.all { it >= 0 } && ratios.values.sum() > 0) { "Ratios must be positive" }
        return allocateByWeights(targetCalories, ratios)
    }

    fun redistribute(
        targetCalories: Int,
        currentIntakeCalories: Int,
        remainingMeals: List<MealType>,
        preference: UserMealPreference
    ): MealRedistribution {
        val remaining = max(0, targetCalories - currentIntakeCalories)
        val allRatios = enabledRatios(preference)
        val weights = linkedMapOf<MealType, Int>()
        remainingMeals.distinct().forEach { type ->
            allRatios[type]?.takeIf { it > 0 }?.let { weights[type] = it }
        }
        val budgets = if (remaining > 0 && weights.isNotEmpty()) allocateByWeights(remaining, weights) else emptyList()
        return MealRedistribution(
            remainingCalories = remaining,
            budgets = budgets,
            targetExceeded = currentIntakeCalories > targetCalories
        )
    }

    /** 다음 한 끼의 원래 배분만 제안합니다. 건너뛴 식사의 몫을 자동 이월하지 않습니다. */
    fun nextMealBudget(
        targetCalories: Int,
        currentIntakeCalories: Int,
        mealType: MealType,
        preference: UserMealPreference
    ): Int? {
        val allocation = allocate(targetCalories.coerceAtLeast(0), preference)
            .firstOrNull { it.mealType == mealType }?.calories ?: return null
        val remaining = (targetCalories - currentIntakeCalories).coerceAtLeast(0)
        return if (remaining > 0) allocation.coerceAtMost(remaining) else allocation
    }

    fun remainingMeals(now: LocalTime, preference: UserMealPreference): List<MealType> {
        val scheduled = listOf(
            MealType.BREAKFAST to LocalTime.of(9, 30),
            MealType.LUNCH to LocalTime.of(14, 0),
            MealType.SNACK to LocalTime.of(17, 0),
            MealType.DINNER to LocalTime.of(21, 0)
        )
        val enabled = enabledRatios(preference).keys
        return scheduled.filter { (type, endTime) -> type in enabled && now.isBefore(endTime) }.map { it.first }
    }

    private fun allocateByWeights(total: Int, weights: LinkedHashMap<MealType, Int>): List<MealBudget> {
        val weightSum = weights.values.sum().toDouble()
        require(weightSum > 0.0) { "Weight sum must be positive" }
        var assigned = 0
        val entries = weights.entries.toList()
        return entries.mapIndexed { index, entry ->
            val calories = if (index == entries.lastIndex) {
                total - assigned
            } else {
                floor(total * entry.value / weightSum).toInt().also { assigned += it }
            }
            MealBudget(entry.key, calories)
        }
    }
}
