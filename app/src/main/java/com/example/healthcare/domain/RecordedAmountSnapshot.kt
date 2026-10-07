package com.example.healthcare.domain

import com.example.healthcare.data.entity.MealRecord

/** Portable conversion stored in the existing portion fields, separate from the basis amount. */
data class RecordedAmountSnapshot(val quantity: Double, val unit: String, val basisPerUnit: Double, val basisUnit: String) {
    val presetId: String get() = "amount/$unit/$basisPerUnit/$quantity"
    val label: String get() = "${format(quantity)}$unit"
    fun portion(evidence: String, type: PortionEstimationType = PortionEstimationType.MANUAL_AMOUNT) =
        PortionPreset(presetId, label, quantity * basisPerUnit, basisUnit,
            type, evidence, "저장한 섭취 단위 기준")
    companion object {
        fun from(record: MealRecord): RecordedAmountSnapshot? {
            val parts = record.portionPresetId?.split('/') ?: return null
            if (parts.size != 4 || parts[0] != "amount") return null
            val factor = parts[2].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
            val quantity = parts[3].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
            val basis = record.servingUnit?.takeIf { it.isNotBlank() } ?: return null
            val amount = record.servingAmount ?: return null
            if (kotlin.math.abs(amount - factor * quantity) > 0.0001 * kotlin.math.max(1.0, amount)) return null
            return RecordedAmountSnapshot(quantity, parts[1], factor, basis)
        }
        fun format(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else
            java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }
}
