package com.example.healthcare.ui.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owned by the saving ViewModel; acknowledgement consumes the pending action once. */
class SaveAcknowledgement<T : Any> {
    private val state = MutableStateFlow<T?>(null)
    val pending = state.asStateFlow()
    val isPending: Boolean get() = state.value != null
    fun saved(action: T): Boolean = state.compareAndSet(null, action)
    fun confirm(): T? {
        val action = state.value ?: return null
        return if (state.compareAndSet(action, null)) action else null
    }
}
