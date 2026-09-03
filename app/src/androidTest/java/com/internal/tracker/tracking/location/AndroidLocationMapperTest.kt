package com.internal.tracker.tracking.location

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidLocationMapperTest {
    @Test fun preservesRequiredLocationValues() {
        val location = Location("fused").apply { time = 1_000L; elapsedRealtimeNanos = 2_000L; latitude = 10.7769; longitude = 106.7009; accuracy = 4f; speed = 3f }
        val sample = AndroidLocationMapper.map(location, 420)
        assertEquals(1_000L, sample.capturedUtcMillis)
        assertEquals(2_000L, sample.elapsedRealtimeNanos)
        assertEquals(10.7769, sample.latitude, 0.0)
        assertEquals(106.7009, sample.longitude, 0.0)
        assertEquals("fused", sample.provider)
        assertEquals(4f, sample.horizontalAccuracyMeters)
        assertEquals(3f, sample.speedMetersPerSecond)
    }
}
