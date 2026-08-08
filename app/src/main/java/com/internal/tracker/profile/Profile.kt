package com.internal.tracker.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.internal.tracker.config.Scheme
import com.internal.tracker.config.TlsMode

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val port: Int,
    val scheme: String,
    val intervalSeconds: Int,
    val tlsMode: String,
    val active: Boolean = false,
)

data class ProfileSecret(
    val host: String,
    val certificatePin: String? = null,
    val customCa: ByteArray? = null,
    val ingestToken: String? = null,
)

data class Profile(
    val id: Long,
    val name: String,
    val host: String,
    val port: Int,
    val scheme: Scheme,
    val intervalSeconds: Int,
    val tlsMode: TlsMode,
    val certificatePin: String?,
    val customCa: ByteArray?,
    val active: Boolean,
    val ingestToken: String? = null,
)

interface ProfileSecrets {
    fun get(id: Long): ProfileSecret?
    fun put(id: Long, value: ProfileSecret)
    fun delete(id: Long)
}
