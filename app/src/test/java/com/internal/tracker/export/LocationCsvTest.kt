package com.internal.tracker.export

import com.internal.tracker.history.DeliveryState
import com.internal.tracker.history.LocationRecord
import com.internal.tracker.history.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationCsvTest {
    @Test
    fun writesStableHeaderAndEscapesValues() {
        val csv = LocationCsv.encode(listOf(record(deviceId = "AND-Test,One")))

        assertTrue(csv.startsWith("record_number,device_number,device_id,captured_at,timezone,latitude,longitude,accuracy_m,battery_percent,tracked_duration,delivery_state,record_type\r\n"))
        assertTrue(csv.contains("\"AND-Test,One\""))
    }

    @Test
    fun writesStopRecordTypeAfterDeliveryState() {
        val csv = LocationCsv.encode(listOf(record(recordType = RecordType.STOP)))

        assertTrue(csv.endsWith(",pending,STOP\r\n"))
    }

    @Test
    fun writesReadableTrackedDurationAndMissingAccuracy() {
        val columns = LocationCsv.encode(listOf(record(accuracy = null, trackedMillis = 190_860_000)))
            .lineSequence().drop(1).first().split(',')

        assertEquals("", columns[7])
        assertEquals("2d 5h 1m", columns[9])
    }

    private fun record(
        deviceId: String = "AND-1",
        accuracy: Double? = 4.5,
        trackedMillis: Long = 60_000,
        recordType: RecordType = RecordType.PERIODIC,
    ) = LocationRecord(
        id = 7,
        deviceNumber = "001",
        deviceId = deviceId,
        capturedAt = 1_786_553_550_000,
        timezone = "Asia/Ho_Chi_Minh",
        latitude = 10.5,
        longitude = 20.25,
        accuracy = accuracy,
        batteryPercent = 82,
        trackedDurationMillis = trackedMillis,
        state = DeliveryState.PENDING,
        recordType = recordType,
    )
}
