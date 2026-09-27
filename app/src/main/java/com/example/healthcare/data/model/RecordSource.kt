package com.example.healthcare.data.model

/** 음식 기록이 생성된 경로입니다. */
enum class RecordSource {
    MANUAL,
    SAVED_FOOD,
    PHOTO_AI,
    FOOD_SEARCH,
    BARCODE,
    NUTRITION_LABEL,
    RECOMMENDATION,
    RECENT_REPEAT
}
