package com.example.healthcare.data.photo.model

import java.io.File

object PhotoAnalysisPolicy {
    const val LOW_CONFIDENCE_THRESHOLD = 0.55
}

data class FoodPhotoAnalysisRequest(
    val imageFile: File,
    val requestId: String,
    val locale: String,
    val timezone: String,
    val capturedAt: String
)

data class FoodPhotoAnalysis(
    val analysisId: String?,
    val requestId: String,
    val analyzedAt: String?,
    val detectedItems: List<DetectedFoodItem>,
    val totalEstimatedKcal: Int?,
    val totalMinimumKcal: Int?,
    val totalMaximumKcal: Int?,
    val warnings: List<String>,
    val modelVersion: String?,
    val isPartial: Boolean
)

data class DetectedFoodItem(
    val itemId: String,
    val foodName: String,
    val alternativeNames: List<String>,
    val confidence: Double?,
    val estimatedAmount: Double?,
    val amountUnit: String?,
    val estimatedKcal: Int?,
    val minimumKcal: Int?,
    val maximumKcal: Int?,
    val description: String?,
    val confidenceLevel: String? = null,
    val assumptions: List<String> = emptyList()
)

enum class PhotoAnalysisProgress {
    PROCESSING_IMAGE,
    UPLOADING,
    ANALYZING
}

sealed interface PhotoAnalysisOutcome {
    data class Success(val analysis: FoodPhotoAnalysis) : PhotoAnalysisOutcome
    data object FoodNotDetected : PhotoAnalysisOutcome
    data object InvalidImage : PhotoAnalysisOutcome
    data object ImageTooLarge : PhotoAnalysisOutcome
    data object UnsupportedImage : PhotoAnalysisOutcome
    data object NetworkUnavailable : PhotoAnalysisOutcome
    data object TimedOut : PhotoAnalysisOutcome
    data object AuthenticationRequired : PhotoAnalysisOutcome
    data object RateLimited : PhotoAnalysisOutcome
    data object MalformedResponse : PhotoAnalysisOutcome
    data object ServerError : PhotoAnalysisOutcome
    data object ServiceNotConfigured : PhotoAnalysisOutcome
    data object Cancelled : PhotoAnalysisOutcome
}
