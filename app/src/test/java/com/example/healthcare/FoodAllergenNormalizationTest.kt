package com.example.healthcare

import com.example.healthcare.domain.FoodAllergenPolicy
import org.junit.Assert.*
import org.junit.Test

class FoodAllergenNormalizationTest {
    @Test fun aliasesReuseExistingCanonicalCauses() {
        assertEquals("달걀", FoodAllergenPolicy.canonicalize("난류"))
        assertEquals("견과류", FoodAllergenPolicy.canonicalize("잣"))
        assertEquals("생선", FoodAllergenPolicy.canonicalize("고등어"))
        assertEquals("조개류", FoodAllergenPolicy.canonicalize("전복"))
    }
    @Test fun compoundDeclarationsMatchTokens() {
        assertTrue(FoodAllergenPolicy.matches("난류(계란)", "달걀"))
        assertTrue(FoodAllergenPolicy.matches("우유·대두", "우유"))
        assertTrue(FoodAllergenPolicy.matches("밀, 대두", "밀"))
    }
    @Test fun substringsAreNotEvidence() {
        assertFalse(FoodAllergenPolicy.matches("메밀", "밀"))
        assertFalse(FoodAllergenPolicy.matches("밀크티향", "밀"))
        assertFalse(FoodAllergenPolicy.matches("게살향료", "게"))
        assertFalse(FoodAllergenPolicy.matches("UNKNOWN", "우유"))
    }
    @Test fun officialStarbucksEggLabelReusesExistingEggCause() {
        assertEquals("달걀", FoodAllergenPolicy.canonicalize("알류"))
        assertTrue(FoodAllergenPolicy.matches("땅콩@대두@우유@알류@밀", "달걀"))
    }
}
