package com.internal.tracker.profile

import com.internal.tracker.config.ImportedProfile
import com.internal.tracker.config.Scheme
import com.internal.tracker.config.TlsMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryTest {
    private val dao = FakeProfileDao()
    private val secrets = FakeProfileSecrets()

    @Test
    fun savingAndActivatingKeepsOneActiveProfile() = runTest {
        val repository = ProfileRepository(dao, secrets) { false }
        val first = repository.save(profile("First"))
        val second = repository.save(profile("Second"))

        repository.activate(first)
        repository.activate(second)

        assertFalse(dao.rows.getValue(first).active)
        assertTrue(dao.rows.getValue(second).active)
        assertEquals("a.example", repository.get(second)!!.host)
    }

    @Test
    fun activationIsRejectedWhileTracking() = runTest {
        val repository = ProfileRepository(dao, secrets) { true }
        val id = repository.save(profile("Production"))

        assertTrue(repository.activate(id).isFailure)
    }

    @Test
    fun deletingProfileDeletesSecrets() = runTest {
        val repository = ProfileRepository(dao, secrets) { false }
        val id = repository.save(profile("Production"))

        repository.delete(id)

        assertFalse(secrets.values.containsKey(id))
    }

    private fun profile(name: String) = ImportedProfile(name, "a.example", 443, Scheme.HTTPS, 60, TlsMode.SYSTEM)
}

private class FakeProfileDao : ProfileDao {
    val rows = linkedMapOf<Long, ProfileEntity>()
    private val flow = MutableStateFlow<List<ProfileEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ProfileEntity>> = flow
    override suspend fun get(id: Long): ProfileEntity? = rows[id]
    override suspend fun getActive(): ProfileEntity? = rows.values.firstOrNull { it.active }
    override suspend fun insert(profile: ProfileEntity): Long = nextId++.also { rows[it] = profile.copy(id = it); emit() }
    override suspend fun deactivateAll() { rows.replaceAll { _, value -> value.copy(active = false) }; emit() }
    override suspend fun setActive(id: Long) { rows[id]?.let { rows[id] = it.copy(active = true) }; emit() }
    override suspend fun delete(id: Long) { rows.remove(id); emit() }
    private fun emit() { flow.value = rows.values.toList() }
}

private class FakeProfileSecrets : ProfileSecrets {
    val values = mutableMapOf<Long, ProfileSecret>()
    override fun get(id: Long): ProfileSecret? = values[id]
    override fun put(id: Long, value: ProfileSecret) { values[id] = value }
    override fun delete(id: Long) { values.remove(id) }
}
