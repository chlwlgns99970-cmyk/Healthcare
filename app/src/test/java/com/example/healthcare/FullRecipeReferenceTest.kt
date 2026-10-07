package com.example.healthcare

import com.example.healthcare.domain.RecipeCaloriePolicy
import com.example.healthcare.domain.RecipeIngredientEstimate
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FullRecipeReferenceTest {
    private fun row(id: String, complete: Boolean, kind: String = "ORIGINAL") = RecipeIngredientEstimate(
        "food",id,id,"전체 참고 구성","감자 30g","감자",30.0,"potato","감자, 삶은것",77.0,
        "https://www.nics.go.kr/recipe/$id","https://www.nics.go.kr/nutrient/potato","a".repeat(64),
        "2026-10-05",complete,123.0,100.0,"g",kind,"농촌진흥청")
    @After fun reset() { RecipeCaloriePolicy.install(emptyList()) }
    @Test fun completeReferenceDoesNotCompleteOriginalOrMixAmounts() {
        val original=row("original",false)
        val reference=row("reference",true,"REFERENCE_RECIPE").copy(amountGrams=80.0)
        RecipeCaloriePolicy.install(listOf(original),listOf(reference))
        assertFalse(RecipeCaloriePolicy.lookupCompositions("food")[0][0].recipeComplete)
        assertEquals(2,RecipeCaloriePolicy.lookupCompositions("food").size)
        assertEquals(80.0,RecipeCaloriePolicy.lookup("food").single().amountGrams,0.0)
        assertEquals(61.6,RecipeCaloriePolicy.lookup("food").single().estimatedKcal,0.0001)
        assertEquals(123.0,RecipeCaloriePolicy.lookup("food").single().foodReferenceKcal!!,0.0)
    }
    @Test fun completeOriginalRemainsPreferred() {
        RecipeCaloriePolicy.install(listOf(row("original",true)),listOf(row("reference",true,"REFERENCE_RECIPE")))
        assertEquals("original",RecipeCaloriePolicy.lookup("food").single().recipeId)
    }
    @Test fun surveyKindAndSourceRemainExplicit() {
        RecipeCaloriePolicy.install(emptyList(),listOf(row("survey",true,"SURVEY_AVERAGE")))
        assertEquals("SURVEY_AVERAGE",RecipeCaloriePolicy.lookup("food").single().compositionKind)
        assertEquals("농촌진흥청",RecipeCaloriePolicy.lookup("food").single().sourceInstitution)
    }
    @Test fun multipleReferencesAreKeptAsWholeSeparateCompositions() {
        RecipeCaloriePolicy.install(emptyList(),listOf(row("a",false,"REFERENCE_RECIPE"),row("b",true,"REFERENCE_RECIPE")))
        assertEquals(listOf("a","b"),RecipeCaloriePolicy.lookupCompositions("food").map { it.single().recipeId })
        assertEquals("b",RecipeCaloriePolicy.lookup("food").single().recipeId)
    }
    @Test fun unknownKindAndMixedKindsWithinCompositionAreRejected() {
        for (rows in listOf(listOf(row("a",true,"UNKNOWN")),listOf(row("a",true),
            row("a",true,"REFERENCE_RECIPE").copy(ingredientName="소금")))) {
            try { RecipeCaloriePolicy.install(emptyList(),rows);fail("Invalid composition accepted") }
            catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun resetClearsEveryComposition() {
        RecipeCaloriePolicy.install(emptyList(),listOf(row("a",true,"REFERENCE_RECIPE")))
        RecipeCaloriePolicy.install(emptyList())
        assertTrue(RecipeCaloriePolicy.lookup("food").isEmpty())
        assertTrue(RecipeCaloriePolicy.lookupCompositions("food").isEmpty())
    }
}
