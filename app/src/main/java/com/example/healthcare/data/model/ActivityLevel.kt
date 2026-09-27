package com.example.healthcare.data.model

/** 사용자가 선택하는 활동 수준과 표준 PAL 값입니다. */
enum class ActivityLevel(val defaultPalMultiplier: Double?) {
    LIGHT(1.55),
    MODERATE(1.75),
    HIGH(2.20),
    CUSTOM(null)
}
