package com.internal.tracker.tracking.health

import com.internal.tracker.tracking.database.TrackingDao
import com.internal.tracker.tracking.database.TrackingIncidentEntity
import com.internal.tracker.tracking.model.TrackingIncident
import com.internal.tracker.tracking.model.TrackingIncidentType

class TrackingIncidentRepository(private val dao: TrackingDao) {
    suspend fun recover(marker: IncidentRecoveryMarker) {
        val pending = marker.read() ?: return
        val incident = open(pending.type, pending.openedAtUtcMillis)
        pending.closedAtUtcMillis?.let { close(incident.id, it) }
        marker.clear()
    }
    suspend fun open(type: TrackingIncidentType, openedAtUtcMillis: Long): TrackingIncident {
        dao.openIncident(type.name)?.let(::toModel)?.let { return it }
        val id = dao.insertIncident(
            TrackingIncidentEntity(type = type.name, openedAtUtcMillis = openedAtUtcMillis, closedAtUtcMillis = null),
        )
        return TrackingIncident(id, type, openedAtUtcMillis, null)
    }

    suspend fun close(id: Long, closedAtUtcMillis: Long) {
        dao.closeIncident(id, closedAtUtcMillis)
    }

    private fun toModel(entity: TrackingIncidentEntity) = TrackingIncident(
        id = entity.id,
        type = TrackingIncidentType.valueOf(entity.type),
        openedAtUtcMillis = entity.openedAtUtcMillis,
        closedAtUtcMillis = entity.closedAtUtcMillis,
    )
}
