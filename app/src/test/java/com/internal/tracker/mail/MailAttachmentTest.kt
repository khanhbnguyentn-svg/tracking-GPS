package com.internal.tracker.mail

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MailAttachmentTest {
    @Test fun reportMessageCanCarryMultipleTypedAttachmentsAndMetadata() {
        val first = MailAttachment("route.csv", "text/csv", byteArrayOf(1))
        val second = MailAttachment("diagnostics.csv", "text/csv", byteArrayOf(2))

        val message = ReportMessage(
            subject = "subject",
            body = "body",
            attachments = listOf(first, second),
        )

        assertEquals(listOf("route.csv", "diagnostics.csv"), message.attachments.map { it.name })
        assertArrayEquals(byteArrayOf(2), message.attachments[1].bytes)
        assertEquals(emptyList<Long>(), message.recordIds)
        assertEquals(emptyList<String>(), message.incidentIds)
        assertNull(message.reportId)
    }
}
