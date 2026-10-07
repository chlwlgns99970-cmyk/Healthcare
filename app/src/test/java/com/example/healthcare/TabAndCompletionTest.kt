package com.example.healthcare

import androidx.navigation3.runtime.NavKey
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.RecordCompletion
import com.example.healthcare.domain.RecordedAmountSnapshot
import com.example.healthcare.ui.*
import com.example.healthcare.ui.viewmodel.AddRecordUiState
import com.example.healthcare.ui.viewmodel.draftValues
import org.junit.Assert.*
import org.junit.Test

class TabAndCompletionTest {
    @Test fun retapClearsChildrenOfEveryTab() {
        TopLevelDestination.entries.forEach { tab ->
            val stack = mutableListOf<NavKey>(tab.route, MealPreferenceRoute, TodayReportRoute)
            selectTabRoot(stack, tab)
            assertEquals(listOf(tab.route), stack)
        }
    }
    @Test fun rootRetapIsIdempotentWithoutDuplicate() {
        val stack = mutableListOf<NavKey>(AddRecordRoute)
        repeat(5) { selectTabRoot(stack, TopLevelDestination.ADD) }
        assertEquals(listOf(AddRecordRoute), stack)
    }
    @Test fun otherTabKeepsExistingClearAndSelectPolicy() {
        val stack = mutableListOf<NavKey>(DashboardRoute, TodayReportRoute)
        selectTabRoot(stack, TopLevelDestination.HISTORY)
        assertEquals(listOf(HistoryRoute), stack)
    }
    @Test fun dirtyDraftWaitsForExplicitDiscardAndSavingCannotLeave() {
        assertEquals(TabSelectionDecision.CONFIRM_DISCARD, tabSelectionDecision(DraftExitGuard(true)))
        assertEquals(TabSelectionDecision.WAIT_FOR_SAVE, tabSelectionDecision(DraftExitGuard(true, true)))
        assertEquals(TabSelectionDecision.MOVE, tabSelectionDecision(DraftExitGuard(false)))
    }
    @Test fun realFieldComparisonIgnoresValidationAndRecognizesRevertedInput() {
        val baseline = AddRecordUiState(foodName = "김밥", foodQuantity = "1", foodQuantityUnit = "줄")
        assertEquals(baseline.draftValues(), baseline.copy(saveError = "실패").draftValues())
        assertNotEquals(baseline.draftValues(), baseline.copy(foodQuantity = "0.5").draftValues())
        assertEquals(baseline.draftValues(), baseline.copy(foodQuantity = "0.5").copy(foodQuantity = "1").draftValues())
    }
    private fun record() = MealRecord(date = "2026-10-02", time = "12:00", mealType = MealType.LUNCH,
        foodName = "검증 김밥", calories = 296, servingAmount = 216.0, servingUnit = "g",
        portionPresetId = RecordedAmountSnapshot(1.0, "줄", 216.0, "g").presetId,
        portionDisplayLabel = "1줄")
    @Test fun completionUsesSavedFoodAndPortableAmountSnapshot() {
        val completion = RecordCompletion(record())
        assertEquals("기록했어요", completion.title)
        assertEquals("검증 김밥", completion.record.foodName)
        assertEquals("1줄", completion.amountLabel)
        assertEquals("296 kcal", completion.calorieLabel)
    }
    @Test fun unknownCaloriesNeverDisplayZero() {
        assertEquals("칼로리 정보 없음", RecordCompletion(record().copy(calories = 0)).calorieLabel)
    }
    @Test fun modificationIsDistinctFromNewRecord() {
        assertEquals("수정했어요", RecordCompletion(record().copy(id = 12), true).title)
    }
}
