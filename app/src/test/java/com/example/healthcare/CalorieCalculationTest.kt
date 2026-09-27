package com.example.healthcare

import com.example.healthcare.util.CalorieUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 칼로리 계산 및 상태 메시지 표현 로직에 대한 단위 테스트
 */
class CalorieCalculationTest {

    @Test
    fun `칼로리 남음 상태 - 중립적 표현 검증`() {
        val target = 2000
        val total = 1500
        val expected = "목표까지 500kcal"
        
        val actual = CalorieUtils.getCalorieStatusText(total, target)
        
        assertEquals(expected, actual)
    }

    @Test
    fun `칼로리 초과 상태 - 중립적 표현 검증`() {
        val target = 2000
        val total = 2300
        val expected = "목표 대비 +300kcal"
        
        val actual = CalorieUtils.getCalorieStatusText(total, target)
        
        assertEquals(expected, actual)
    }

    @Test
    fun `칼로리 목표 달성 상태 - 중립적 표현 검증`() {
        val target = 2000
        val total = 2000
        val expected = "목표까지 0kcal"
        
        val actual = CalorieUtils.getCalorieStatusText(total, target)
        
        assertEquals(expected, actual)
    }

    @Test
    fun `칼로리 반올림 로직 검증`() {
        assertEquals(101, CalorieUtils.parseAndRoundCalories("100.7"))
        assertEquals(100, CalorieUtils.parseAndRoundCalories("100.3"))
        assertEquals(101, CalorieUtils.parseAndRoundCalories("100.5"))
        assertEquals(100, CalorieUtils.parseAndRoundCalories("100"))
        assertEquals(null, CalorieUtils.parseAndRoundCalories("abc"))
    }
}
