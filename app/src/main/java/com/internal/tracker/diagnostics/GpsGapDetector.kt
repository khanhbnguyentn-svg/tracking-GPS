package com.internal.tracker.diagnostics

data class GapState(
    val lastCallbackElapsed: Long,
    val gapOpen: Boolean,
    val lastRecoveryAttemptElapsed: Long?,
)

enum class GapAction { OPEN, RECOVER, RETRY_REGISTRATION }

data class GapTransition(val state: GapState, val actions: List<GapAction>)

class GpsGapDetector {
    fun onStarted(nowElapsed: Long, gapAlreadyOpen: Boolean) = GapState(
        lastCallbackElapsed = nowElapsed,
        gapOpen = gapAlreadyOpen,
        lastRecoveryAttemptElapsed = nowElapsed.takeIf { gapAlreadyOpen },
    )

    fun onTick(state: GapState, nowElapsed: Long): GapTransition {
        if (!state.gapOpen && nowElapsed - state.lastCallbackElapsed >= GAP_THRESHOLD_MILLIS) {
            return GapTransition(
                state.copy(gapOpen = true, lastRecoveryAttemptElapsed = nowElapsed),
                listOf(GapAction.OPEN),
            )
        }
        val lastAttempt = state.lastRecoveryAttemptElapsed
        if (state.gapOpen && lastAttempt != null && nowElapsed - lastAttempt >= RECOVERY_RETRY_MILLIS) {
            return GapTransition(
                state.copy(lastRecoveryAttemptElapsed = nowElapsed),
                listOf(GapAction.RETRY_REGISTRATION),
            )
        }
        return GapTransition(state, emptyList())
    }

    fun onCallback(state: GapState, nowElapsed: Long) = GapTransition(
        state = state.copy(
            lastCallbackElapsed = nowElapsed,
            gapOpen = false,
            lastRecoveryAttemptElapsed = null,
        ),
        actions = if (state.gapOpen) listOf(GapAction.RECOVER) else emptyList(),
    )

    companion object {
        const val GAP_THRESHOLD_MILLIS = 30_000L
        const val RECOVERY_RETRY_MILLIS = 300_000L
    }
}
