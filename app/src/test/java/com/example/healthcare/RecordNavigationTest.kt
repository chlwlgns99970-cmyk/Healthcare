package com.example.healthcare

import androidx.navigation3.runtime.NavKey
import com.example.healthcare.ui.*
import org.junit.Assert.*
import org.junit.Test

class RecordNavigationTest {
    @Test fun homeSaveRemovesRecordWithoutPushingDuplicateHome() {
        val stack = mutableListOf<NavKey>(DashboardRoute, AddRecordRoute)
        assertTrue(finishRecordFlow(stack))
        assertEquals(listOf(DashboardRoute), stack)
        assertFalse(finishRecordFlow(stack))
        assertEquals(listOf(DashboardRoute), stack)
        stack.removeAt(stack.lastIndex) // system/gesture back exits the root
        assertFalse(stack.contains(AddRecordRoute))
    }

    @Test fun searchFromRecommendationReturnsToItsParent() {
        val stack = mutableListOf<NavKey>(DashboardRoute, RecommendationsRoute, AddRecordRoute)
        finishRecordFlow(stack)
        assertEquals(listOf(DashboardRoute, RecommendationsRoute), stack)
    }

    @Test fun recordTabCompletionUsesOneHomeRoot() {
        val stack = mutableListOf<NavKey>(AddRecordRoute)
        finishRecordFlow(stack)
        assertEquals(listOf(DashboardRoute), stack)
    }

    @Test fun reportAndHistoryParentsArePreservedOnCancelOrCompletion() {
        listOf<NavKey>(TodayReportRoute, HistoryRoute).forEach { parent ->
            val stack = mutableListOf<NavKey>(DashboardRoute, parent, AddRecordRoute)
            finishRecordFlow(stack)
            assertEquals(listOf(DashboardRoute, parent), stack)
        }
    }

    @Test fun staleSuccessCannotClearAnotherFlow() {
        val stack = mutableListOf<NavKey>(HistoryRoute)
        assertFalse(finishRecordFlow(stack))
        assertEquals(listOf(HistoryRoute), stack)
    }
}
