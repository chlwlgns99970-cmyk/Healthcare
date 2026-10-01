package com.example.healthcare

import com.example.healthcare.data.InMemoryRecommendationCycleStore
import com.example.healthcare.data.RecommendationCyclePolicy
import com.example.healthcare.data.RecommendationCycleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlanRecommendationCycleTest {
    @Test
    fun everyEligibleTemplateAppearsOnceBeforeCycleRestart() {
        val eligible = (1..292).map { "meal-$it" }
        var snapshot = RecommendationCycleSnapshot()
        val shown = mutableListOf<String>()
        repeat(98) {
            val result = RecommendationCyclePolicy.select(eligible, snapshot, limit = 3)
            shown += result.selectedTemplateIds
            snapshot = result.updatedSnapshot
        }
        assertEquals(292, shown.size)
        assertEquals(292, shown.distinct().size)
        assertEquals(eligible.toSet(), shown.toSet())
    }

    @Test
    fun cycleRestartsOnlyAfterExhaustionAndDoesNotImmediatelyRepeatLastItem() {
        val eligible = listOf("a", "b", "c")
        val exhausted = RecommendationCycleSnapshot(eligible.toSet(), lastShownTemplateId = "c")
        val result = RecommendationCyclePolicy.select(eligible, exhausted, limit = 1)
        assertTrue(result.cycleRestarted)
        assertEquals(listOf("a"), result.selectedTemplateIds)
        assertFalse("c" in result.selectedTemplateIds)
    }

    @Test
    fun restoredSeenStateContinuesWithoutRepeatingAfterRepositoryRecreation() {
        val store = InMemoryRecommendationCycleStore()
        val eligible = (1..8).map { "meal-$it" }
        val first = RecommendationCyclePolicy.select(eligible, store.read("LUNCH"), 3)
        store.write("LUNCH", first.updatedSnapshot)
        val afterRestart = RecommendationCyclePolicy.select(eligible, store.read("LUNCH"), 3)
        assertTrue(first.selectedTemplateIds.intersect(afterRestart.selectedTemplateIds.toSet()).isEmpty())
    }

    @Test
    fun conditionChangeReconcilesSeenIdsAndNewCandidateIsUnseen() {
        val result = RecommendationCyclePolicy.select(
            listOf("a", "c", "new"),
            RecommendationCycleSnapshot(setOf("a", "b"), "b"),
            limit = 3
        )
        assertEquals(listOf("c", "new"), result.selectedTemplateIds)
        assertEquals(setOf("a", "c", "new"), result.updatedSnapshot.seenTemplateIds)
    }

    @Test
    fun zeroAndSingleCandidateStatesAreExplicit() {
        assertTrue(RecommendationCyclePolicy.select(emptyList(), RecommendationCycleSnapshot(), 3)
            .selectedTemplateIds.isEmpty())
        val first = RecommendationCyclePolicy.select(listOf("only"), RecommendationCycleSnapshot(), 3)
        val second = RecommendationCyclePolicy.select(listOf("only"), first.updatedSnapshot, 3)
        assertEquals(listOf("only"), first.selectedTemplateIds)
        assertTrue(second.selectedTemplateIds.isEmpty())
    }

    @Test
    fun stableDailySelectionKeepsOrderAcrossStoreReads() {
        val store = InMemoryRecommendationCycleStore()
        store.writeStableSelection("2026-09-30_LUNCH", listOf("meal-2", "meal-1", "meal-3"))

        assertEquals(
            listOf("meal-2", "meal-1", "meal-3"),
            store.readStableSelection("2026-09-30_LUNCH")
        )
    }
}
