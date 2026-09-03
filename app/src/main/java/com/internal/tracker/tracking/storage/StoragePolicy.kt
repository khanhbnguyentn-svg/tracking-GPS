package com.internal.tracker.tracking.storage

enum class StorageStatus { NORMAL, WARNING, CRITICAL }

object StoragePolicy {
    private const val WARNING_BYTES = 500L * 1024 * 1024
    private const val CRITICAL_BYTES = 200L * 1024 * 1024

    fun status(freeBytes: Long): StorageStatus = when {
        freeBytes < CRITICAL_BYTES -> StorageStatus.CRITICAL
        freeBytes < WARNING_BYTES -> StorageStatus.WARNING
        else -> StorageStatus.NORMAL
    }
}
