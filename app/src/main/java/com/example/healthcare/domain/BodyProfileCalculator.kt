package com.example.healthcare.domain

import kotlin.math.roundToInt

enum class BodySex { MALE, FEMALE }

data class BodyProfile(
    val sex: BodySex,
    val ageYears: Int,
    val heightCm: Double,
    val weightKg: Double
)

data class BodyProfileValidation(
    val profile: BodyProfile? = null,
    val sexError: String? = null,
    val ageError: String? = null,
    val heightError: String? = null,
    val weightError: String? = null
) {
    val isValid: Boolean get() = profile != null
}

/**
 * Mifflin–St Jeor resting-energy equation.
 * Source: Mifflin et al., Am J Clin Nutr. 1990;51(2):241-247, PMID 2305711.
 * This is an adult population estimate, not a measured BMR or medical prescription.
 */
object BodyProfileCalculator {
    const val MIN_AGE = 18
    const val MAX_AGE = 120
    const val MIN_HEIGHT_CM = 100.0
    const val MAX_HEIGHT_CM = 250.0
    const val MIN_WEIGHT_KG = 25.0
    const val MAX_WEIGHT_KG = 350.0

    fun validate(
        sex: BodySex?,
        ageText: String,
        heightText: String,
        weightText: String
    ): BodyProfileValidation {
        val age = ageText.toIntOrNull()
        val height = heightText.normalizedDecimal()
        val weight = weightText.normalizedDecimal()
        val sexError = if (sex == null) "성별을 선택해 주세요." else null
        val ageError = when {
            ageText.isBlank() -> "나이를 입력해 주세요."
            age == null || age !in MIN_AGE..MAX_AGE -> "나이는 18세 이상 120세 이하로 입력해 주세요."
            else -> null
        }
        val heightError = when {
            heightText.isBlank() -> "키를 입력해 주세요."
            height == null || height !in MIN_HEIGHT_CM..MAX_HEIGHT_CM -> "키는 100cm 이상 250cm 이하로 입력해 주세요."
            else -> null
        }
        val weightError = when {
            weightText.isBlank() -> "몸무게를 입력해 주세요."
            weight == null || weight !in MIN_WEIGHT_KG..MAX_WEIGHT_KG -> "몸무게는 25kg 이상 350kg 이하로 입력해 주세요."
            else -> null
        }
        val profile = if (sexError == null && ageError == null && heightError == null && weightError == null) {
            BodyProfile(requireNotNull(sex), requireNotNull(age), requireNotNull(height), requireNotNull(weight))
        } else null
        return BodyProfileValidation(profile, sexError, ageError, heightError, weightError)
    }

    fun estimateBmr(profile: BodyProfile): Int {
        val sexOffset = if (profile.sex == BodySex.MALE) 5.0 else -161.0
        return (10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.ageYears + sexOffset)
            .roundToInt()
            .coerceAtLeast(1)
    }

    private fun String.normalizedDecimal(): Double? = replace(',', '.').toDoubleOrNull()
}
