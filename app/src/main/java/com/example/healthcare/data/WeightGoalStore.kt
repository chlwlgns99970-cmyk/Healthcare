package com.example.healthcare.data

import android.content.Context
import androidx.core.content.edit

data class WeightLossGoal(
    val currentWeightKg: Double,
    val targetWeightKg: Double,
    val durationWeeks: Int,
    val dailyDeficitKcal: Int,
    val appliedTargetKcal: Int,
    val createdAtEpochMillis: Long
)

interface WeightGoalPersistence {
    fun read(): WeightLossGoal?
    fun save(goal: WeightLossGoal)
}

/** Room과 독립된 선택 설정입니다. 사용자가 명시적으로 적용하기 전에는 기록하지 않습니다. */
class WeightGoalStore(context: Context) : WeightGoalPersistence {
    private val preferences = context.getSharedPreferences("weight_loss_goal", Context.MODE_PRIVATE)

    override fun read(): WeightLossGoal? {
        if (!preferences.contains(KEY_TARGET)) return null
        val current = preferences.getString(KEY_CURRENT, null)?.toDoubleOrNull() ?: return null
        val target = preferences.getString(KEY_TARGET, null)?.toDoubleOrNull() ?: return null
        val weeks = preferences.getInt(KEY_WEEKS, 0)
        val deficit = preferences.getInt(KEY_DEFICIT, 0)
        val applied = preferences.getInt(KEY_APPLIED, 0)
        if (weeks <= 0 || deficit <= 0 || applied <= 0 || target >= current) return null
        return WeightLossGoal(current, target, weeks, deficit, applied, preferences.getLong(KEY_CREATED, 0L))
    }

    override fun save(goal: WeightLossGoal) {
        require(goal.targetWeightKg > 0 && goal.targetWeightKg < goal.currentWeightKg)
        require(goal.durationWeeks > 0 && goal.dailyDeficitKcal > 0 && goal.appliedTargetKcal > 0)
        preferences.edit(commit = true) {
            putString(KEY_CURRENT, goal.currentWeightKg.toString())
            putString(KEY_TARGET, goal.targetWeightKg.toString())
            putInt(KEY_WEEKS, goal.durationWeeks)
            putInt(KEY_DEFICIT, goal.dailyDeficitKcal)
            putInt(KEY_APPLIED, goal.appliedTargetKcal)
            putLong(KEY_CREATED, goal.createdAtEpochMillis)
        }
    }

    private companion object {
        const val KEY_CURRENT = "current_weight_kg"
        const val KEY_TARGET = "target_weight_kg"
        const val KEY_WEEKS = "duration_weeks"
        const val KEY_DEFICIT = "daily_deficit_kcal"
        const val KEY_APPLIED = "applied_target_kcal"
        const val KEY_CREATED = "created_at_epoch_millis"
    }
}
