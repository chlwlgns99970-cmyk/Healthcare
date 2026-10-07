package com.example.healthcare

import com.example.healthcare.data.model.MealType
import com.example.healthcare.ui.viewmodel.AddRecordUiState
import com.example.healthcare.ui.viewmodel.draftValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Draft comparisons must cover input that does not yet make a valid meal record. */
class DraftFieldComparisonTest {
    private val empty = AddRecordUiState(
        date = LocalDate.of(2026, 10, 4),
        time = LocalTime.of(12, 0)
    )

    @Test fun unnamedManualAmountIsStillAnUnsavedDraft() {
        val typed = empty.copy(servingAmount = "150")
        assertNotEquals(empty.draftValues(), typed.draftValues())
        assertEquals(empty.draftValues(), typed.copy(servingAmount = "").draftValues())
    }

    @Test fun manualUnitWithoutNameOrCaloriesIsStillAnUnsavedDraft() {
        assertNotEquals(empty.draftValues(), empty.copy(servingUnit = "ml").draftValues())
    }

    @Test fun clearingSelectedFoodDoesNotEraseEvidenceOfAnUnsavedChange() {
        val selected = empty.copy(foodName = "검증 김밥", calories = "296", servingAmount = "216",
            servingUnit = "g", foodQuantity = "1", foodQuantityUnit = "줄")
        val cleared = selected.copy(foodName = "", calories = "", servingAmount = "", servingUnit = "",
            foodQuantity = "", foodQuantityUnit = "")
        assertNotEquals(selected.draftValues(), cleared.draftValues())
    }

    @Test fun changedMealTimeIsProtectedAndRevertingItIsClean() {
        val changed = empty.copy(time = LocalTime.of(13, 30))
        assertNotEquals(empty.draftValues(), changed.draftValues())
        assertEquals(empty.draftValues(), changed.copy(time = empty.time).draftValues())
    }

    @Test fun entryDateAndMealContextCanBeTheCleanBaselineWithoutHidingLaterEdits() {
        val fromHistoryDate = LocalDate.of(2026, 10, 1)
        val baseline = empty.copy(date = fromHistoryDate, mealType = MealType.DINNER)
        val contextualEntry = empty.copy(date = fromHistoryDate, mealType = MealType.DINNER)
        assertEquals(baseline.draftValues(), contextualEntry.draftValues())
        assertNotEquals(baseline.draftValues(), contextualEntry.copy(mealType = MealType.LUNCH).draftValues())
        assertNotEquals(baseline.draftValues(), contextualEntry.copy(date = fromHistoryDate.plusDays(1)).draftValues())
    }
}
