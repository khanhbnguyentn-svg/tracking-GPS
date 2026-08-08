package com.internal.tracker.profile

import com.internal.tracker.config.ImportedProfile
import com.internal.tracker.config.Scheme
import com.internal.tracker.config.TlsMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepository(
    private val dao: ProfileDao,
    private val secrets: ProfileSecrets,
    private val isTracking: () -> Boolean,
) {
    fun observeAll(): Flow<List<Profile>> = dao.observeAll().map { rows -> rows.mapNotNull(::toProfile) }

    suspend fun get(id: Long): Profile? = dao.get(id)?.let(::toProfile)
    suspend fun active(): Profile? = dao.getActive()?.let(::toProfile)

    suspend fun save(profile: ImportedProfile, customCa: ByteArray? = null): Long {
        val id = dao.insert(
            ProfileEntity(
                name = profile.name,
                port = profile.port,
                scheme = profile.scheme.name,
                intervalSeconds = profile.intervalSeconds,
                tlsMode = profile.tlsMode.name,
            ),
        )
        secrets.put(id, ProfileSecret(profile.host, profile.certificatePin, customCa, profile.ingestToken))
        return id
    }

    suspend fun activate(id: Long): Result<Unit> = runCatching {
        check(!isTracking()) { "Hãy dừng theo dõi trước khi đổi profile" }
        requireNotNull(dao.get(id)) { "Không tìm thấy profile" }
        dao.activate(id)
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        secrets.delete(id)
    }

    private fun toProfile(row: ProfileEntity): Profile? = secrets.get(row.id)?.let { secret ->
        Profile(
            row.id,
            row.name,
            secret.host,
            row.port,
            enumValueOf<Scheme>(row.scheme),
            row.intervalSeconds,
            enumValueOf<TlsMode>(row.tlsMode),
            secret.certificatePin,
            secret.customCa,
            row.active,
            secret.ingestToken,
        )
    }
}
