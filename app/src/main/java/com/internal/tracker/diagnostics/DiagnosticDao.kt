package com.internal.tracker.diagnostics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

interface DiagnosticStore {
    suspend fun upsertIncident(incident: DiagnosticIncident)
    suspend fun incident(id: String): DiagnosticIncident?
    suspend fun openIncident(type: IncidentType): DiagnosticIncident?
    suspend fun insertSamples(values: List<DiagnosticSample>)
    suspend fun pendingForReport(limit: Int): List<DiagnosticIncident>
    suspend fun samplesFor(incidentIds: List<String>): List<DiagnosticSample>
    suspend fun deleteIncidentsBefore(before: Long)
    suspend fun deleteReportedSamplesBefore(before: Long)
}

@Dao
interface DiagnosticDao : DiagnosticStore {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsertIncident(incident: DiagnosticIncident)

    @Query("SELECT * FROM diagnostic_incidents WHERE incidentId = :id")
    override suspend fun incident(id: String): DiagnosticIncident?

    @Query("SELECT * FROM diagnostic_incidents WHERE type = :type AND state = 'OPEN' ORDER BY openedAt, incidentId LIMIT 1")
    override suspend fun openIncident(type: IncidentType): DiagnosticIncident?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun insertSamples(values: List<DiagnosticSample>)

    @Query("SELECT * FROM diagnostic_incidents WHERE reportedAt IS NULL ORDER BY openedAt, incidentId LIMIT :limit")
    override suspend fun pendingForReport(limit: Int): List<DiagnosticIncident>

    @Query("SELECT * FROM diagnostic_samples WHERE incidentId IN (:incidentIds) ORDER BY incidentId, sequence")
    override suspend fun samplesFor(incidentIds: List<String>): List<DiagnosticSample>

    @Query("DELETE FROM diagnostic_incidents WHERE openedAt < :before AND reportedAt IS NULL")
    override suspend fun deleteIncidentsBefore(before: Long)

    @Query("DELETE FROM diagnostic_samples WHERE incidentId IN (SELECT incidentId FROM diagnostic_incidents WHERE reportedAt IS NOT NULL AND reportedAt < :before)")
    override suspend fun deleteReportedSamplesBefore(before: Long)
}
