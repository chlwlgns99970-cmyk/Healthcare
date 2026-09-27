package com.example.healthcare.data.photo.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface FoodPhotoAnalysisApi {
    @Multipart
    @POST("food-photo/analyze")
    suspend fun analyzeFoodPhoto(
        @Part image: MultipartBody.Part,
        @Part("requestId") requestId: RequestBody,
        @Part("locale") locale: RequestBody,
        @Part("timezone") timezone: RequestBody,
        @Part("capturedAt") capturedAt: RequestBody
    ): Response<FoodPhotoAnalysisResponseDto>
}

@JsonClass(generateAdapter = true)
data class FoodPhotoAnalysisResponseDto(
    @param:Json(name = "analysisId") val analysisId: String? = null,
    @param:Json(name = "requestId") val requestId: String? = null,
    @param:Json(name = "status") val status: String? = null,
    @param:Json(name = "analyzedAt") val analyzedAt: String? = null,
    @param:Json(name = "items") val items: List<DetectedFoodItemDto>? = null,
    @param:Json(name = "totalEstimatedKcal") val totalEstimatedKcal: Int? = null,
    @param:Json(name = "totalMinimumKcal") val totalMinimumKcal: Int? = null,
    @param:Json(name = "totalMaximumKcal") val totalMaximumKcal: Int? = null,
    @param:Json(name = "warnings") val warnings: List<String>? = null,
    @param:Json(name = "modelVersion") val modelVersion: String? = null
)

@JsonClass(generateAdapter = true)
data class DetectedFoodItemDto(
    @param:Json(name = "itemId") val itemId: String? = null,
    @param:Json(name = "foodName") val foodName: String? = null,
    @param:Json(name = "alternativeNames") val alternativeNames: List<String>? = null,
    @param:Json(name = "confidence") val confidence: Double? = null,
    @param:Json(name = "confidenceLevel") val confidenceLevel: String? = null,
    @param:Json(name = "estimatedAmount") val estimatedAmount: Double? = null,
    @param:Json(name = "amountUnit") val amountUnit: String? = null,
    @param:Json(name = "estimatedKcal") val estimatedKcal: Int? = null,
    @param:Json(name = "minimumKcal") val minimumKcal: Int? = null,
    @param:Json(name = "maximumKcal") val maximumKcal: Int? = null,
    @param:Json(name = "description") val description: String? = null,
    @param:Json(name = "assumptions") val assumptions: List<String>? = null,
    @param:Json(name = "selected") val selected: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class FoodPhotoErrorResponseDto(
    @param:Json(name = "error") val error: FoodPhotoErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class FoodPhotoErrorDto(
    @param:Json(name = "code") val code: String? = null,
    @param:Json(name = "message") val message: String? = null,
    @param:Json(name = "retryable") val retryable: Boolean? = null,
    @param:Json(name = "requestId") val requestId: String? = null
)
