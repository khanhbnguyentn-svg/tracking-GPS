package com.internal.tracker

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectConfigTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("com.internal.tracker", BuildConfig.APPLICATION_ID)
    }
}
