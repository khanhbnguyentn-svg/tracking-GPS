package com.internal.tracker.network

import com.internal.tracker.config.Scheme
import com.internal.tracker.config.TlsMode
import com.internal.tracker.profile.Profile
import com.internal.tracker.queue.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionTesterTest {
    @Test
    fun networkAndDataTestsRemainSeparate() {
        var sends = 0
        val tester = ConnectionTester(NetworkProbe { SendResult.Success }, LocationSender { _, _, _ -> sends++; SendResult.Success })

        assertEquals(DiagnosticResult.ServerReachable, tester.testNetwork(profile()))
        assertEquals(0, sends)
        assertEquals(DiagnosticResult.RealLocationRequired, tester.sendLatest(profile(), "AND-0123456789abcdef", null))
        assertEquals(DiagnosticResult.DataAccepted, tester.sendLatest(profile(), "AND-0123456789abcdef", LocationSample(1.0, 2.0, 3, 0.0, 4.0)))
        assertEquals(1, sends)
    }

    @Test
    fun errorsAreSpecific() {
        val tester = ConnectionTester(NetworkProbe { SendResult.DnsFailure }, LocationSender { _, _, _ -> SendResult.HttpFailure(400) })

        assertEquals(DiagnosticResult.DnsError, tester.testNetwork(profile()))
        assertEquals(DiagnosticResult.HttpError(400), tester.sendLatest(profile(), "AND-0123456789abcdef", LocationSample(1.0, 2.0, 3, 0.0, 4.0)))
    }

    @Test
    fun authenticationFailureHasASpecificDiagnostic() {
        val sample = LocationSample(1.0, 2.0, 3, 0.0, 4.0)

        assertEquals(
            DiagnosticResult.AuthenticationError,
            ConnectionTester(NetworkProbe { SendResult.Success }, LocationSender { _, _, _ -> SendResult.AuthenticationFailure })
                .sendLatest(profile(), "AND-0123456789abcdef", sample),
        )
    }

    private fun profile() = Profile(1, "P", "example.com", 443, Scheme.HTTPS, 60, TlsMode.SYSTEM, null, null, true)
}
