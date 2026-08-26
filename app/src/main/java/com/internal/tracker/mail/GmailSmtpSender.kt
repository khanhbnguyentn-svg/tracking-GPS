package com.internal.tracker.mail

import com.internal.tracker.config.PilotConfig
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.AuthenticationFailedException
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object SmtpExecutor {
    suspend fun <T> run(dispatcher: CoroutineDispatcher = Dispatchers.IO, block: () -> T): T =
        withContext(dispatcher) { block() }
}

class GmailSmtpSender : MailSender {
    override suspend fun send(config: PilotConfig, message: ReportMessage): MailResult = SmtpExecutor.run { classify {
        val session = session()
        val mail = MimeMessage(session).apply {
            setFrom(InternetAddress(config.sender))
            setRecipient(Message.RecipientType.TO, InternetAddress(config.recipient))
            setSubject(message.subject, Charsets.UTF_8.name())
            setContent(
                MimeMultipart().apply {
                    addBodyPart(MimeBodyPart().apply { setText(message.body, Charsets.UTF_8.name()) })
                    message.attachments.forEach { attachment ->
                        addBodyPart(MimeBodyPart().apply {
                            dataHandler = DataHandler(ByteArrayDataSource(attachment.bytes, attachment.contentType))
                            fileName = attachment.name
                        })
                    }
                },
            )
            saveChanges()
        }
        session.getTransport("smtps").use { transport ->
            transport.connect(HOST, PORT, config.sender, config.appPassword)
            transport.sendMessage(mail, mail.allRecipients)
        }
    } }

    fun testCredentials(config: PilotConfig): MailResult = classify {
        session().getTransport("smtps").use { it.connect(HOST, PORT, config.sender, config.appPassword) }
    }

    private fun session() = Session.getInstance(Properties().apply {
        put("mail.smtps.host", HOST)
        put("mail.smtps.port", PORT.toString())
        put("mail.smtps.auth", "true")
        put("mail.smtps.ssl.enable", "true")
        put("mail.smtps.connectiontimeout", TIMEOUT_MILLIS.toString())
        put("mail.smtps.timeout", TIMEOUT_MILLIS.toString())
        put("mail.smtps.writetimeout", TIMEOUT_MILLIS.toString())
    })

    private fun classify(block: () -> Unit): MailResult = try {
        block()
        MailResult.Accepted
    } catch (_: AuthenticationFailedException) {
        MailResult.AuthenticationRejected
    } catch (_: SSLException) {
        MailResult.TlsFailure
    } catch (_: SocketTimeoutException) {
        MailResult.NetworkFailure
    } catch (_: UnknownHostException) {
        MailResult.NetworkFailure
    } catch (_: ConnectException) {
        MailResult.NetworkFailure
    } catch (_: Exception) {
        MailResult.UnknownFailure
    }

    private companion object {
        const val HOST = "smtp.gmail.com"
        const val PORT = 465
        const val TIMEOUT_MILLIS = 30_000
    }
}
