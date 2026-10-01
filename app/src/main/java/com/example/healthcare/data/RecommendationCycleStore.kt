package com.example.healthcare.data

import android.content.Context
import androidx.core.content.edit

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
    fun readStableSelection(scope: String): List<String> = emptyList()
    fun writeStableSelection(scope: String, templateIds: List<String>) = Unit
    fun readStablePreference(scope: String): String? = null
    fun writeStablePreference(scope: String, preference: String) = Unit
}

class InMemoryRecommendationCycleStore : RecommendationCycleStore {
    private val values = mutableMapOf<String, RecommendationCycleSnapshot>()
    private val stableSelections = mutableMapOf<String, List<String>>()
    private val stablePreferences = mutableMapOf<String, String>()
    override fun readStablePreference(scope: String): String? = stablePreferences[scope]
    override fun writeStablePreference(scope: String, preference: String) { stablePreferences[scope] = preference }
    override fun read(scope: String): RecommendationCycleSnapshot = values[scope] ?: RecommendationCycleSnapshot()
    override fun write(scope: String, snapshot: RecommendationCycleSnapshot) {
        values[scope] = snapshot
    }
    override fun readStableSelection(scope: String): List<String> = stableSelections[scope].orEmpty()
    override fun writeStableSelection(scope: String, templateIds: List<String>) {
        stableSelections[scope] = templateIds.distinct()
    }
}

class SharedPreferencesRecommendationCycleStore(context: Context) : RecommendationCycleStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    override fun readStablePreference(scope: String): String? = preferences.getString("taste_$scope", null)
    override fun writeStablePreference(scope: String, preference: String) {
        preferences.edit { putString("taste_$scope", preference) }
    }

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

    override fun readStableSelection(scope: String): List<String> =
        preferences.getString("stable_$scope", null)
            ?.split('|')
            ?.filter(String::isNotBlank)
            .orEmpty()

    override fun writeStableSelection(scope: String, templateIds: List<String>) {
        preferences.edit { putString("stable_$scope", templateIds.distinct().joinToString("|")) }
    }

    private companion object {
        const val PREFERENCES_NAME = "recommendation_cycle_v2"
    }
}
