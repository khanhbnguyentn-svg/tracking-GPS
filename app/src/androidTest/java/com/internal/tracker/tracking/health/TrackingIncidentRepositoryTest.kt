package com.internal.tracker.tracking.health

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.internal.tracker.core.database.SqlCipherFactoryProvider
import com.internal.tracker.core.security.DatabaseKeyResult
import com.internal.tracker.core.security.DatabasePassphraseStore
import com.internal.tracker.tracking.database.MovementEventEntity
import com.internal.tracker.tracking.database.TrackingDatabaseFactory
import com.internal.tracker.tracking.database.TrackingIncidentEntity
import com.internal.tracker.tracking.model.BootReason
import com.internal.tracker.tracking.model.RawLocationSample
import com.internal.tracker.tracking.model.TrackingIncidentType
import com.internal.tracker.tracking.raw.RoomRawLocationRepository
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingIncidentRepositoryTest {
    @Test
    fun recoveryMaterializesMarkerIntoRoomAndClearsIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tracking-marker-recovery-${UUID.randomUUID()}.db"
        val database = TrackingDatabaseFactory(context, DatabasePassphraseStore { DatabaseKeyResult.Ready(ByteArray(32)) }, SqlCipherFactoryProvider()).open(name)
        val marker = IncidentRecoveryMarker(context)
        marker.clear()
        marker.write(TrackingIncidentType.GPS_GAP, 30_000L, 45_000L)
        try {
            TrackingIncidentRepository(database.trackingDao()).recover(marker)
            assertEquals(45_000L, database.trackingDao().incidents().single().closedAtUtcMillis)
            assertEquals(null, marker.read())
        } finally { marker.clear(); database.close(); context.deleteDatabase(name) }
    }

    @Test
    fun recoveryMarkerPersistsOnlyIncidentTimingUntilCleared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = IncidentRecoveryMarker(context)
        marker.clear()
        marker.write(TrackingIncidentType.GPS_GAP, 30_000L, null)

        assertEquals(
            PendingIncidentRecovery(TrackingIncidentType.GPS_GAP, 30_000L, null),
            IncidentRecoveryMarker(context).read(),
        )

        marker.clear()
        assertEquals(null, marker.read())
    }

    @Test
    fun opensOneGpsGapAndClosesTheSameIncident() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tracking-singleton-gap-${UUID.randomUUID()}.db"
        val database = TrackingDatabaseFactory(
            context,
            DatabasePassphraseStore { DatabaseKeyResult.Ready(ByteArray(32)) },
            SqlCipherFactoryProvider(),
        ).open(name)

        try {
            val repository = TrackingIncidentRepository(database.trackingDao())

            val opened = repository.open(TrackingIncidentType.GPS_GAP, 30_000L)
            val duplicate = repository.open(TrackingIncidentType.GPS_GAP, 31_000L)
            repository.close(opened.id, 45_000L)

            assertEquals(opened.id, duplicate.id)
            assertEquals(1, database.trackingDao().incidents().size)
            assertEquals(45_000L, database.trackingDao().incidents().single().closedAtUtcMillis)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun storesMovementEventReferencingPersistedRawSample() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tracking-movement-source-${UUID.randomUUID()}.db"
        val database = TrackingDatabaseFactory(
            context,
            DatabasePassphraseStore { DatabaseKeyResult.Ready(ByteArray(32)) },
            SqlCipherFactoryProvider(),
        ).open(name)

        try {
            val raw = RoomRawLocationRepository(database)
            val bootSession = raw.startBootSession(BootReason.PROCESS_START, 0L, 0L)
            val persisted = raw.persistOrdinary(sampleAt(10_000_000_000L), bootSession)!!

            database.trackingDao().insertMovementEvent(
                MovementEventEntity(
                    type = "TEMP_STOP_STARTED",
                    effectiveAtUtcMillis = persisted.sample.capturedUtcMillis,
                    confirmedAtUtcMillis = persisted.sample.capturedUtcMillis,
                    firstSourceSequenceNumber = persisted.sequenceNumber,
                    confirmingSourceSequenceNumber = persisted.sequenceNumber,
                    algorithmVersion = 1,
                ),
            )

            assertEquals(persisted.sequenceNumber, database.trackingDao().movementEvents().single().firstSourceSequenceNumber)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun rejectsMovementEventReferencingMissingRawSample() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tracking-movement-reference-${UUID.randomUUID()}.db"
        val database = TrackingDatabaseFactory(
            context,
            DatabasePassphraseStore { DatabaseKeyResult.Ready(ByteArray(32)) },
            SqlCipherFactoryProvider(),
        ).open(name)

        try {
            try {
                database.trackingDao().insertMovementEvent(
                    MovementEventEntity(
                        type = "TEMP_STOP_STARTED",
                        effectiveAtUtcMillis = 0L,
                        confirmedAtUtcMillis = 60_000L,
                        firstSourceSequenceNumber = 999L,
                        confirmingSourceSequenceNumber = 999L,
                        algorithmVersion = 1,
                    ),
                )
                fail("Movement events must not reference a missing raw sample")
            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                // Expected: the raw sequence foreign key protects event traceability.
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun storesDerivedMovementAndCoordinateFreeGpsGap() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tracking-incident-${UUID.randomUUID()}.db"
        val database = TrackingDatabaseFactory(
            context,
            DatabasePassphraseStore { DatabaseKeyResult.Ready(ByteArray(32)) },
            SqlCipherFactoryProvider(),
        ).open(name)

        try {
            val dao = database.trackingDao()
            val raw = RoomRawLocationRepository(database)
            val bootSession = raw.startBootSession(BootReason.PROCESS_START, 0L, 0L)
            val persisted = raw.persistOrdinary(sampleAt(10_000_000_000L), bootSession)!!
            dao.insertMovementEvent(
                MovementEventEntity(
                    type = "TEMP_STOP_STARTED",
                    effectiveAtUtcMillis = 0L,
                    confirmedAtUtcMillis = 60_000L,
                    firstSourceSequenceNumber = persisted.sequenceNumber,
                    confirmingSourceSequenceNumber = persisted.sequenceNumber,
                    algorithmVersion = 1,
                ),
            )
            val incidentId = dao.insertIncident(
                TrackingIncidentEntity(
                    type = "GPS_GAP",
                    openedAtUtcMillis = 30_000L,
                    closedAtUtcMillis = null,
                ),
            )
            dao.closeIncident(incidentId, 31_000L)

            assertEquals("TEMP_STOP_STARTED", dao.movementEvents().single().type)
            assertEquals(1, dao.movementEvents().single().algorithmVersion)
            assertEquals(31_000L, dao.incidents().single().closedAtUtcMillis)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun sampleAt(elapsedRealtimeNanos: Long) = RawLocationSample(
        capturedUtcMillis = elapsedRealtimeNanos / 1_000_000L,
        capturedOffsetMinutes = 420,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        latitude = 10.7769,
        longitude = 106.7009,
        altitudeMeters = null,
        horizontalAccuracyMeters = null,
        verticalAccuracyMeters = null,
        speedMetersPerSecond = 0.5f,
        speedAccuracyMetersPerSecond = null,
        bearingDegrees = null,
        bearingAccuracyDegrees = null,
        provider = "fused",
        isMock = false,
    )
}
