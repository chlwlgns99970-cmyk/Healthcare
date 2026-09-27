package com.example.healthcare.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 자주 먹는 음식 엔티티
 */
@Entity(tableName = "frequent_foods")
data class FrequentFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodName: String,      // 음식명
    val defaultServing: String, // 기본 제공량 (예: 100g, 1그릇)
    val calories: Int,         // 칼로리
    val isFavorite: Boolean = false // 즐겨찾기 여부
)
