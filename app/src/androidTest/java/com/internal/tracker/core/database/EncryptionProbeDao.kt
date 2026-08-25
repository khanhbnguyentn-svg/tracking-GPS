package com.internal.tracker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EncryptionProbeDao {
    @Insert
    suspend fun insert(entity: EncryptionProbeEntity)

    @Query("SELECT value FROM encryption_probe WHERE id = :id")
    suspend fun value(id: Int): String?
}
