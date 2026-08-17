package com.internal.tracker.diagnostics

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticAlertSchedulerPolicyTest {
    @Test fun eachIncidentPhaseHasAStableIndependentKeepKey() {
        assertEquals(
            "diagnostic-alert-gap-1-OPENED",
            DiagnosticAlertWork.uniqueName("gap-1", DiagnosticAlertPhase.OPENED),
        )
        assertEquals(
            "diagnostic-alert-gap-1-RECOVERED",
            DiagnosticAlertWork.uniqueName("gap-1", DiagnosticAlertPhase.RECOVERED),
        )
        assertEquals(ExistingWorkPolicy.KEEP, DiagnosticAlertWork.existingWorkPolicy)
    }
}
