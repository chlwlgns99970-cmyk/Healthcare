package com.example.healthcare.data.model

/**
 * 식사 유형을 나타내는 Enum 클래스
 */
enum class MealType(val displayName: String) {
    BREAKFAST("아침"),
    LUNCH("점심"),
    DINNER("저녁"),
    SNACK("간식"),
    OTHER("기타")
}
