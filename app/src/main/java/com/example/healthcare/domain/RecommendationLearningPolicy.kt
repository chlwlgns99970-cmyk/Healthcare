package com.example.healthcare.domain

import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.RecordSource
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow

data class RecommendationRejection(val count: Int, val updatedAt: Long)

data class RecommendationLearningSnapshot(
    val resetAt: Long? = null,
    val resetRecordId: Long = 0,
    val suppressedFavorites: Set<String> = emptySet(),
    val rejections: Map<String, RecommendationRejection> = emptyMap()
)

/** Only confirmed local choices are used. No demographic or health inference is performed. */
object RecommendationLearningPolicy {
    const val MAX_SCORE = 4.0
    const val MIN_SCORE = -2.0
    const val MAX_RECORDS = 256
    const val MAX_REJECTIONS = 128
    private const val DAY = 86_400_000L
    private const val WINDOW = 90 * DAY

    fun favoriteKey(food: FrequentFood): String = food.foodItemId?.let { "food:$it" }
        ?: "saved:${food.id}:${MealRecommendationEngine.normalizeFoodName(food.foodName)}"

    fun reset(records: List<MealRecord>, favorites: List<FrequentFood>, now: Long) =
        RecommendationLearningSnapshot(resetAt = now, resetRecordId = records.maxOfOrNull { it.id } ?: 0,
            suppressedFavorites = favorites.filter { it.isFavorite }.map(::favoriteKey).toSet())

    fun bounded(value: Double): Double = value.takeIf(Double::isFinite)?.coerceIn(MIN_SCORE, MAX_SCORE) ?: 0.0

    fun scores(seeds: List<RecommendationSeed>, records: List<MealRecord>, favorites: List<FrequentFood>,
        snapshot: RecommendationLearningSnapshot, now: Long): Map<String, Double> {
        val positives = mutableMapOf<String, Double>()
        fun add(name: String, amount: Double) {
            val key = MealRecommendationEngine.normalizeFoodName(name)
            if (key.isNotBlank()) positives[key] = ((positives[key] ?: 0.0) + amount).coerceAtMost(MAX_SCORE)
        }
        favorites.filter { it.isFavorite && favoriteKey(it) !in snapshot.suppressedFavorites }
            .distinctBy(::favoriteKey).forEach { add(it.foodName, 3.0) }
        fun recordTime(record: MealRecord): Long = record.createdAtEpochMillis.takeIf { it > 0 }
            ?: runCatching { LocalDate.parse(record.date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrDefault(0)
        records.asSequence().map { it to recordTime(it) }.filter { (record, time) ->
            time in (now - WINDOW)..now &&
                (snapshot.resetAt == null || (record.id > snapshot.resetRecordId && time > snapshot.resetAt))
        }.sortedByDescending { it.second }.take(MAX_RECORDS).forEach { (record, time) ->
            val decay = 0.5.pow((now - time).toDouble() / (30 * DAY))
            // Connecting a recommendation to an actual record adds only a small extra signal.
            add(record.foodName, (if (record.source == RecordSource.RECOMMENDATION) 2.25 else 2.0) * decay)
        }
        return seeds.associate { seed ->
            val positive = (seed.ingredientNames + seed.template.name)
                .maxOfOrNull { positives[MealRecommendationEngine.normalizeFoodName(it)] ?: 0.0 } ?: 0.0
            val rejection = snapshot.rejections[seed.template.id]
            val negative = if (rejection == null || now - rejection.updatedAt !in 0..WINDOW) 0.0 else
                (rejection.count * 0.75).coerceAtMost(-MIN_SCORE) *
                    0.5.pow((now - rejection.updatedAt).toDouble() / (14 * DAY))
            seed.template.id to bounded(positive - negative)
        }
    }
}
