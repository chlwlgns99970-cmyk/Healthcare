package com.example.healthcare.data

import android.content.Context
import androidx.core.content.edit
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodyProfileCalculator
import com.example.healthcare.domain.BodySex

interface BodyProfilePersistence {
    fun read(): BodyProfile?
    fun readDraft(): BodyProfileDraft = read()?.let {
        BodyProfileDraft(it.sex, it.ageYears, it.heightCm, it.weightKg)
    } ?: BodyProfileDraft()
    fun readExerciseWeight(): Double?
    fun save(profile: BodyProfile)
}

data class BodyProfileDraft(
    val sex: BodySex? = null,
    val ageYears: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null
)

internal fun mergeConfirmedLegacyWeight(
    canonical: BodyProfileDraft,
    legacyWeightKg: Double?
): BodyProfileDraft {
    if (canonical.weightKg != null) return canonical
    val validLegacyWeight = legacyWeightKg
        ?.takeIf { it in BodyProfileCalculator.MIN_WEIGHT_KG..BodyProfileCalculator.MAX_WEIGHT_KG }
    return canonical.copy(weightKg = validLegacyWeight)
}

/** Room과 독립된 선택 설정. 기존 운동 체중 설정과 같은 값을 함께 유지한다. */
class BodyProfileStore(context: Context) : BodyProfilePersistence {
    private val preferences = context.getSharedPreferences("body_profile", Context.MODE_PRIVATE)
    private val exercisePreferences = context.getSharedPreferences("exercise_coach", Context.MODE_PRIVATE)

    override fun read(): BodyProfile? {
        val draft = readDraft()
        return BodyProfileCalculator.validate(
            sex = draft.sex,
            ageText = draft.ageYears?.toString().orEmpty(),
            heightText = draft.heightCm?.toString().orEmpty(),
            weightText = draft.weightKg?.toString().orEmpty()
        ).profile
    }

    override fun readDraft(): BodyProfileDraft = runCatching {
        migrateLegacyExerciseWeight()
        BodyProfileDraft(
            sex = preferences.getString(KEY_SEX, null)
                ?.let { runCatching { BodySex.valueOf(it) }.getOrNull() },
            ageYears = preferences.getInt(KEY_AGE, 0).takeIf { it > 0 },
            heightCm = preferences.getString(KEY_HEIGHT, null)?.toDoubleOrNull(),
            weightKg = preferences.getString(KEY_WEIGHT, null)?.toDoubleOrNull()
        )
    }.getOrDefault(BodyProfileDraft())

    override fun readExerciseWeight(): Double? = exercisePreferences.getString("weight_kg", null)?.toDoubleOrNull()

    // Only the onboarding progress lives here; food choices use user_meal_preferences.
    fun isTasteSetupPending(): Boolean = preferences.getBoolean("taste_setup_pending", false)

    fun setTasteSetupPending(pending: Boolean) {
        preferences.edit(commit = true) { putBoolean("taste_setup_pending", pending) }
    }

    override fun save(profile: BodyProfile) {
        require(BodyProfileCalculator.validate(
            profile.sex,
            profile.ageYears.toString(),
            profile.heightCm.toString(),
            profile.weightKg.toString()
        ).isValid)
        check(preferences.edit().apply {
            putString(KEY_SEX, profile.sex.name)
            putInt(KEY_AGE, profile.ageYears)
            putString(KEY_HEIGHT, profile.heightCm.toString())
            putString(KEY_WEIGHT, profile.weightKg.toString())
        }.commit()) { "Body profile persistence failed" }
        check(exercisePreferences.edit().apply { putString("weight_kg", profile.weightKg.toString()) }.commit()) { "Exercise weight persistence failed" }
    }

    /** 운동 화면에서 바꾼 몸무게도 유효한 신체정보가 있으면 같은 값으로 맞춘다. */
    fun saveExerciseWeight(weightKg: Double) {
        val current = read()
        if (current != null && weightKg in BodyProfileCalculator.MIN_WEIGHT_KG..BodyProfileCalculator.MAX_WEIGHT_KG) {
            save(current.copy(weightKg = weightKg))
        } else {
            check(exercisePreferences.edit().apply { putString("weight_kg", weightKg.toString()) }.commit()) { "Exercise weight persistence failed" }
        }
    }

    /**
     * v1에서 실제로 확인된 신체 관련 저장값은 exercise_coach/weight_kg뿐이다.
     * canonical 값이 없는 경우에만 유효한 기존 값을 옮기며, 일부 profile을 완성하기 위한
     * 성별·나이·키는 추정하지 않는다. KEY_WEIGHT가 생기면 이후 실행은 아무것도 변경하지 않는다.
     */
    private fun migrateLegacyExerciseWeight() {
        if (preferences.contains(KEY_WEIGHT)) return
        val legacyWeight = mergeConfirmedLegacyWeight(
            canonical = BodyProfileDraft(),
            legacyWeightKg = readExerciseWeight()
        ).weightKg ?: return
        preferences.edit(commit = true) { putString(KEY_WEIGHT, legacyWeight.toString()) }
    }

    private companion object {
        const val KEY_SEX = "sex"
        const val KEY_AGE = "age_years"
        const val KEY_HEIGHT = "height_cm"
        const val KEY_WEIGHT = "weight_kg"
    }
}
