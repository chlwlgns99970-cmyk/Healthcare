package com.example.healthcare

import com.example.healthcare.data.InMemoryRecommendationLearningPersistence
import com.example.healthcare.data.RecommendationLearningStore
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.RecommendationLearningPolicy
import org.junit.Assert.*
import org.junit.Test

class RecommendationLearningStoreTest {
    private val favorite = FrequentFood(id = 3, foodName = "김밥", defaultServing = "1줄", calories = 450,
        isFavorite = true, foodItemId = "kimbap-1")
    @Test fun explicitReplacementPersistsBoundedCountAcrossStoreRecreation() {
        val persistence = InMemoryRecommendationLearningPersistence()
        val store = RecommendationLearningStore(persistence) { 1000 }
        repeat(100) { store.recordReplacement("template-1") }
        assertEquals(3, RecommendationLearningStore(persistence).snapshot().rejections.getValue("template-1").count)
    }
    @Test fun historyIsBoundedAndBlankReplacementHasNoEffect() {
        var time = 1L
        val store = RecommendationLearningStore(now = { time++ })
        store.recordReplacement("")
        assertTrue(store.snapshot().rejections.isEmpty())
        repeat(200) { store.recordReplacement("template-$it") }
        assertEquals(RecommendationLearningPolicy.MAX_REJECTIONS, store.snapshot().rejections.size)
        assertFalse("template-0" in store.snapshot().rejections)
    }
    @Test fun resetStoresCutoffAndRetainedFavoriteSuppressionWithoutChangingInputs() {
        val rows = listOf(MealRecord(id = 9, date = "2026-10-02", time = "12:00", mealType = MealType.LUNCH,
            foodName = "김밥", calories = 450, createdAtEpochMillis = 0))
        val store = RecommendationLearningStore(now = { 5000 })
        store.recordReplacement("template-1")
        store.reset(rows, listOf(favorite))
        assertEquals(5000L, store.snapshot().resetAt)
        assertEquals(9L, store.snapshot().resetRecordId)
        assertTrue(store.snapshot().rejections.isEmpty())
        assertEquals(setOf(RecommendationLearningPolicy.favoriteKey(favorite)), store.snapshot().suppressedFavorites)
        assertEquals(1, rows.size); assertTrue(favorite.isFavorite)
    }
    @Test fun retainedFavoriteDoesNotLearnAgainUntilExplicitRemovalAndReaddition() {
        val store = RecommendationLearningStore(now = { 5000 })
        store.reset(emptyList(), listOf(favorite))
        store.favoriteChanged(favorite, true)
        assertFalse(store.snapshot().suppressedFavorites.isEmpty())
        store.favoriteChanged(favorite, false)
        store.favoriteChanged(favorite, true)
        assertTrue(store.snapshot().suppressedFavorites.isEmpty())
    }
    @Test fun newReplacementPreservesPersistedResetCutoffAndFavoriteSuppression() {
        val persistence = InMemoryRecommendationLearningPersistence()
        val first = RecommendationLearningStore(persistence) { 1000 }
        first.reset(emptyList(), listOf(favorite))
        val second = RecommendationLearningStore(persistence) { 2000 }
        second.recordReplacement("new-template")
        assertEquals(1000L, second.snapshot().resetAt)
        assertEquals(setOf(RecommendationLearningPolicy.favoriteKey(favorite)), second.snapshot().suppressedFavorites)
        assertEquals(setOf("new-template"), second.snapshot().rejections.keys)
    }
}
