package com.example.healthcare.domain

import com.example.healthcare.data.entity.MealRecord

data class RecordCompletion(val record: MealRecord, val edited: Boolean = false) {
    val title get() = if (edited) "수정했어요" else "기록했어요"
    val amountLabel: String? get() = RecordedAmountSnapshot.from(record)?.label
        ?: record.portionDisplayLabel?.takeIf(String::isNotBlank)
        ?: record.servingAmount?.takeIf { it.isFinite() && it > 0 }?.let {
            record.servingUnit?.takeIf(String::isNotBlank)?.let { unit -> "${RecordedAmountSnapshot.format(it)}$unit" }
        }
    val calorieLabel: String get() = record.calories.takeIf { it > 0 }?.let { "$it kcal" }
        ?: "칼로리 정보 없음"
}
