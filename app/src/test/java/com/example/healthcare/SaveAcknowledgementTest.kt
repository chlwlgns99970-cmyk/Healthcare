package com.example.healthcare

import com.example.healthcare.ui.viewmodel.SaveAcknowledgement
import org.junit.Assert.*
import org.junit.Test

class SaveAcknowledgementTest {
    @Test fun everySettingsActionWaitsAndConsumesOnce() {
        val gate = SaveAcknowledgement<String>()
        for (action in listOf("body", "energy", "goal", "preference", "photo")) {
            assertNull(gate.pending.value)
            assertNull(gate.confirm())
            assertTrue(gate.saved(action))
            repeat(10) { assertFalse(gate.saved(action)) }
            assertEquals(action, gate.pending.value)
            assertEquals(action, gate.confirm())
            repeat(10) { assertNull(gate.confirm()) }
        }
    }
    @Test fun concurrentAcknowledgementsExecuteOnlyOnce() {
        val gate = SaveAcknowledgement<Unit>()
        gate.saved(Unit)
        val confirmations = java.util.concurrent.atomic.AtomicInteger()
        val threads = List(16) { Thread { if (gate.confirm() != null) confirmations.incrementAndGet() } }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        assertEquals(1, confirmations.get())
    }
}
