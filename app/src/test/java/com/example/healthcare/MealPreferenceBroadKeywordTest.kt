package com.example.healthcare

import com.example.healthcare.ui.viewmodel.MealPreferenceUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPreferenceBroadKeywordTest {
    @Test
    fun broadDislikeKeywordsWarnWithoutBlockingSave() {
        assertTrue(MealPreferenceUiState(dislikeInput = "국").broadDislikeWarning)
        assertTrue(MealPreferenceUiState(dislikeInput = " 밥 ").broadDislikeWarning)
        assertFalse(MealPreferenceUiState(dislikeInput = "미역국").broadDislikeWarning)
    }
}
