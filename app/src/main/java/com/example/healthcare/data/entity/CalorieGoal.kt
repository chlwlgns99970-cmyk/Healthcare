package com.example.healthcare.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 일일 칼로리 목표 엔티티
 */
@Entity(tableName = "calorie_goals")
data class CalorieGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetCalories: Int,   // 목표 칼로리
    val startDate: String      // 적용 시작일 (YYYY-MM-DD)
)
