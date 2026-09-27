package com.example.healthcare.data.photo

import com.example.healthcare.data.photo.api.FoodPhotoAnalysisApi
import com.example.healthcare.data.photo.api.FoodPhotoErrorResponseDto
import com.example.healthcare.data.photo.api.FoodPhotoAnalysisResponseDto
import com.example.healthcare.data.photo.model.DetectedFoodItem
import com.example.healthcare.data.photo.model.FoodPhotoAnalysis
import com.example.healthcare.data.photo.model.FoodPhotoAnalysisRequest
import com.example.healthcare.data.photo.model.PhotoAnalysisOutcome
import com.example.healthcare.data.photo.model.PhotoAnalysisProgress
import kotlinx.coroutines.CancellationException
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

interface FoodPhotoAnalysisRepository {
    val isConfigured: Boolean get() = true

    suspend fun analyze(
        request: FoodPhotoAnalysisRequest,
        onProgress: (PhotoAnalysisProgress) -> Unit
    ): PhotoAnalysisOutcome
}

/** 백엔드 설정이 없는 빌드에서 가짜 분석값 대신 명시적인 미설정 상태를 반환합니다. */
class UnconfiguredFoodPhotoAnalysisRepository : FoodPhotoAnalysisRepository {
    override val isConfigured: Boolean = false

    override suspend fun analyze(
        request: FoodPhotoAnalysisRequest,
        onProgress: (PhotoAnalysisProgress) -> Unit
    ): PhotoAnalysisOutcome = PhotoAnalysisOutcome.ServiceNotConfigured
}

/** 실제 백엔드가 제공되면 이 구현을 주입해 동일한 UI와 저장 흐름을 사용합니다. */
class RetrofitFoodPhotoAnalysisRepository(
    private val api: FoodPhotoAnalysisApi,
    private val processor: FoodPhotoProcessor,
    moshi: Moshi = Moshi.Builder().build()
) : FoodPhotoAnalysisRepository {
    private val errorAdapter = moshi.adapter(FoodPhotoErrorResponseDto::class.java)

    override suspend fun analyze(
        request: FoodPhotoAnalysisRequest,
        onProgress: (PhotoAnalysisProgress) -> Unit
    ): PhotoAnalysisOutcome {
        onProgress(PhotoAnalysisProgress.PROCESSING_IMAGE)
        val processed = when (val result = processor.prepareForUpload(request.imageFile)) {
            is PhotoProcessingResult.Success -> result
            PhotoProcessingResult.InvalidImage,
            PhotoProcessingResult.UnsupportedFormat -> return PhotoAnalysisOutcome.InvalidImage
            PhotoProcessingResult.TooLarge -> return PhotoAnalysisOutcome.ImageTooLarge
        }

        return try {
            onProgress(PhotoAnalysisProgress.UPLOADING)
            val textType = "text/plain".toMediaType()
            val imagePart = MultipartBody.Part.createFormData(
                "image",
                processed.file.name,
                processed.file.asRequestBody(processed.mimeType.toMediaType())
            )
            val response = api.analyzeFoodPhoto(
                image = imagePart,
                requestId = request.requestId.toRequestBody(textType),
                locale = request.locale.toRequestBody(textType),
                timezone = request.timezone.toRequestBody(textType),
                capturedAt = request.capturedAt.toRequestBody(textType)
            )

            if (!response.isSuccessful) {
                return mapHttpError(response.code(), response.errorBody()?.string())
            }

            onProgress(PhotoAnalysisProgress.ANALYZING)
            mapResponse(response.body(), request.requestId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SocketTimeoutException) {
            PhotoAnalysisOutcome.TimedOut
        } catch (_: JsonDataException) {
            PhotoAnalysisOutcome.MalformedResponse
        } catch (_: JsonEncodingException) {
            PhotoAnalysisOutcome.MalformedResponse
        } catch (_: IOException) {
            PhotoAnalysisOutcome.NetworkUnavailable
        } catch (_: Exception) {
            PhotoAnalysisOutcome.ServerError
        } finally {
            processed.file.delete()
        }
    }

    internal fun mapResponse(
        response: FoodPhotoAnalysisResponseDto?,
        expectedRequestId: String
    ): PhotoAnalysisOutcome {
        if (response == null) return PhotoAnalysisOutcome.MalformedResponse
        if (response.analysisId.isNullOrBlank() || response.requestId != expectedRequestId) {
            return PhotoAnalysisOutcome.MalformedResponse
        }

        val status = response.status?.uppercase() ?: return PhotoAnalysisOutcome.MalformedResponse
        if (status == "NO_FOOD") {
            return if (response.items.orEmpty().isEmpty()) PhotoAnalysisOutcome.FoodNotDetected
            else PhotoAnalysisOutcome.MalformedResponse
        }
        if (status != "SUCCESS" && status != "UNCERTAIN") {
            return PhotoAnalysisOutcome.MalformedResponse
        }

        var partial = false
        val items = response.items.orEmpty().mapNotNull { item ->
            val name = item.foodName?.trim().orEmpty()
            val itemId = item.itemId?.trim().orEmpty()
            if (name.isBlank() || itemId.isBlank()) {
                partial = true
                null
            } else {
                val estimated = item.estimatedKcal?.takeIf { it > 0 }
                val amount = item.estimatedAmount?.takeIf { it > 0.0 }
                val unit = item.amountUnit?.trim()?.takeIf { it.isNotBlank() }
                val confidenceLevel = item.confidenceLevel?.uppercase()
                    ?.takeIf { it in setOf("HIGH", "MEDIUM", "LOW") }
                val minimum = item.minimumKcal?.takeIf { it > 0 }
                val maximum = item.maximumKcal?.takeIf { it > 0 }
                val rangeValid = (minimum == null && maximum == null) ||
                    (minimum != null && maximum != null && estimated != null &&
                        minimum <= estimated && estimated <= maximum)
                if (estimated == null || amount == null || unit == null || confidenceLevel == null || !rangeValid) {
                    partial = true
                    return@mapNotNull null
                }
                DetectedFoodItem(
                    itemId = itemId,
                    foodName = name,
                    alternativeNames = item.alternativeNames.orEmpty().filter { it.isNotBlank() },
                    confidence = item.confidence?.takeIf { it in 0.0..1.0 },
                    estimatedAmount = amount,
                    amountUnit = unit,
                    estimatedKcal = estimated,
                    minimumKcal = minimum,
                    maximumKcal = maximum,
                    description = item.description?.takeIf { it.isNotBlank() },
                    confidenceLevel = confidenceLevel,
                    assumptions = item.assumptions.orEmpty().filter { it.isNotBlank() }
                )
            }
        }

        if (items.isEmpty()) return PhotoAnalysisOutcome.MalformedResponse
        val calculatedTotal = items.sumOf { it.estimatedKcal ?: 0 }
        val total = response.totalEstimatedKcal?.takeIf { it > 0 }
        if (total == null || total != calculatedTotal) return PhotoAnalysisOutcome.MalformedResponse

        val allRangesPresent = items.all { it.minimumKcal != null && it.maximumKcal != null }
        val totalMinimum = response.totalMinimumKcal?.takeIf { it > 0 }
        val totalMaximum = response.totalMaximumKcal?.takeIf { it > 0 }
        if (allRangesPresent) {
            if (totalMinimum != items.sumOf { it.minimumKcal ?: 0 } ||
                totalMaximum != items.sumOf { it.maximumKcal ?: 0 }) {
                return PhotoAnalysisOutcome.MalformedResponse
            }
        } else if (totalMinimum != null || totalMaximum != null) {
            return PhotoAnalysisOutcome.MalformedResponse
        }

        return PhotoAnalysisOutcome.Success(
            FoodPhotoAnalysis(
                analysisId = response.analysisId,
                requestId = expectedRequestId,
                analyzedAt = response.analyzedAt,
                detectedItems = items,
                totalEstimatedKcal = total,
                totalMinimumKcal = totalMinimum,
                totalMaximumKcal = totalMaximum,
                warnings = response.warnings.orEmpty().filter { it.isNotBlank() },
                modelVersion = response.modelVersion,
                isPartial = partial || status == "UNCERTAIN"
            )
        )
    }

    internal fun mapHttpError(statusCode: Int, errorBody: String?): PhotoAnalysisOutcome {
        val code = runCatching {
            errorBody?.let(errorAdapter::fromJson)?.error?.code?.uppercase()
        }.getOrNull()
        return when (code) {
            "INVALID_IMAGE" -> PhotoAnalysisOutcome.InvalidImage
            "IMAGE_TOO_LARGE" -> PhotoAnalysisOutcome.ImageTooLarge
            "UNSUPPORTED_IMAGE_TYPE" -> PhotoAnalysisOutcome.UnsupportedImage
            "RATE_LIMITED", "UPSTREAM_RATE_LIMITED" -> PhotoAnalysisOutcome.RateLimited
            "UPSTREAM_TIMEOUT" -> PhotoAnalysisOutcome.TimedOut
            "PROVIDER_NOT_CONFIGURED", "UPSTREAM_AUTHENTICATION_FAILED", "UPSTREAM_MODEL_ACCESS_DENIED" ->
                PhotoAnalysisOutcome.ServiceNotConfigured
            "UPSTREAM_INVALID_RESPONSE", "UPSTREAM_INCOMPLETE_RESPONSE", "UPSTREAM_REFUSED" ->
                PhotoAnalysisOutcome.MalformedResponse
            "INVALID_REQUEST", "REQUEST_ID_CONFLICT" -> PhotoAnalysisOutcome.MalformedResponse
            "UPSTREAM_ERROR", "INTERNAL_ERROR" -> PhotoAnalysisOutcome.ServerError
            else -> when (statusCode) {
                401, 403 -> PhotoAnalysisOutcome.AuthenticationRequired
                413 -> PhotoAnalysisOutcome.ImageTooLarge
                415 -> PhotoAnalysisOutcome.UnsupportedImage
                429 -> PhotoAnalysisOutcome.RateLimited
                504 -> PhotoAnalysisOutcome.TimedOut
                in 500..599 -> PhotoAnalysisOutcome.ServerError
                else -> PhotoAnalysisOutcome.MalformedResponse
            }
        }
    }
}
