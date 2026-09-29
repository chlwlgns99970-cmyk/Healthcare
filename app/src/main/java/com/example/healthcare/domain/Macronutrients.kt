package com.example.healthcare.domain

import com.example.healthcare.data.entity.FoodItem
import java.util.Locale
import kotlin.math.abs

/**
 * 제공되지 않은 값은 null로 유지합니다. 일부 항목을 0g으로 간주해 합계를 과소 표시하지 않습니다.
 */
data class Macronutrients(
    val carbohydrateGrams: Double?,
    val proteinGrams: Double?,
    val fatGrams: Double?
) {
    fun scaled(ratio: Double): Macronutrients {
        if (!ratio.isFinite() || ratio < 0.0) return Unknown
        return Macronutrients(
            carbohydrateGrams?.times(ratio),
            proteinGrams?.times(ratio),
            fatGrams?.times(ratio)
        )
    }

    val hasAnyKnownValue: Boolean
        get() = carbohydrateGrams != null || proteinGrams != null || fatGrams != null

    companion object {
        val Unknown = Macronutrients(null, null, null)

        fun forFood(food: FoodItem, amount: Double): Macronutrients {
            if (!amount.isFinite() || amount < 0.0 ||
                !food.referenceAmount.isFinite() || food.referenceAmount <= 0.0
            ) return Unknown
            return Macronutrients(
                food.carbohydrateGrams,
                food.proteinGrams,
                food.fatGrams
            ).scaled(amount / food.referenceAmount)
        }

        /** 각 영양소는 모든 항목에 값이 있을 때만 합산합니다. */
        fun strictSum(items: Iterable<Macronutrients>): Macronutrients {
            val values = items.toList()
            if (values.isEmpty()) return Unknown
            return Macronutrients(
                values.map { it.carbohydrateGrams }.strictSum(),
                values.map { it.proteinGrams }.strictSum(),
                values.map { it.fatGrams }.strictSum()
            )
        }

        /**
         * 영양소별로 확인 가능한 값만 합산합니다. 어떤 기록의 한 영양소가 누락되어도 다른
         * 기록의 확인된 값은 유지하며, 해당 영양소가 전부 누락된 경우에만 null을 반환합니다.
         */
        fun knownSum(items: Iterable<Macronutrients>): Macronutrients {
            val values = items.toList()
            if (values.isEmpty()) return Unknown
            return Macronutrients(
                values.map { it.carbohydrateGrams }.knownSum(),
                values.map { it.proteinGrams }.knownSum(),
                values.map { it.fatGrams }.knownSum()
            )
        }

        private fun List<Double?>.strictSum(): Double? =
            if (all { it != null }) sumOf { requireNotNull(it) } else null

        private fun List<Double?>.knownSum(): Double? =
            filterNotNull().takeIf { it.isNotEmpty() }?.sum()
    }
}

object MacronutrientFormatter {
    fun grams(value: Double?): String = value?.takeIf { it.isFinite() && it >= 0.0 }?.let {
        val rounded = kotlin.math.round(it * 10.0) / 10.0
        if (abs(rounded - rounded.toLong()) < 0.0001) {
            "${rounded.toLong()}g"
        } else {
            String.format(Locale.KOREA, "%.1fg", rounded)
        }
    } ?: "정보 없음"
}
