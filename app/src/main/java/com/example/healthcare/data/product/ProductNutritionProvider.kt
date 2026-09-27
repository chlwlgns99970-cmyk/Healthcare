package com.example.healthcare.data.product

import com.example.healthcare.data.entity.FoodItem

/** 외부 상품 데이터 공급자는 검증된 영양 항목만 반환하며, 미설정 시 가짜 항목을 만들지 않습니다. */
interface ProductNutritionProvider {
    val isConfigured: Boolean
    suspend fun findByBarcode(barcode: String): FoodItem?
}

object DisabledProductNutritionProvider : ProductNutritionProvider {
    override val isConfigured: Boolean = false
    override suspend fun findByBarcode(barcode: String): FoodItem? = null
}
