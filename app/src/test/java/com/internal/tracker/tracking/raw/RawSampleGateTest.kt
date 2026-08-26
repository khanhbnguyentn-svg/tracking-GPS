package com.internal.tracker.tracking.raw

import com.internal.tracker.tracking.model.RawSampleKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSampleGateTest {
    private val gate = RawSampleGate()

    @Test
    fun acceptsOnlyOneOrdinarySamplePerTenSecondWindow() {
        assertTrue(gate.shouldPersist(10_000_000_000L, null, RawSampleKind.ORDINARY))
        assertFalse(gate.shouldPersist(15_000_000_000L, 10_000_000_000L, RawSampleKind.ORDINARY))
        assertTrue(gate.shouldPersist(20_000_000_000L, 10_000_000_000L, RawSampleKind.ORDINARY))
    }

    @Test
    fun rejectsNonIncreasingOrdinaryElapsedTime() {
        assertFalse(gate.shouldPersist(10_000_000_000L, 10_000_000_000L, RawSampleKind.ORDINARY))
        assertFalse(gate.shouldPersist(9_000_000_000L, 10_000_000_000L, RawSampleKind.ORDINARY))
    }

    @Test
    fun persistsBoundarySamplesRegardlessOfOrdinaryGate() {
        assertTrue(gate.shouldPersist(10_000_000_000L, 10_000_000_000L, RawSampleKind.START_BOUNDARY))
        assertTrue(gate.shouldPersist(9_000_000_000L, 10_000_000_000L, RawSampleKind.END_BOUNDARY))
    }
}
