package com.example.healthcare

import com.example.healthcare.domain.FoodMetadata
import com.example.healthcare.domain.FoodMetadataPolicy
import com.example.healthcare.data.FoodMetadataStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FoodMetadataPolicyTest {
    @After fun restore() { FoodMetadataPolicy.install(emptyList()) }
    private fun row(id: String = "exact-1") = FoodMetadata(id, sourceReference = "https://official.example/menu/1", checkedAt = "2026-10-04")
    @Test fun exactIdentityOnlyAndUnknownRemainsUnknown() {
        FoodMetadataPolicy.install(listOf(row()))
        assertNotNull(FoodMetadataPolicy.lookup("exact-1"))
        assertNull(FoodMetadataPolicy.lookup("same-name-other-id"))
        assertFalse(requireNotNull(FoodMetadataPolicy.lookup("exact-1")).allergenInfoComplete)
    }
    @Test fun partialEvidenceCannotBecomeComplete() {
        assertThrows(IllegalArgumentException::class.java) {
            FoodMetadataPolicy.install(listOf(row().copy(allergenInfoComplete = true, allergenStatus = "PARTIAL_INGREDIENT_EVIDENCE")))
        }
    }
    @Test fun provenanceAndUniqueIdentityAreRequired() {
        assertThrows(IllegalArgumentException::class.java) { FoodMetadataPolicy.install(listOf(row().copy(checkedAt = ""))) }
        assertThrows(IllegalArgumentException::class.java) { FoodMetadataPolicy.install(listOf(row(), row())) }
    }
    @Test fun csvEscapesAndEmptyFieldsRemainDistinct() {
        assertEquals(listOf("a", "official, ingredient", "", "a\"b"), FoodMetadataStore.csv("a,\"official, ingredient\",,\"a\"\"b\""))
    }
    @Test fun completeExactLabelsReplaceUnknownWhilePartialEvidenceRetainsIt() {
        FoodMetadataPolicy.install(listOf(row().copy(allergens = setOf("우유"), allergenInfoComplete = true,
            allergenStatus = "CONFIRMED_LABEL")))
        assertEquals(setOf("우유"),FoodMetadataPolicy.allergenTags(setOf("UNKNOWN"),listOf("exact-1")))
        assertEquals(setOf("UNKNOWN","우유"),FoodMetadataPolicy.allergenTags(setOf("UNKNOWN"),listOf("exact-1","missing")))
    }
    @Test fun conflictingDeclarationsCannotCertifyNoIntersection() {
        FoodMetadataPolicy.install(listOf(row().copy(allergens = setOf("밀"), allergenStatus = "PARTIAL_CONFLICT")))
        assertTrue("UNKNOWN" in FoodMetadataPolicy.allergenTags(emptySet(),listOf("exact-1")))
    }
    @Test fun unverifiedTemplateTagsCannotConfirmContainmentOrACompleteFoodLabel() {
        FoodMetadataPolicy.install(listOf(row().copy(allergens = setOf("밀"),
            allergenStatus = "PARTIAL_INGREDIENT_EVIDENCE")))
        assertEquals(setOf("밀", "UNKNOWN"),
            FoodMetadataPolicy.allergenTags(setOf("밀", "대두"), listOf("exact-1")))
        assertEquals(setOf("UNKNOWN"), FoodMetadataPolicy.allergenTags(setOf("밀"), listOf("missing")))
    }
}
