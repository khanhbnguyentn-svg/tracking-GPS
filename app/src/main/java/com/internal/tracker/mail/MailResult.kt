package com.internal.tracker.mail

import com.internal.tracker.config.PilotConfig

sealed interface MailResult {
    data object Accepted : MailResult
    data object AuthenticationRejected : MailResult
    data object NetworkFailure : MailResult
    data object TlsFailure : MailResult
    data object RateLimited : MailResult
    data object UnknownFailure : MailResult
}

fun interface MailSender {
    suspend fun send(config: PilotConfig, message: ReportMessage): MailResult
}
