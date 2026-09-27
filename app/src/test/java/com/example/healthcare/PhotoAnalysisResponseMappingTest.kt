package com.example.healthcare

import com.example.healthcare.data.photo.FoodPhotoProcessor
import com.example.healthcare.data.photo.RetrofitFoodPhotoAnalysisRepository
import com.example.healthcare.data.photo.api.DetectedFoodItemDto
import com.example.healthcare.data.photo.api.FoodPhotoAnalysisApi
import com.example.healthcare.data.photo.api.FoodPhotoAnalysisResponseDto
import com.example.healthcare.data.photo.model.PhotoAnalysisOutcome
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.File

class PhotoAnalysisResponseMappingTest {
    private val unusedApi = object : FoodPhotoAnalysisApi {
        override suspend fun analyzeFoodPhoto(
            image: MultipartBody.Part,
            requestId: RequestBody,
            locale: RequestBody,
            timezone: RequestBody,
            capturedAt: RequestBody
        ): Response<FoodPhotoAnalysisResponseDto> = error("이 테스트에서는 네트워크를 호출하지 않습니다.")
    }
    private val repository = RetrofitFoodPhotoAnalysisRepository(
        unusedApi,
        FoodPhotoProcessor(File("build/test-photo-cache"))
    )

    @Test
    fun `서버가 범위와 신뢰도를 주지 않으면 값을 만들지 않는다`() {
        val outcome = repository.mapResponse(
            FoodPhotoAnalysisResponseDto(
                analysisId = "analysis-1",
                requestId = "request-1",
                status = "SUCCESS",
                items = listOf(
                    DetectedFoodItemDto(
                        itemId = "item-1",
                        foodName = "사과",
                        estimatedAmount = 1.0,
                        amountUnit = "개",
                        confidenceLevel = "MEDIUM",
                        estimatedKcal = 95
                    )
                ),
                totalEstimatedKcal = 95
            ),
            "request-1"
        )

        assertTrue(outcome is PhotoAnalysisOutcome.Success)
        val item = (outcome as PhotoAnalysisOutcome.Success).analysis.detectedItems.single()
        assertNull(item.confidence)
        assertNull(item.minimumKcal)
        assertNull(item.maximumKcal)
    }

    @Test
    fun `응답 요청 ID가 다르면 결과를 거부한다`() {
        val outcome = repository.mapResponse(
            FoodPhotoAnalysisResponseDto(
                analysisId = "analysis-1",
                requestId = "another-request",
                status = "SUCCESS",
                totalEstimatedKcal = 100
            ),
            "request-1"
        )

        assertEquals(PhotoAnalysisOutcome.MalformedResponse, outcome)
    }

    @Test
    fun `음식과 총 칼로리가 모두 없으면 음식 인식 실패로 처리한다`() {
        val outcome = repository.mapResponse(
            FoodPhotoAnalysisResponseDto(
                analysisId = "analysis-1",
                requestId = "request-1",
                status = "NO_FOOD",
                items = emptyList()
            ),
            "request-1"
        )

        assertEquals(PhotoAnalysisOutcome.FoodNotDetected, outcome)
    }

    @Test
    fun `불확실 응답은 수정 가능한 부분 결과로 표시한다`() {
        val outcome = repository.mapResponse(
            FoodPhotoAnalysisResponseDto(
                analysisId = "analysis-1",
                requestId = "request-1",
                status = "UNCERTAIN",
                items = listOf(
                    DetectedFoodItemDto(
                        itemId = "item-1",
                        foodName = "볶음밥 추정",
                        estimatedAmount = 1.0,
                        amountUnit = "인분",
                        estimatedKcal = 600,
                        confidenceLevel = "LOW"
                    )
                ),
                totalEstimatedKcal = 600
            ),
            "request-1"
        )

        assertTrue(outcome is PhotoAnalysisOutcome.Success)
        assertTrue((outcome as PhotoAnalysisOutcome.Success).analysis.isPartial)
    }
}
