package com.internal.tracker.schedule

import com.internal.tracker.report.ReportWorker
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduledReportWorkTest {
    @Test fun requestDataCarriesTheExactScheduledEpoch() {
        val data = ScheduledReportWork.inputData(123_456)

        assertEquals(123_456, data.getLong(ReportWorker.KEY_SCHEDULED_FOR, 0))
    }
}
