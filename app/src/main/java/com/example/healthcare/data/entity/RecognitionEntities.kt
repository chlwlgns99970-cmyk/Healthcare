package com.example.healthcare.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recognition_sessions",
    indices = [Index(value = ["requestId"], unique = true)]
)
data class RecognitionSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: String,
    val sourceType: String,
    val capturedAt: Long,
    val status: String,
    val createdAt: Long,
    val completedAt: Long? = null
)

@Entity(
    tableName = "recognition_candidates",
    indices = [Index(value = ["sessionId"]), Index(value = ["matchedFoodItemId"])]
)
data class RecognitionCandidate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val detectedName: String,
    val matchedFoodItemId: String? = null,
    val estimatedAmount: Double? = null,
    val unit: String? = null,
    val estimatedKcal: Int? = null,
    val minimumKcal: Int? = null,
    val maximumKcal: Int? = null,
    val confidenceLevel: String? = null,
    val selected: Boolean = false
)
