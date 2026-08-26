package com.internal.tracker.diagnostics

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class IncidentType { GPS_GAP, SUSPECTED_GPS_JUMP, TIMESTAMP_ANOMALY, EVENT_SEQUENCE_ANOMALY, UNRESOLVED_TEMP_STOP }
enum class IncidentState { OPEN, RECOVERED }
enum class ConfidenceBand { LOW, MEDIUM, HIGH }
enum class EvidenceRole { BEFORE, TRIGGER, AFTER }
enum class DeviceCondition { NORMAL, LOCATION_DISABLED, PERMISSION_MISSING, PROVIDER_SILENT, REBOOT, PACKAGE_REPLACED, PROCESS_RECREATED, UNKNOWN }
enum class DiagnosticDeliveryState { NOT_REQUIRED, PENDING, ACCEPTED, FAILED }

data class DiagnosticLocation(val capturedAt: Long, val latitude: Double, val longitude: Double)

@Entity(
    tableName = "diagnostic_incidents",
    indices = [Index("openedAt"), Index("reportedAt"), Index("type")],
)
data class DiagnosticIncident(
    @PrimaryKey val incidentId: String,
    val type: IncidentType,
    val reasonCodes: String,
    val openedAt: Long,
    val recoveredAt: Long? = null,
    val state: IncidentState = IncidentState.OPEN,
    val confidenceScore: Int = 0,
    val confidenceBand: ConfidenceBand = ConfidenceBand.LOW,
    val deviceCondition: DeviceCondition = DeviceCondition.UNKNOWN,
    val evidenceComplete: Boolean = false,
    val lastCapturedAt: Long? = null,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val firstCapturedAt: Long? = null,
    val firstLatitude: Double? = null,
    val firstLongitude: Double? = null,
    val openedAlertState: DiagnosticDeliveryState = DiagnosticDeliveryState.NOT_REQUIRED,
    val openedAlertAttempts: Int = 0,
    val openedAlertSentAt: Long? = null,
    val openedAlertError: String? = null,
    val recoveredAlertState: DiagnosticDeliveryState = DiagnosticDeliveryState.NOT_REQUIRED,
    val recoveredAlertAttempts: Int = 0,
    val recoveredAlertSentAt: Long? = null,
    val recoveredAlertError: String? = null,
    val reportedAt: Long? = null,
)

@Entity(
    tableName = "diagnostic_samples",
    primaryKeys = ["incidentId", "sequence"],
    foreignKeys = [ForeignKey(
        entity = DiagnosticIncident::class,
        parentColumns = ["incidentId"],
        childColumns = ["incidentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("incidentId")],
)
data class DiagnosticSample(
    val incidentId: String,
    val sequence: Int,
    val role: EvidenceRole,
    val capturedAt: Long,
    val receivedAt: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val speedMetersPerSecond: Double?,
    val derivedDistanceMeters: Double?,
    val derivedSpeedMetersPerSecond: Double?,
    val signalFlags: String,
)

data class DiagnosticBundle(
    val incidents: List<DiagnosticIncident>,
    val samples: List<DiagnosticSample>,
)
