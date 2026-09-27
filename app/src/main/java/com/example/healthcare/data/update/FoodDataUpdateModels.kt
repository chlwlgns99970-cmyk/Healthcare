package com.example.healthcare.data.update

import com.example.healthcare.data.entity.FoodItem
import java.security.MessageDigest

enum class FoodDataUpdateStatus { IDLE, CHECKING, UP_TO_DATE, UPDATED, SNAPSHOT_ONLY, FAILED }

data class FoodDataUpdateState(
    val bundledVersion: String = "2026-08-28",
    val activeVersion: String = bundledVersion,
    val lastCheckedAt: Long? = null,
    val lastUpdatedAt: Long? = null,
    val status: FoodDataUpdateStatus = FoodDataUpdateStatus.IDLE,
    val message: String = "번들 음식 데이터를 사용 중"
)

data class FoodDataUpdateCandidate(
    val sourceId: String,
    val version: String,
    val items: List<FoodItem>? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val sha256: String? = null
)

interface FoodDataUpdateSource {
    suspend fun check(currentVersion: String): FoodDataUpdateCandidate?
}

data class FoodDataValidationResult(val valid: Boolean, val errors: List<String>)

object FoodDataUpdateValidator {
    fun validate(
        candidate: FoodDataUpdateCandidate,
        currentVersion: String,
        currentRowCount: Int,
        minimumRetainedRatio: Double = 0.70
    ): FoodDataValidationResult {
        val rows = candidate.items.orEmpty()
        val errors = buildList {
            if (!candidate.version.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) add("source version 없음")
            if (candidate.version <= currentVersion) add("이전 데이터보다 오래되었거나 같은 버전")
            if (rows.isEmpty()) add("행이 없음")
            if (currentRowCount > 0 && rows.size < currentRowCount * minimumRetainedRatio) add("row count 급감")
            if (rows.map(FoodItem::id).distinct().size != rows.size) add("duplicate food ids")
            if (rows.map { "${it.sourceType}|${it.sourceFoodCode}" }.distinct().size != rows.size) {
                add("duplicate food codes")
            }
            val malformed = rows.count { row ->
                row.sourceFoodCode.isBlank() || row.name.isBlank() || row.normalizedName.isBlank() ||
                    row.referenceAmount <= 0.0 || row.unit.isBlank() || row.energyKcal < 0.0 ||
                    !row.referenceAmount.isFinite() || !row.energyKcal.isFinite()
            }
            if (rows.isNotEmpty() && malformed.toDouble() / rows.size > 0.01) add("malformed row 비율 초과")
            candidate.sha256?.let { expected ->
                val actual = sha256(rows)
                if (!expected.equals(actual, ignoreCase = true)) add("file hash 불일치")
            }
        }
        return FoodDataValidationResult(errors.isEmpty(), errors)
    }

    fun sha256(items: List<FoodItem>): String {
        val canonical = items.sortedBy(FoodItem::id).joinToString("\n") {
            listOf(it.id, it.sourceFoodCode, it.name, it.referenceAmount, it.unit, it.energyKcal, it.dataVersion)
                .joinToString("|")
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
