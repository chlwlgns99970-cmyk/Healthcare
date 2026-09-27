package com.example.healthcare

import com.example.healthcare.data.photo.api.FoodPhotoAnalysisServiceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FoodPhotoAnalysisServiceFactoryTest {
    @Test
    fun `HTTPS 기본 주소를 정규화한다`() {
        val url = FoodPhotoAnalysisServiceFactory.normalizeBaseUrl("  https://api.example.com/base///  ")
        assertEquals("https://api.example.com/base/", url.toString())
    }

    @Test
    fun `HTTP와 잘못된 주소를 거부한다`() {
        listOf("http://api.example.com", "not-a-url", "").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                FoodPhotoAnalysisServiceFactory.normalizeBaseUrl(value)
            }
        }
    }
}
