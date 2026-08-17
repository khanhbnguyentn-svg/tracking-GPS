package com.internal.tracker.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsGapDetectorTest {
    private val detector = GpsGapDetector()

    @Test
    fun opensAtThirtySecondsAndDoesNotDuplicate() {
        var state = detector.onStarted(nowElapsed = 0, gapAlreadyOpen = false)
        assertTrue(detector.onTick(state, 29_999).actions.isEmpty())

        val opened = detector.onTick(state, 30_000)
        assertEquals(listOf(GapAction.OPEN), opened.actions)
        state = opened.state
        assertTrue(detector.onTick(state, 40_000).actions.isEmpty())
    }

    @Test
    fun recoversOpenGapAndThrottlesRegistration() {
        val open = GapState(lastCallbackElapsed = 0, gapOpen = true, lastRecoveryAttemptElapsed = 30_000)
        assertTrue(detector.onTick(open, 329_999).actions.isEmpty())

        val retry = detector.onTick(open, 330_000)
        assertEquals(listOf(GapAction.RETRY_REGISTRATION), retry.actions)
        val recovered = detector.onCallback(retry.state, 331_000)
        assertEquals(listOf(GapAction.RECOVER), recovered.actions)
        assertFalse(recovered.state.gapOpen)
        assertEquals(331_000, recovered.state.lastCallbackElapsed)
    }

    @Test
    fun everyCallbackMovesTheDeadline() {
        var state = detector.onStarted(10_000, false)
        state = detector.onCallback(state, 20_000).state
        assertTrue(detector.onTick(state, 49_999).actions.isEmpty())
        assertEquals(listOf(GapAction.OPEN), detector.onTick(state, 50_000).actions)
    }

    @Test
    fun startsWithPersistedOpenGapWithoutOpeningAnother() {
        val state = detector.onStarted(50_000, true)
        assertTrue(state.gapOpen)
        assertTrue(detector.onTick(state, 60_000).actions.isEmpty())
    }
}
