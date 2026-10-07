package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.FoodMetadata
import com.example.healthcare.domain.FoodMetadataPolicy
import com.example.healthcare.ui.viewmodel.RecommendedIngredientUi
import com.example.healthcare.ui.viewmodel.SelectedMealUi
import com.example.healthcare.ui.viewmodel.withIngredientEvidence
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RecommendationAllergyRefreshTest {
    @After fun restore() { FoodMetadataPolicy.install(emptyList()) }
    private fun item(id: String) = RecommendedIngredientUi(1, FoodItem(id, "K-FIND", id, id, id,
        referenceAmount = 100.0, unit = "g", energyKcal = 100.0, servingDescription = "100g 기준",
        dataVersion = "isolated", createdAt = 0, updatedAt = 0), 100.0, 100.0, "g", 100,
        adjustable = false, minimumAmount = null, maximumAmount = null, adjustmentStep = null)
    private fun old() = SelectedMealUi("template", "식사", "공식 자료", listOf(item("old")), 100,
        declaredAllergens = setOf("우유"), matchedAllergens = setOf("우유"), allergenInfoComplete = true)
    private fun label(id: String, allergens: Set<String>) = FoodMetadata(id, allergens = allergens,
        allergenInfoComplete = true, allergenStatus = "CONFIRMED_LABEL",
        sourceReference = "https://official.example/$id", checkedAt = "2026-10-04")

    @Test fun replacingCompleteMealWithUnknownFoodInvalidatesOldDeclaration() {
        FoodMetadataPolicy.install(listOf(label("old", setOf("우유"))))
        val changed = old().withIngredientEvidence(listOf(item("unknown")), setOf("우유"))
        assertFalse(changed.allergenInfoComplete)
        assertTrue(changed.declaredAllergens.isEmpty())
        assertTrue(changed.matchedAllergens.isEmpty())
        assertEquals(100, changed.totalCalories)
    }
    @Test fun replacementUsesCurrentExactLabelAndCurrentConfiguredCauses() {
        FoodMetadataPolicy.install(listOf(label("new", setOf("달걀"))))
        val changed = old().withIngredientEvidence(listOf(item("new")), setOf("달걀", "우유"))
        assertTrue(changed.allergenInfoComplete)
        assertEquals(setOf("달걀"), changed.declaredAllergens)
        assertEquals(setOf("달걀"), changed.matchedAllergens)
    }
    @Test fun excludedIngredientCannotRetainItsPreviousAllergenWarning() {
        FoodMetadataPolicy.install(listOf(label("old", setOf("우유")), label("remaining", setOf("밀"))))
        val changed = old().withIngredientEvidence(listOf(item("old").copy(included = false), item("remaining")), setOf("우유"))
        assertTrue(changed.allergenInfoComplete)
        assertEquals(setOf("밀"), changed.declaredAllergens)
        assertTrue(changed.matchedAllergens.isEmpty())
        assertEquals(100, changed.totalCalories)
    }
}
