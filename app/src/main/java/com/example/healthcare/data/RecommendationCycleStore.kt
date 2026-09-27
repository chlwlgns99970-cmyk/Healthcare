package com.example.healthcare.data

import android.content.Context

data class RecommendationCycleSnapshot(
    val seenTemplateIds: Set<String> = emptySet(),
    val lastShownTemplateId: String? = null
)

data class RecommendationCycleSelection(
    val selectedTemplateIds: List<String>,
    val updatedSnapshot: RecommendationCycleSnapshot,
    val cycleRestarted: Boolean,
    val hasMore: Boolean
)

/** Stable-template-ID based cycle selection shared by the persisted and test stores. */
object RecommendationCyclePolicy {
    fun select(
        eligibleTemplateIds: List<String>,
        snapshot: RecommendationCycleSnapshot,
        limit: Int
    ): RecommendationCycleSelection {
        if (limit <= 0 || eligibleTemplateIds.isEmpty()) {
            return RecommendationCycleSelection(
                selectedTemplateIds = emptyList(),
                updatedSnapshot = RecommendationCycleSnapshot(),
                cycleRestarted = false,
                hasMore = false
            )
        }
        val eligible = eligibleTemplateIds.distinct()
        val eligibleSet = eligible.toSet()
        val reconciledSeen = snapshot.seenTemplateIds.intersect(eligibleSet)
        val unseen = eligible.filterNot(reconciledSeen::contains)
        val restart = unseen.isEmpty()
        val pool = if (restart) {
            if (eligible.size == 1 && eligible.first() == snapshot.lastShownTemplateId) emptyList()
            else eligible.filterNot { it == snapshot.lastShownTemplateId }
        } else {
            unseen
        }
        val selected = pool.take(limit)
        val seenAfter = (if (restart) emptySet() else reconciledSeen) + selected
        return RecommendationCycleSelection(
            selectedTemplateIds = selected,
            updatedSnapshot = RecommendationCycleSnapshot(
                seenTemplateIds = seenAfter,
                lastShownTemplateId = selected.lastOrNull() ?: snapshot.lastShownTemplateId
            ),
            cycleRestarted = restart && selected.isNotEmpty(),
            hasMore = pool.size > selected.size
        )
    }
}

interface RecommendationCycleStore {
    fun read(scope: String): RecommendationCycleSnapshot
    fun write(scope: String, snapshot: RecommendationCycleSnapshot)
}

class InMemoryRecommendationCycleStore : RecommendationCycleStore {
    private val values = mutableMapOf<String, RecommendationCycleSnapshot>()
    override fun read(scope: String): RecommendationCycleSnapshot = values[scope] ?: RecommendationCycleSnapshot()
    override fun write(scope: String, snapshot: RecommendationCycleSnapshot) {
        values[scope] = snapshot
    }
}

class SharedPreferencesRecommendationCycleStore(context: Context) : RecommendationCycleStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(scope: String): RecommendationCycleSnapshot = RecommendationCycleSnapshot(
        seenTemplateIds = preferences.getStringSet("seen_$scope", emptySet()).orEmpty().toSet(),
        lastShownTemplateId = preferences.getString("last_$scope", null)
    )

    override fun write(scope: String, snapshot: RecommendationCycleSnapshot) {
        preferences.edit()
            .putStringSet("seen_$scope", snapshot.seenTemplateIds)
            .putString("last_$scope", snapshot.lastShownTemplateId)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "recommendation_cycle_v2"
    }
}
