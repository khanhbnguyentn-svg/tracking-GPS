package com.internal.tracker.tracking.raw

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.internal.tracker.core.database.SqlCipherFactoryProvider
import com.internal.tracker.core.security.DatabaseKeyResult
import com.internal.tracker.core.security.DatabasePassphraseStore
import com.internal.tracker.tracking.database.TrackingDatabaseFactory
import com.internal.tracker.tracking.model.BootReason
import com.internal.tracker.tracking.model.RawLocationSample
import com.internal.tracker.tracking.model.RawSampleKind
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRawLocationRepositoryTest {
    @Test
    fun failedRawInsertRollsBackSequenceAllocation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tracking-rollback-${UUID.randomUUID()}.db"
        val database = TrackingDatabaseFactory(
            context,
            DatabasePassphraseStore { DatabaseKeyResult.Ready(ByteArray(32)) },
            SqlCipherFactoryProvider(),
        ).open(name)

        try {
            val repository = RoomRawLocationRepository(database)
            val bootSessionId = repository.startBootSession(BootReason.PROCESS_START, 1L, 1L)

            try {
                repository.persistOrdinary(sampleAt(10_000_000_000L), "missing-boot-session")
                fail("Expected foreign-key failure")
            } catch (_: Exception) {
                // The next valid write proves Room rolled back both the row and sequence state.
            }

            assertEquals(1L, repository.persistOrdinary(sampleAt(10_000_000_000L), bootSessionId)?.sequenceNumber)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun preservesEndBoundaryKindEvenWhenElapsedTimeMovesBackward() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tracking-boundary-${UUID.randomUUID()}.db"
        val database = TrackingDatabaseFactory(
            context,
            DatabasePassphraseStore { DatabaseKeyResult.Ready(ByteArray(32)) },
            SqlCipherFactoryProvider(),
        ).open(name)

        try {
            val repository = RoomRawLocationRepository(database)
            val bootSessionId = repository.startBootSession(BootReason.PROCESS_START, 1L, 1L)
            repository.persistOrdinary(sampleAt(10_000_000_000L), bootSessionId)

            assertEquals(
                2L,
                repository.persistBoundary(
                    sampleAt(9_000_000_000L),
                    bootSessionId,
                    RawSampleKind.END_BOUNDARY,
                ).sequenceNumber,
            )
            assertEquals("END_BOUNDARY", database.trackingDao().rawSamples().last().kind)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun rejectedOrdinaryWindowDoesNotAdvanceSequenceNumber() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tracking-window-${UUID.randomUUID()}.db"
        val database = TrackingDatabaseFactory(
            context,
            DatabasePassphraseStore { DatabaseKeyResult.Ready(ByteArray(32)) },
            SqlCipherFactoryProvider(),
        ).open(name)

        try {
            val repository = RoomRawLocationRepository(database)
            val bootSessionId = repository.startBootSession(BootReason.PROCESS_START, 1L, 1L)

            assertEquals(1L, repository.persistOrdinary(sampleAt(10_000_000_000L), bootSessionId)?.sequenceNumber)
            assertNull(repository.persistOrdinary(sampleAt(15_000_000_000L), bootSessionId))
            assertEquals(2L, repository.persistOrdinary(sampleAt(20_000_000_000L), bootSessionId)?.sequenceNumber)
            assertEquals(listOf(1L, 2L), database.trackingDao().rawSamples().map { it.sequenceNumber })
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun persistsExactRawSourceValuesWithFirstSequenceNumber() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tracking-raw-${UUID.randomUUID()}.db"
        val database = TrackingDatabaseFactory(
            context = context,
            passphraseStore = DatabasePassphraseStore {
                DatabaseKeyResult.Ready(ByteArray(32) { it.toByte() })
            },
            factoryProvider = SqlCipherFactoryProvider(),
        ).open(name)

        try {
            val repository = RoomRawLocationRepository(database)
            val bootSessionId = repository.startBootSession(
                reason = BootReason.PROCESS_START,
                nowUtcMillis = 1_725_000_000_000L,
                nowElapsedNanos = 1_000L,
            )
            val sample = RawLocationSample(
                capturedUtcMillis = 1_725_000_010_000L,
                capturedOffsetMinutes = 420,
                elapsedRealtimeNanos = 10_000_000_000L,
                latitude = 10.7769,
                longitude = 106.7009,
                altitudeMeters = 12.5,
                horizontalAccuracyMeters = 3.25f,
                verticalAccuracyMeters = 4.5f,
                speedMetersPerSecond = 8.75f,
                speedAccuracyMetersPerSecond = 0.6f,
                bearingDegrees = 180f,
                bearingAccuracyDegrees = 2.5f,
                provider = "fused",
                isMock = false,
            )

            assertEquals(1L, repository.persistOrdinary(sample, bootSessionId)?.sequenceNumber)

            val stored = database.trackingDao().rawSamples().single()
            assertEquals(sample.capturedUtcMillis, stored.capturedUtcMillis)
            assertEquals(sample.capturedOffsetMinutes, stored.capturedOffsetMinutes)
            assertEquals(sample.elapsedRealtimeNanos, stored.elapsedRealtimeNanos)
            assertEquals(sample.latitude, stored.latitude, 0.0)
            assertEquals(sample.longitude, stored.longitude, 0.0)
            assertEquals(sample.altitudeMeters, stored.altitudeMeters)
            assertEquals(sample.horizontalAccuracyMeters, stored.horizontalAccuracyMeters)
            assertEquals(sample.verticalAccuracyMeters, stored.verticalAccuracyMeters)
            assertEquals(sample.speedMetersPerSecond, stored.speedMetersPerSecond)
            assertEquals(sample.speedAccuracyMetersPerSecond, stored.speedAccuracyMetersPerSecond)
            assertEquals(sample.bearingDegrees, stored.bearingDegrees)
            assertEquals(sample.bearingAccuracyDegrees, stored.bearingAccuracyDegrees)
            assertEquals(sample.provider, stored.provider)
            assertEquals(sample.isMock, stored.isMock)
            assertEquals(bootSessionId, stored.bootSessionId)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun sampleAt(elapsedRealtimeNanos: Long) = RawLocationSample(
        capturedUtcMillis = elapsedRealtimeNanos,
        capturedOffsetMinutes = 420,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        latitude = 10.7769,
        longitude = 106.7009,
        altitudeMeters = null,
        horizontalAccuracyMeters = null,
        verticalAccuracyMeters = null,
        speedMetersPerSecond = null,
        speedAccuracyMetersPerSecond = null,
        bearingDegrees = null,
        bearingAccuracyDegrees = null,
        provider = null,
        isMock = false,
    )
}
