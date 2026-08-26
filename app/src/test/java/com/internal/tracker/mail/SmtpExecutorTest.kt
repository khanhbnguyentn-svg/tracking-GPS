package com.internal.tracker.mail

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SmtpExecutorTest {
    @Test fun runsBlockingTransportOnConfiguredDispatcher() {
        Executors.newSingleThreadExecutor { Thread(it, "smtp-io-test") }.asCoroutineDispatcher().use { dispatcher ->
            val thread = runBlocking { SmtpExecutor.run(dispatcher) { Thread.currentThread().name } }
            assertTrue(thread.startsWith("smtp-io-test"))
        }
    }
}
