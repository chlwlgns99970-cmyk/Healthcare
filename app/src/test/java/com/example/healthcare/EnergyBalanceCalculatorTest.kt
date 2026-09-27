package com.example.healthcare

import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.domain.EnergyBalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyBalanceCalculatorTest {
    @Test
    fun `BMR과 PAL로 유지 칼로리를 계산한다`() {
        assertEquals(2325.0, EnergyBalanceCalculator.calculateMaintenanceKcal(1500.0, 1.55), 0.0)
    }

    @Test
    fun `화면용 칼로리는 반올림한다`() {
        assertEquals(2326, EnergyBalanceCalculator.roundKcal(2325.5))
    }

    @Test
    fun `유지 칼로리보다 높은 양만 초과로 계산한다`() {
        assertEquals(300.0, EnergyBalanceCalculator.calculateDailySurplus(2625.0, 2325.0), 0.0)
    }

    @Test
    fun `BMR보다 높아도 유지 칼로리 이하면 초과가 아니다`() {
        assertEquals(0.0, EnergyBalanceCalculator.calculateDailySurplus(2000.0, 2325.0), 0.0)
    }

    @Test
    fun `7일 단순 환산을 계산한다`() {
        assertEquals(0.2727, EnergyBalanceCalculator.calculateSimpleWeightEquivalentKg(300.0, 7), 0.0001)
    }

    @Test
    fun `30일 단순 환산을 계산한다`() {
        assertEquals(1.1688, EnergyBalanceCalculator.calculateSimpleWeightEquivalentKg(300.0, 30), 0.0001)
    }

    @Test
    fun `초과가 0이면 환산도 0이다`() {
        assertEquals(0.0, EnergyBalanceCalculator.calculateSimpleWeightEquivalentKg(0.0, 30), 0.0)
    }

    @Test
    fun `매우 작은 환산값도 정밀도를 유지한다`() {
        val result = EnergyBalanceCalculator.calculateSimpleWeightEquivalentKg(1.0, 7)
        assertTrue(result > 0.0)
        assertTrue(result < 0.05)
    }

    @Test
    fun `음수 입력을 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            EnergyBalanceCalculator.calculateMaintenanceKcal(-1.0, 1.55)
        }
    }

    @Test
    fun `NaN과 Infinity를 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            EnergyBalanceCalculator.calculateMaintenanceKcal(Double.NaN, 1.55)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EnergyBalanceCalculator.calculateMaintenanceKcal(1500.0, Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `사용자 PAL 최소값을 허용한다`() {
        assertTrue(EnergyBalanceCalculator.isValidCustomPal(1.40))
    }

    @Test
    fun `사용자 PAL 최대값을 허용한다`() {
        assertTrue(EnergyBalanceCalculator.isValidCustomPal(2.40))
    }

    @Test
    fun `사용자 PAL 범위 밖을 거부한다`() {
        assertFalse(EnergyBalanceCalculator.isValidCustomPal(1.39))
        assertFalse(EnergyBalanceCalculator.isValidCustomPal(2.41))
    }

    @Test
    fun `큰 숫자 오버플로를 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            EnergyBalanceCalculator.calculateMaintenanceKcal(Int.MAX_VALUE.toDouble(), 2.40)
        }
    }

    @Test
    fun `목표 모드별 목표를 계산한다`() {
        assertEquals(2000, EnergyBalanceCalculator.resolveDailyTargetKcal(2000, 1500, 1.55, TargetMode.MANUAL))
        assertEquals(1500, EnergyBalanceCalculator.resolveDailyTargetKcal(2000, 1500, 1.55, TargetMode.BMR))
        assertEquals(2325, EnergyBalanceCalculator.resolveDailyTargetKcal(2000, 1500, 1.55, TargetMode.MAINTENANCE))
    }
}
