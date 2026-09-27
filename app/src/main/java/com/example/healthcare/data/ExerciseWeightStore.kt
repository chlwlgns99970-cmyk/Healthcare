package com.example.healthcare.data

import android.content.Context
import androidx.core.content.edit
import com.example.healthcare.domain.ExerciseCoachCalculator

/** 운동 시간 추정에만 쓰는 선택 정보. 기존 Room 스키마와 무관하다. */
class ExerciseWeightStore(context: Context) {
    private val preferences = context.getSharedPreferences("exercise_coach", Context.MODE_PRIVATE)

    fun read(): Double? = preferences.getString("weight_kg", null)
        ?.let(ExerciseCoachCalculator::validateWeight)

    fun save(weightKg: Double) {
        require(ExerciseCoachCalculator.validateWeight(weightKg.toString()) != null)
        preferences.edit { putString("weight_kg", weightKg.toString()) }
    }
}
