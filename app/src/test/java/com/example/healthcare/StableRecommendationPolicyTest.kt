package com.example.healthcare

import com.example.healthcare.data.StableRecommendationPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class StableRecommendationPolicyTest {
    private val ids = setOf("a", "b", "c", "rice")
    @Test fun sameDayKeepsAllThreeAndOrder() {
        assertEquals(listOf("c", "b", "a"), StableRecommendationPolicy.preserve(
            listOf("c", "b", "a"), ids, emptySet(), false, emptySet()))
    }
    @Test fun newPreferenceReplacesOnlyOneUnmatchedChoice() {
        assertEquals(listOf("a", "b"), StableRecommendationPolicy.preserve(
            listOf("a", "b", "c"), ids, setOf("rice"), true, setOf("rice")))
    }
    @Test fun alreadyRepresentedPreferenceKeepsAllValidChoices() {
        assertEquals(listOf("rice", "b", "c"), StableRecommendationPolicy.preserve(
            listOf("rice", "b", "c"), ids, setOf("rice"), true, setOf("rice")))
    }
    @Test fun dislikeOrCalorieInvalidationOnlyRemovesAffectedIds() {
        assertEquals(listOf("a", "c"), StableRecommendationPolicy.preserve(
            listOf("a", "b", "c"), ids - "b", emptySet(), false, emptySet()))
    }
    @Test fun exhaustedPreferredCycleDoesNotForceARepeat() {
        assertEquals(listOf("a", "b", "c"), StableRecommendationPolicy.preserve(
            listOf("a", "b", "c"), ids, setOf("rice"), true, emptySet()))
    }
    @Test fun invalidatedSlotIsUsedForNewTasteWithoutDroppingAnotherValidChoice() {
        assertEquals(listOf("a", "b"), StableRecommendationPolicy.preserve(
            listOf("a", "b", "c"), ids - "c", setOf("rice"), true, setOf("rice")))
    }
    @Test fun clearedPreferenceKeepsValidChoicesAndDuplicatesCannotPersist() {
        assertEquals(listOf("a", "b"), StableRecommendationPolicy.preserve(
            listOf("a", "a", "b"), ids, emptySet(), true, emptySet()))
    }
}
