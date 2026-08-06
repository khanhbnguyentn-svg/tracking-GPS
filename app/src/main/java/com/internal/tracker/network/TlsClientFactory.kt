package com.internal.tracker.network

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

class TlsClientFactory {
    fun system(): OkHttpClient = OkHttpClient.Builder().build()

    fun pinning(host: String, pin: String): OkHttpClient = OkHttpClient.Builder()
        .certificatePinner(CertificatePinner.Builder().add(host, pin).build())
        .build()

    fun customCa(certificateBytes: ByteArray): OkHttpClient {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("custom", certificate)
        }
        val custom = trustManager(keyStore)
        val combined = CompositeTrustManager(defaultTrustManager(), custom)
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(combined), null) }
        return OkHttpClient.Builder().sslSocketFactory(context.socketFactory, combined).build()
    }

    private fun defaultTrustManager(): X509TrustManager = trustManager(null)

    private fun trustManager(keyStore: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    }
}

private class CompositeTrustManager(
    private vararg val delegates: X509TrustManager,
) : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate> = delegates.flatMap { it.acceptedIssuers.toList() }.toTypedArray()
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = tryDelegates { it.checkClientTrusted(chain, authType) }
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = tryDelegates { it.checkServerTrusted(chain, authType) }

    private fun tryDelegates(check: (X509TrustManager) -> Unit) {
        var failure: Exception? = null
        for (delegate in delegates) {
            try {
                check(delegate)
                return
            } catch (error: Exception) {
                failure = error
            }
        }
        throw failure ?: java.security.cert.CertificateException("Không có trust manager")
    }
}
