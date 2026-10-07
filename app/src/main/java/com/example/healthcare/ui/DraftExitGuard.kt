package com.example.healthcare.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** The active form supplies its real draft comparison and a reversible discard action. */
data class DraftExitGuard(
    val hasChanges: Boolean,
    val isSaving: Boolean = false,
    val discard: () -> Unit = {}
)

val LocalDraftExitGuardRegistration = staticCompositionLocalOf<(DraftExitGuard) -> Unit> { {} }

internal enum class TabSelectionDecision { MOVE, CONFIRM_DISCARD, WAIT_FOR_SAVE }
internal fun tabSelectionDecision(guard: DraftExitGuard?): TabSelectionDecision = when {
    guard?.isSaving == true -> TabSelectionDecision.WAIT_FOR_SAVE
    guard?.hasChanges == true -> TabSelectionDecision.CONFIRM_DISCARD
    else -> TabSelectionDecision.MOVE
}

internal fun selectTabRoot(stack: MutableList<androidx.navigation3.runtime.NavKey>, destination: TopLevelDestination) {
    if (stack.size == 1 && stack.single() == destination.route) return
    stack.clear()
    stack.add(destination.route)
}
