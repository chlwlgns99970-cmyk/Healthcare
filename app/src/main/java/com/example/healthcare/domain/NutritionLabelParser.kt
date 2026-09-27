package com.example.healthcare.domain

import kotlin.math.roundToInt

data class NutritionBasisCandidate(
    val referenceAmount: Double,
    val unit: String,
    val energyKcal: Double,
    val evidence: String
)

data class NutritionLabelParseResult(
    val productNameCandidate: String? = null,
    val totalAmount: Double? = null,
    val totalAmountUnit: String? = null,
    val servingAmount: Double? = null,
    val servingAmountUnit: String? = null,
    val servingCount: Double? = null,
    val energyCandidates: List<NutritionBasisCandidate> = emptyList(),
    val carbohydrateGrams: Double? = null,
    val proteinGrams: Double? = null,
    val fatGrams: Double? = null,
    val sodiumMilligrams: Double? = null,
    val rawText: String = ""
)

/** OCR 원문에서 충돌 가능한 열량 기준을 각각 보존하는 보수적인 파서입니다. */
object NutritionLabelParser {
    private val amountUnit = "(g|그램|ml|mL|ML|㎖)"
    private val amountEnergyPattern = Regex(
        "(\\d+(?:[.,]\\d+)?)\\s*$amountUnit\\s*(?:당|기준)?[^0-9]{0,20}(\\d+(?:[.,]\\d+)?)\\s*(?:kcal|㎉)",
        RegexOption.IGNORE_CASE
    )
    private val energyOnlyPattern = Regex("(?:열량|에너지)\\s*[:：]?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:kcal|㎉)", RegexOption.IGNORE_CASE)
    private val totalAmountPattern = Regex("총\\s*(?:내용량|중량)\\s*[:：]?\\s*(\\d+(?:[.,]\\d+)?)\\s*$amountUnit", RegexOption.IGNORE_CASE)
    private val servingAmountPattern = Regex("(?:1\\s*회\\s*)?제공량\\s*[:：]?\\s*(\\d+(?:[.,]\\d+)?)\\s*$amountUnit", RegexOption.IGNORE_CASE)
    private val servingCountPattern = Regex("총\\s*(\\d+(?:[.,]\\d+)?)\\s*회\\s*제공", RegexOption.IGNORE_CASE)

    fun parse(text: String): NutritionLabelParseResult {
        val normalized = text.replace('\u00A0', ' ').replace("ＫＣＡＬ", "kcal", ignoreCase = true)
        val total = totalAmountPattern.find(normalized)
        val serving = servingAmountPattern.find(normalized)
        val servingCount = servingCountPattern.find(normalized)?.groupValues?.getOrNull(1)?.number()

        val candidates = buildList {
            amountEnergyPattern.findAll(normalized).forEach { match ->
                val amount = match.groupValues[1].number() ?: return@forEach
                val unit = canonicalUnit(match.groupValues[2])
                val kcal = match.groupValues[3].number() ?: return@forEach
                if (amount > 0.0 && kcal > 0.0) {
                    add(NutritionBasisCandidate(amount, unit, kcal, match.value.trim()))
                }
            }
            if (isEmpty()) {
                val energy = energyOnlyPattern.find(normalized)?.groupValues?.getOrNull(1)?.number()
                val servingAmount = serving?.groupValues?.getOrNull(1)?.number()
                val servingUnit = serving?.groupValues?.getOrNull(2)?.let(::canonicalUnit)
                if (energy != null && servingAmount != null && servingUnit != null && energy > 0.0) {
                    add(
                        NutritionBasisCandidate(
                            servingAmount,
                            servingUnit,
                            energy,
                            "1회 제공량 기준 열량"
                        )
                    )
                }
            }
        }.distinctBy { Triple(it.referenceAmount, it.unit, it.energyKcal) }

        return NutritionLabelParseResult(
            totalAmount = total?.groupValues?.getOrNull(1)?.number(),
            totalAmountUnit = total?.groupValues?.getOrNull(2)?.let(::canonicalUnit),
            servingAmount = serving?.groupValues?.getOrNull(1)?.number(),
            servingAmountUnit = serving?.groupValues?.getOrNull(2)?.let(::canonicalUnit),
            servingCount = servingCount,
            energyCandidates = candidates,
            carbohydrateGrams = nutrientValue(normalized, "탄수화물", "g"),
            proteinGrams = nutrientValue(normalized, "단백질", "g"),
            fatGrams = nutrientValue(normalized, "지방", "g"),
            sodiumMilligrams = nutrientValue(normalized, "나트륨", "mg"),
            rawText = text
        )
    }

    fun calculateCalories(candidate: NutritionBasisCandidate, consumedAmount: Double): Int {
        require(candidate.referenceAmount > 0.0 && consumedAmount > 0.0)
        return (candidate.energyKcal * consumedAmount / candidate.referenceAmount).roundToInt()
    }

    private fun nutrientValue(text: String, label: String, unit: String): Double? {
        val pattern = Regex("$label\\s*[:：]?\\s*(\\d+(?:[.,]\\d+)?)\\s*$unit", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.getOrNull(1)?.number()
    }

    private fun String.number(): Double? = replace(',', '.').toDoubleOrNull()

    private fun canonicalUnit(value: String): String = when (value.lowercase()) {
        "g", "그램" -> "g"
        else -> "ml"
    }
}
