package com.example.healthcare.data

import android.content.Context
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.domain.RecommendationLearningPolicy
import com.example.healthcare.domain.RecommendationLearningSnapshot
import com.example.healthcare.domain.RecommendationRejection
import android.util.Base64

interface RecommendationLearningPersistence {
    fun read(): RecommendationLearningSnapshot
    fun write(snapshot: RecommendationLearningSnapshot)
}

class InMemoryRecommendationLearningPersistence : RecommendationLearningPersistence {
    private var snapshot = RecommendationLearningSnapshot()
    override fun read() = snapshot
    override fun write(snapshot: RecommendationLearningSnapshot) { this.snapshot = snapshot }
}

/** Reuses the existing local recommendation preferences file; no new Room data or network calls. */
class SharedPreferencesRecommendationLearningPersistence(context: Context) : RecommendationLearningPersistence {
    private val preferences = context.getSharedPreferences("recommendation_cycle_v2", Context.MODE_PRIVATE)
    override fun read() = RecommendationLearningSnapshot(
        resetAt = if (preferences.contains("learning_reset_at")) preferences.getLong("learning_reset_at", 0) else null,
        resetRecordId = preferences.getLong("learning_reset_record_id", 0),
        suppressedFavorites = preferences.getStringSet("learning_suppressed_favorites", emptySet()).orEmpty().toSet(),
        rejections = preferences.getStringSet("learning_rejections", emptySet()).orEmpty().mapNotNull { row ->
            runCatching {
                val parts = row.split('|')
                require(parts.size == 3)
                String(Base64.decode(parts[0], Base64.NO_WRAP), Charsets.UTF_8) to
                    RecommendationRejection(parts[1].toInt().coerceIn(1, 3), parts[2].toLong())
            }.getOrNull()
        }.toMap()
    )
    override fun write(snapshot: RecommendationLearningSnapshot) {
        val editor = preferences.edit().putLong("learning_reset_record_id", snapshot.resetRecordId)
            .putStringSet("learning_suppressed_favorites", snapshot.suppressedFavorites)
            .putStringSet("learning_rejections", snapshot.rejections.map { (id, value) ->
                "${Base64.encodeToString(id.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}|${value.count}|${value.updatedAt}"
            }.toSet())
        if (snapshot.resetAt == null) editor.remove("learning_reset_at") else editor.putLong("learning_reset_at", snapshot.resetAt)
        check(editor.commit()) { "추천 학습을 저장하지 못했어요." }
    }
}

class RecommendationLearningStore(
    private val persistence: RecommendationLearningPersistence = InMemoryRecommendationLearningPersistence(),
    private val now: () -> Long = System::currentTimeMillis
) {
    @Synchronized fun snapshot() = persistence.read()

    @Synchronized fun recordReplacement(templateId: String) {
        if (templateId.isBlank()) return
        val current = persistence.read()
        val timestamp = now()
        val previous = current.rejections[templateId]?.takeIf { timestamp - it.updatedAt in 0..(90 * 86_400_000L) }
        val next = current.rejections + (templateId to RecommendationRejection(
            ((previous?.count ?: 0) + 1).coerceAtMost(3), timestamp))
        persistence.write(current.copy(rejections = next.entries.sortedByDescending { it.value.updatedAt }
            .take(RecommendationLearningPolicy.MAX_REJECTIONS).associate { it.toPair() }))
    }

    /** Removing a retained favorite allows a later, explicit re-add to become a new positive choice. */
    @Synchronized fun favoriteChanged(food: FrequentFood, favorite: Boolean) {
        if (favorite) return
        val current = persistence.read()
        val key = RecommendationLearningPolicy.favoriteKey(food)
        if (key in current.suppressedFavorites) persistence.write(current.copy(suppressedFavorites = current.suppressedFavorites - key))
    }

    @Synchronized fun reset(records: List<MealRecord>, favorites: List<FrequentFood>) {
        persistence.write(RecommendationLearningPolicy.reset(records, favorites, now()))
    }
}
