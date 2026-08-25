package com.internal.tracker.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "encryption_probe")
data class EncryptionProbeEntity(
    @PrimaryKey val id: Int,
    val value: String,
)
