package com.example.healthcare.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 자주 먹는 음식 엔티티
 */
@Entity(
    tableName = "frequent_foods",
    indices = [Index(value = ["foodItemId"], unique = true)]
)
data class FrequentFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodName: String,      // 음식명
    val defaultServing: String, // 기본 제공량 (예: 100g, 1그릇)
    val calories: Int,         // 칼로리
    val isFavorite: Boolean = false, // 사용자가 직접 고정한 즐겨찾기
    val isFrequent: Boolean = true, // 직접 저장한 자주 먹는 음식인지 여부
    val carbohydrateGrams: Double? = null,
    val proteinGrams: Double? = null,
    val fatGrams: Double? = null,
    /** 번들 업데이트 후에도 같은 제품을 구분하기 위한 FoodItem 안정 ID입니다. */
    val foodItemId: String? = null,
    val sourceType: String? = null,
    val sourceFoodCode: String? = null,
    val brand: String? = null
)
