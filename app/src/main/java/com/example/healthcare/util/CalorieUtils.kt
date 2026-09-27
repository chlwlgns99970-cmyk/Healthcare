package com.example.healthcare.util

import kotlin.math.roundToInt

/**
 * 칼로리 계산 및 표현과 관련된 유틸리티 클래스
 */
object CalorieUtils {

    /**
     * 칼로리 섭취 현황에 대한 중립적인 상태 메시지를 생성합니다.
     * 
     * @param total 현재 총 섭취량
     * @param target 목표 섭취량
     * @return 중립적으로 표현된 상태 메시지
     */
    fun getCalorieStatusText(total: Int, target: Int): String {
        val remaining = target - total
        return if (remaining >= 0) {
            "목표까지 ${remaining}kcal"
        } else {
            "목표 대비 +${-remaining}kcal"
        }
    }

    /**
     * 문자열 입력값을 반올림하여 정수 칼로리 값으로 변환합니다.
     * 
     * @param input 사용자가 입력한 문자열 (예: "100.7")
     * @return 반올림된 정수값, 변환 불가 시 null
     */
    fun parseAndRoundCalories(input: String): Int? {
        return input.toDoubleOrNull()?.let {
            it.roundToInt()
        }
    }
}
