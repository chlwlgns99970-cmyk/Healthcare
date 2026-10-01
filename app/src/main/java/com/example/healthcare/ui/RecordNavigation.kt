package com.example.healthcare.ui

import androidx.navigation3.runtime.NavKey

/** Complete only the active record flow. Its parent remains the return destination. */
internal fun finishRecordFlow(backStack: MutableList<NavKey>): Boolean {
    if (backStack.lastOrNull() != AddRecordRoute) return false
    backStack.removeAt(backStack.lastIndex)
    if (backStack.isEmpty()) backStack.add(DashboardRoute)
    return true
}
