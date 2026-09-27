package com.example.healthcare

import com.example.healthcare.domain.ExerciseActivity
import com.example.healthcare.domain.ExerciseCoachCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCoachCalculatorTest {
    @Test fun `목표 이하는 추천 대상이 아니다`() {
        assertEquals(0, ExerciseCoachCalculator.targetExcess(1900, 2000))
        assertEquals(0, ExerciseCoachCalculator.targetExcess(2000, 2000))
        assertNull(ExerciseCoachCalculator.estimate(0, 70.0, ExerciseActivity.WALK))
    }

    @Test fun `체중과 초과량에 따라 시간이 달라진다`() {
        val walking50 = ExerciseCoachCalculator.estimate(150, 50.0, ExerciseActivity.WALK)!!
        val walking90 = ExerciseCoachCalculator.estimate(150, 90.0, ExerciseActivity.WALK)!!
        assertTrue(walking50.minutes > walking90.minutes)
        assertNotEquals(walking50.minutes, ExerciseCoachCalculator.estimate(200, 50.0, ExerciseActivity.WALK)!!.minutes)
        assertEquals(150, ExerciseCoachCalculator.targetExcess(2150, 2000))
    }

    @Test fun `100 kcal와 초과분은 같은 MET 정책으로 계산한다`() {
        val standard = ExerciseCoachCalculator.estimate(100, 70.0, ExerciseActivity.BRISK_WALK)!!
        val excess = ExerciseCoachCalculator.estimate(250, 70.0, ExerciseActivity.BRISK_WALK)!!
        assertEquals(100, standard.estimatedKcal)
        assertEquals(250, excess.estimatedKcal)
        assertTrue(excess.minutes > standard.minutes)
        assertEquals(0L, standard.minutes % 5L)
        assertEquals(0L, excess.minutes % 5L)
    }

    @Test fun `모든 여섯 운동은 양수이고 유한한 예상 시간을 만든다`() {
        assertEquals(6, ExerciseActivity.entries.size)
        ExerciseActivity.entries.forEach { activity ->
            val estimate = ExerciseCoachCalculator.estimate(100, 70.0, activity)
            assertTrue("No estimate for ${activity.name}", estimate != null)
            assertTrue(requireNotNull(estimate).minutes > 0L)
        }
    }

    @Test fun `매우 큰 칼로리도 overflow 없이 계산한다`() {
        val estimate = ExerciseCoachCalculator.estimate(Int.MAX_VALUE, 20.0, ExerciseActivity.WALK)
        assertTrue(estimate != null)
        assertTrue(requireNotNull(estimate).minutes > 0L)
        assertEquals(Int.MAX_VALUE, estimate.estimatedKcal)
    }

    @Test fun `몸무게 경계와 잘못된 값을 검증한다`() {
        assertEquals(20.0, ExerciseCoachCalculator.validateWeight("20"))
        assertEquals(300.0, ExerciseCoachCalculator.validateWeight("300"))
        listOf("", "abc", "19.9", "300.1", "NaN", "Infinity").forEach {
            assertNull(ExerciseCoachCalculator.validateWeight(it))
        }
        assertFalse(ExerciseCoachCalculator.estimate(150, 0.0, ExerciseActivity.WALK) != null)
    }

    @Test fun `걸음 칼로리는 목표 초과 계산에 자동 상계되지 않는다`() {
        val excessWithoutStepOffset = ExerciseCoachCalculator.targetExcess(2_180, 2_000)
        assertEquals(180, excessWithoutStepOffset)
    }
}
