package com.internal.tracker.tracking

import com.google.android.gms.location.Priority

object LocationPriorityPolicy {
    fun forMode(mode: MovementMode): Int = when (mode) {
        MovementMode.IDLE,
        MovementMode.MOVING,
        MovementMode.STOP_CANDIDATE,
        -> Priority.PRIORITY_HIGH_ACCURACY
    }
}
