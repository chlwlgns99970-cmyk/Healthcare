package com.example.healthcare

import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.RecordCompletion
import com.example.healthcare.ui.viewmodel.RecordSaveConfirmationViewModel
import org.junit.Assert.*
import org.junit.Test

class RecordSaveConfirmationTest {
    private fun saved(time: Long = 1) = RecordCompletion(MealRecord(date="2026-10-06",time="12:00",
        mealType=MealType.LUNCH,foodName="김밥",calories=296,createdAtEpochMillis=time,updatedAtEpochMillis=time))

    @Test fun noSuccessCallbackMeansNoPopupOrNavigation() {
        val gate=RecordSaveConfirmationViewModel()
        assertNull(gate.pending.value);assertNull(gate.confirm());assertNull(gate.completed.value)
    }
    @Test fun savedRecordWaitsForExplicitConfirmation() {
        val gate=RecordSaveConfirmationViewModel();val event=saved()
        assertTrue(gate.saved(event));assertEquals(event,gate.pending.value)
        assertNull(gate.completed.value);assertEquals(event,gate.confirm())
        assertNull(gate.pending.value);assertEquals(296,gate.completed.value!!.record.calories)
    }
    @Test fun repeatedSuccessCollectionDoesNotOfferTwoPopups() {
        val gate=RecordSaveConfirmationViewModel();val event=saved()
        assertTrue(gate.saved(event));repeat(10) { assertFalse(gate.saved(event)) }
        assertEquals(event,gate.pending.value)
        gate.confirm();repeat(10) { assertFalse(gate.saved(event)) };assertNull(gate.pending.value)
    }
    @Test fun rapidConfirmationOnlyNavigatesOnce() {
        val gate=RecordSaveConfirmationViewModel();gate.saved(saved());var navigation=0
        repeat(10) { if(gate.confirm()!=null)navigation++ }
        assertEquals(1,navigation)
    }
    @Test fun genuinelyNewSaveCanShowAnotherConfirmation() {
        val gate=RecordSaveConfirmationViewModel();gate.saved(saved());gate.confirm()
        assertTrue(gate.saved(saved(2)));assertEquals(saved(2),gate.confirm())
    }
    @Test fun leavingCompletionDoesNotReplayConsumedSuccess() {
        val gate=RecordSaveConfirmationViewModel();val event=saved()
        gate.saved(event);gate.confirm();gate.clearCompleted()
        assertNull(gate.completed.value);assertFalse(gate.saved(event));assertNull(gate.pending.value)
    }
    @Test fun editUsesSameOneTimeAcknowledgementWithoutChangingRecord() {
        val gate=RecordSaveConfirmationViewModel();val event=saved().copy(edited=true)
        assertTrue(gate.saved(event));assertTrue(gate.pending.value!!.edited)
        assertEquals(event,gate.confirm());assertEquals(event.record,gate.completed.value!!.record)
    }
}
