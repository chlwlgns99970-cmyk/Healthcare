package com.example.healthcare.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.healthcare.domain.RecordCompletion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI acknowledgement only; accepts the existing callback after a successful database write. */
class RecordSaveConfirmationViewModel : ViewModel() {
    private val _pending = MutableStateFlow<RecordCompletion?>(null)
    val pending = _pending.asStateFlow()
    private val _completed = MutableStateFlow<RecordCompletion?>(null)
    val completed = _completed.asStateFlow()
    private var lastAcknowledged: RecordCompletion? = null

    fun saved(value: RecordCompletion): Boolean {
        if (lastAcknowledged == value) return false
        return _pending.compareAndSet(null, value)
    }

    /** Clear before navigation so rapid confirmation taps cannot navigate twice. */
    fun confirm(): RecordCompletion? {
        val value = _pending.value ?: return null
        if (!_pending.compareAndSet(value, null)) return null
        _completed.value = value
        lastAcknowledged = value
        return value
    }

    fun clearCompleted() { _completed.value = null }
}
