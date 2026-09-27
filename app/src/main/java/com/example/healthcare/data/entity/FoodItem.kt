package com.example.healthcare.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 검증된 출처에서 가져오거나 사용자가 직접 확인한 음식 영양 항목입니다.
 * 제공되지 않은 영양소는 0으로 채우지 않고 null로 보존합니다.
 */
@Entity(
    tableName = "food_items",
    indices = [
        Index(value = ["normalizedName"]),
        Index(value = ["barcode"]),
        Index(value = ["sourceType", "sourceFoodCode"], unique = true)
    ]
)
data class FoodItem(
    @PrimaryKey val id: String,
    val sourceType: String,
    val sourceFoodCode: String,
    val name: String,
    val normalizedName: String,
    /** 검색 동의어를 |동의어| 형태로 저장합니다. */
    val aliases: String = "",
    val category: String? = null,
    val referenceAmount: Double,
    val unit: String,
    val energyKcal: Double,
    val carbohydrateGrams: Double? = null,
    val proteinGrams: Double? = null,
    val fatGrams: Double? = null,
    val sodiumMilligrams: Double? = null,
    val servingDescription: String,
    /** 원본의 브랜드·업체명 또는 제조사명. 소비자 브랜드임을 추정하지 않습니다. */
    val brand: String? = null,
    val barcode: String? = null,
    val dataVersion: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** 공식 표기를 그대로 보존한 브랜드·제조사별 제품 개수입니다. */
data class FoodBrandSummary(
    val brand: String,
    val productCount: Int
)
