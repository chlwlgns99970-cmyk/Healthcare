package com.example.healthcare.domain

import com.example.healthcare.data.entity.FoodItem
import java.util.Locale
import kotlin.math.roundToInt

enum class NutritionQuality { COMPLETE, PARTIAL, CALORIES_ONLY, UNKNOWN }
enum class PortionQuality { OFFICIAL_SERVING, VERIFIED_CONVERSION, WEIGHT_ONLY, VOLUME_ONLY, UNRESOLVED }

/** One user-facing unit, expressed in the unchanged nutrition basis. No density is inferred. */
data class FoodAmountUnit(
    val unit: String,
    val basisAmountPerUnit: Double,
    val basisUnit: String,
    val evidence: String,
    val quality: PortionQuality
)

data class FoodAmountNutrition(
    val basisAmount: Double,
    val basisUnit: String,
    val calories: Int,
    val carbohydrateGrams: Double?,
    val proteinGrams: Double?,
    val fatGrams: Double?
)

/** Shared by every record entry point. Unknown values remain unknown, including zero-energy foods. */
object FoodAmountPolicy {
    const val MAX_BASIS_AMOUNT = 10_000.0
    const val MAX_CALORIES = 100_000.0
    private val amountPattern = Regex("^[0-9]+(?:[.,][0-9]+)?$")
    private val officialServingPattern = Regex(
        "(?:공식|검증된|확인된)\\s*(?:(?:HOT|ICE)\\s+)?(?:제공량\\s*)?([0-9]+(?:\\.[0-9]+)?)\\s*" +
            "(개|줄|봉|봉지|병|캔|팩|컵|잔|조각|인분|공기|장)\\s*[=(（(]?\\s*" +
            "([0-9]+(?:\\.[0-9]+)?)\\s*(g|ml)", RegexOption.IGNORE_CASE
    )

    fun parseAmount(text: String): Double? = text.trim().takeIf(amountPattern::matches)
        ?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= MAX_BASIS_AMOUNT }

    fun nutritionQuality(food: FoodItem): NutritionQuality {
        if (!usableNutrient(food.energyKcal)) return NutritionQuality.UNKNOWN
        val known = listOf(food.carbohydrateGrams, food.proteinGrams, food.fatGrams)
            .count { it != null && usableNutrient(it) }
        return when (known) {
            3 -> NutritionQuality.COMPLETE
            0 -> NutritionQuality.CALORIES_ONLY
            else -> NutritionQuality.PARTIAL
        }
    }

    fun portionQuality(food: FoodItem): PortionQuality = defaultChoice(food)?.quality ?: PortionQuality.UNRESOLVED
    fun defaultChoice(food: FoodItem): FoodAmountUnit? = choices(food).firstOrNull()
    fun canCalculate(food: FoodItem): Boolean = choices(food).isNotEmpty()

    fun choices(food: FoodItem): List<FoodAmountUnit> {
        if (!food.referenceAmount.isFinite() || food.referenceAmount <= 0 ||
            nutritionQuality(food) == NutritionQuality.UNKNOWN || food.unit.isBlank() ||
            FoodSearchPolicy.needsBasisReview(food)
        ) return emptyList()
        val basisUnit = canonicalUnit(food.unit)
        val choices = mutableListOf<FoodAmountUnit>()
        // The generator joins by food ID, source code, original name/brand and
        // report number when present. Exact source servings outrank generic
        // household references, while the original nutrition basis is retained.
        FoodMetadataPolicy.lookup(food.id)?.let { metadata ->
            val amount = metadata.basisAmountPerUnit
            val unit = canonicalUnit(metadata.householdUnit)
            val quality = when (metadata.servingEvidenceKind) {
                "OFFICIAL_SERVING" -> PortionQuality.OFFICIAL_SERVING
                "VERIFIED_CONVERSION" -> PortionQuality.VERIFIED_CONVERSION
                else -> null
            }
            if (quality != null && amount != null && amount.isFinite() && amount > 0 &&
                amount <= MAX_BASIS_AMOUNT && canonicalUnit(metadata.basisUnit) == basisUnit &&
                unit in setOf("개", "줄", "봉", "병", "캔", "팩", "콘", "컵", "통", "잔", "조각", "인분", "공기", "장", "제품 전체") &&
                (metadata.servingSourceReference.startsWith("https://") || metadata.servingSourceReference.startsWith("http://")) &&
                metadata.servingSourceSize.isNotBlank()
            ) {
                choices += FoodAmountUnit(unit, amount, basisUnit,
                    "${food.sourceFoodCode} · ${metadata.servingSourceSize} · ${metadata.servingSourceReference}" +
                        if (quality == PortionQuality.VERIFIED_CONVERSION)
                            " · 실제 음식 크기와 양에 따라 달라질 수 있어요." else "",
                    quality)
            }
        }
        // A stated total is a product amount. The importer separately verifies named packages.
        PortionGuide.verifiedPackage(food)?.let { packaged ->
            choices += FoodAmountUnit(
                if (packaged.packageUnit == "제품") "제품 전체" else packaged.packageUnit,
                packaged.amount, basisUnit,
                "${food.sourceFoodCode} · ${food.servingDescription}", PortionQuality.OFFICIAL_SERVING
            )
        }
        officialServingPattern.find(food.servingDescription)?.let { match ->
            val count = match.groupValues[1].toDoubleOrNull()
            val amount = match.groupValues[3].toDoubleOrNull()
            if (count != null && count > 0 && amount != null && amount > 0 &&
                canonicalUnit(match.groupValues[4]) == basisUnit
            ) choices += FoodAmountUnit(
                canonicalUnit(match.groupValues[2]), amount / count, basisUnit,
                "${food.sourceFoodCode} · ${food.servingDescription}", PortionQuality.OFFICIAL_SERVING
            )
        }
        // Identity-specific FDC portions; never apply an egg/banana weight to similarly named products.
        if (food.sourceType == "USDA-SR-LEGACY" && basisUnit == "g") {
            when (food.sourceFoodCode) {
                "FDC-173424" -> choices += FoodAmountUnit("개", 50.0, "g",
                    "USDA FDC 173424 · 큰 삶은 달걀 1개 50g (large, 껍질 제외) · 실제 크기에 따라 달라질 수 있어요. · https://fdc.nal.usda.gov/food-details/173424/nutrients",
                    PortionQuality.VERIFIED_CONVERSION)
                "FDC-173944" -> choices += FoodAmountUnit("개", 118.0, "g",
                    "USDA FDC 173944 · 중간 크기 바나나 1개 118g (medium, 껍질 제외) · 실제 크기에 따라 달라질 수 있어요. · https://fdc.nal.usda.gov/food-details/173944/nutrients",
                    PortionQuality.VERIFIED_CONVERSION)
            }
        }
        val referencePresetUnits = mapOf(
            "rice-one" to "공기", "cup-one" to "컵", "fish-palm" to "장",
            "bread-one" to "조각", "apple-one" to "개", "jjol-one" to "인분"
        )
        // Preserve the existing food-specific, cited household references and their estimates.
        PortionGuide.presets(food).forEach { preset ->
            referencePresetUnits[preset.id]?.let { unit ->
                choices += FoodAmountUnit(unit, preset.amount, basisUnit,
                    "${preset.sourceReference} · 실제 음식 크기와 양에 따라 달라질 수 있어요.",
                    PortionQuality.VERIFIED_CONVERSION)
            }
        }
        choices += FoodAmountUnit(basisUnit, 1.0, basisUnit,
            "${format(food.referenceAmount)}$basisUnit 영양정보를 기준으로 계산",
            when (basisUnit) {
                "g" -> PortionQuality.WEIGHT_ONLY
                "ml" -> PortionQuality.VOLUME_ONLY
                else -> PortionQuality.OFFICIAL_SERVING
            })
        return choices.filter { it.basisAmountPerUnit.isFinite() && it.basisAmountPerUnit > 0 }
            .distinctBy(FoodAmountUnit::unit)
    }

    fun calculate(food: FoodItem, amount: Double, unit: String): FoodAmountNutrition? {
        val choice = choices(food).firstOrNull { it.unit == canonicalUnit(unit) } ?: return null
        if (!amount.isFinite() || amount <= 0) return null
        val basisAmount = amount * choice.basisAmountPerUnit
        return nutritionForBasis(food.referenceAmount, choice.basisUnit, food.energyKcal,
            Macronutrients(food.carbohydrateGrams, food.proteinGrams, food.fatGrams), basisAmount)
    }

    /** A historical record's nutrients and conversion stay independent of refreshed food metadata. */
    fun calculateSnapshot(referenceAmount: Double, basisUnit: String, calories: Int,
        macros: Macronutrients, amount: Double): FoodAmountNutrition? =
        nutritionForBasis(referenceAmount, basisUnit, calories.toDouble(), macros, amount)

    private fun nutritionForBasis(referenceAmount: Double, basisUnit: String, energyKcal: Double,
        macros: Macronutrients, basisAmount: Double): FoodAmountNutrition? {
        if (!referenceAmount.isFinite() || referenceAmount <= 0 || basisUnit.isBlank() ||
            !basisAmount.isFinite() || basisAmount <= 0 || basisAmount > MAX_BASIS_AMOUNT) return null
        val ratio = basisAmount / referenceAmount
        val calories = energyKcal * ratio
        if (!calories.isFinite() || calories < 0 || calories > MAX_CALORIES) return null
        fun scaled(value: Double?): Double? = value?.takeIf(::usableNutrient)?.times(ratio)
            ?.takeIf { it.isFinite() && it >= 0 }
        return FoodAmountNutrition(basisAmount, basisUnit, calories.roundToInt(),
            scaled(macros.carbohydrateGrams), scaled(macros.proteinGrams), scaled(macros.fatGrams))
    }

    fun amountInUnit(food: FoodItem, basisAmount: Double, unit: String): Double? = choices(food)
        .firstOrNull { it.unit == canonicalUnit(unit) }
        ?.takeIf { basisAmount.isFinite() && basisAmount > 0 && basisAmount <= MAX_BASIS_AMOUNT }
        ?.let { basisAmount / it.basisAmountPerUnit }

    fun canonicalUnit(unit: String): String = when (val value = unit.trim().lowercase(Locale.ROOT)) {
        "봉지" -> "봉"
        "제품", "제품전체" -> "제품 전체"
        else -> value
    }

    private fun usableNutrient(value: Double) = value.isFinite() && value >= 0
    private fun format(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
