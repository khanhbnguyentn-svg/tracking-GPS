package com.internal.tracker.tracking.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class StoragePolicyTest {
    @Test fun classifiesExactThresholds() {
        assertEquals(StorageStatus.NORMAL, StoragePolicy.status(500L * 1024 * 1024))
        assertEquals(StorageStatus.WARNING, StoragePolicy.status(500L * 1024 * 1024 - 1))
        assertEquals(StorageStatus.WARNING, StoragePolicy.status(200L * 1024 * 1024))
        assertEquals(StorageStatus.CRITICAL, StoragePolicy.status(200L * 1024 * 1024 - 1))
    }
}
