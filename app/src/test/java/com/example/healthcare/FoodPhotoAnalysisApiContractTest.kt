package com.example.healthcare

import com.example.healthcare.data.photo.FoodPhotoProcessor
import com.example.healthcare.data.photo.RetrofitFoodPhotoAnalysisRepository
import com.example.healthcare.data.photo.api.FoodPhotoAnalysisApi
import com.example.healthcare.data.photo.model.PhotoAnalysisOutcome
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class FoodPhotoAnalysisApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: FoodPhotoAnalysisApi
    private lateinit var repository: RetrofitFoodPhotoAnalysisRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val moshi = Moshi.Builder().build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                OkHttpClient.Builder()
                    .readTimeout(300, TimeUnit.MILLISECONDS)
                    .build()
            )
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FoodPhotoAnalysisApi::class.java)
        repository = RetrofitFoodPhotoAnalysisRepository(
            api,
            FoodPhotoProcessor(File("build/test-photo-cache")),
            moshi
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `multipart 요청과 정상 응답 계약이 일치한다`() = runBlocking {
        server.enqueue(jsonResponse(successJson()))

        val response = callApi()
        val request = server.takeRequest()
        val outcome = repository.mapResponse(response.body(), "request-1")

        assertEquals("/food-photo/analyze", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertTrue(request.body.readUtf8().contains("request-1"))
        assertTrue(outcome is PhotoAnalysisOutcome.Success)
    }

    @Test
    fun `여러 음식 응답을 파싱한다`() = runBlocking {
        val secondItem = itemJson("item-2", "김치", 30)
        server.enqueue(jsonResponse(successJson(items = "${itemJson()},$secondItem", total = 640)))

        val outcome = repository.mapResponse(callApi().body(), "request-1")

        assertTrue(outcome is PhotoAnalysisOutcome.Success)
        assertEquals(2, (outcome as PhotoAnalysisOutcome.Success).analysis.detectedItems.size)
    }

    @Test
    fun `NO_FOOD와 UNCERTAIN 상태를 각각 매핑한다`() = runBlocking {
        server.enqueue(jsonResponse(noFoodJson()))
        server.enqueue(jsonResponse(successJson(status = "UNCERTAIN")))

        assertEquals(PhotoAnalysisOutcome.FoodNotDetected, repository.mapResponse(callApi().body(), "request-1"))
        val uncertain = repository.mapResponse(callApi().body(), "request-1")
        assertTrue(uncertain is PhotoAnalysisOutcome.Success)
        assertTrue((uncertain as PhotoAnalysisOutcome.Success).analysis.isPartial)
    }

    @Test
    fun `표준 오류 코드와 HTTP 상태를 사용자 결과로 매핑한다`() = runBlocking {
        val cases = listOf(
            Triple(400, "INVALID_IMAGE", PhotoAnalysisOutcome.InvalidImage),
            Triple(413, "IMAGE_TOO_LARGE", PhotoAnalysisOutcome.ImageTooLarge),
            Triple(415, "UNSUPPORTED_IMAGE_TYPE", PhotoAnalysisOutcome.UnsupportedImage),
            Triple(429, "RATE_LIMITED", PhotoAnalysisOutcome.RateLimited),
            Triple(502, "UPSTREAM_INVALID_RESPONSE", PhotoAnalysisOutcome.MalformedResponse),
            Triple(502, "UPSTREAM_INCOMPLETE_RESPONSE", PhotoAnalysisOutcome.MalformedResponse),
            Triple(422, "UPSTREAM_REFUSED", PhotoAnalysisOutcome.MalformedResponse),
            Triple(503, "UPSTREAM_MODEL_ACCESS_DENIED", PhotoAnalysisOutcome.ServiceNotConfigured),
            Triple(504, "UPSTREAM_TIMEOUT", PhotoAnalysisOutcome.TimedOut)
        )
        cases.forEach { (status, code, expected) ->
            server.enqueue(
                MockResponse().setResponseCode(status).setBody(
                    """{"error":{"code":"$code","message":"safe","retryable":false,"requestId":"request-1"}}"""
                )
            )
            val response = callApi()
            assertEquals(expected, repository.mapHttpError(response.code(), response.errorBody()?.string()))
        }
    }

    @Test
    fun `잘못된 JSON과 필수 필드 누락을 거부한다`() = runBlocking {
        server.enqueue(jsonResponse("{not-json"))
        var malformedJson = false
        try {
            callApi()
        } catch (_: JsonEncodingException) {
            malformedJson = true
        } catch (_: JsonDataException) {
            malformedJson = true
        }
        assertTrue(malformedJson)

        server.enqueue(jsonResponse("""{"analysisId":"analysis-1","requestId":"request-1","status":"SUCCESS"}"""))
        assertEquals(
            PhotoAnalysisOutcome.MalformedResponse,
            repository.mapResponse(callApi().body(), "request-1")
        )
    }

    private suspend fun callApi() = api.analyzeFoodPhoto(
        image = MultipartBody.Part.createFormData(
            "image",
            "meal.jpg",
            "image".toRequestBody("image/jpeg".toMediaType())
        ),
        requestId = "request-1".textBody(),
        locale = "ko-KR".textBody(),
        timezone = "Asia/Seoul".textBody(),
        capturedAt = "2026-09-12T12:00:00+09:00".textBody()
    )

    private fun String.textBody() = toRequestBody("text/plain".toMediaType())

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun successJson(
        status: String = "SUCCESS",
        items: String = itemJson(),
        total: Int = 610
    ) = """{
        "analysisId":"analysis-1",
        "requestId":"request-1",
        "status":"$status",
        "items":[$items],
        "totalEstimatedKcal":$total,
        "totalMinimumKcal":null,
        "totalMaximumKcal":null,
        "warnings":[],
        "modelVersion":"test-model",
        "analyzedAt":"2026-09-12T03:00:00Z"
    }"""

    private fun noFoodJson() = """{
        "analysisId":"analysis-1",
        "requestId":"request-1",
        "status":"NO_FOOD",
        "items":[],
        "totalEstimatedKcal":null,
        "totalMinimumKcal":null,
        "totalMaximumKcal":null,
        "warnings":["음식을 찾지 못했습니다."],
        "modelVersion":"test-model",
        "analyzedAt":"2026-09-12T03:00:00Z"
    }"""

    private fun itemJson(itemId: String = "item-1", foodName: String = "비빔밥", kcal: Int = 610) = """{
        "itemId":"$itemId",
        "foodName":"$foodName",
        "alternativeNames":[],
        "confidence":null,
        "confidenceLevel":"MEDIUM",
        "estimatedAmount":1.0,
        "amountUnit":"인분",
        "estimatedKcal":$kcal,
        "minimumKcal":null,
        "maximumKcal":null,
        "description":"추정",
        "assumptions":[],
        "selected":true
    }"""
}
