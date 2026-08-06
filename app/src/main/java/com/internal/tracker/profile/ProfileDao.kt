package com.internal.tracker.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE active = 1 LIMIT 1")
    suspend fun getActive(): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Query("UPDATE profiles SET active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE profiles SET active = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    suspend fun activate(id: Long) {
        deactivateAll()
        setActive(id)
    }
}
