package com.example.healthcare

import com.example.healthcare.data.AppFontSize
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontSizeTest {
    @Test
    fun `기본값과 잘못된 저장값은 보통으로 복원된다`() {
        assertEquals(AppFontSize.NORMAL, AppFontSize.fromStoredValue(null))
        assertEquals(AppFontSize.NORMAL, AppFontSize.fromStoredValue("UNKNOWN"))
    }

    @Test
    fun `지원 단계와 배율이 고정되어 있다`() {
        assertEquals(listOf(0.90f, 1.00f, 1.15f, 1.30f), AppFontSize.entries.map { it.scale })
        assertEquals(AppFontSize.EXTRA_LARGE, AppFontSize.fromStoredValue("EXTRA_LARGE"))
    }
}
