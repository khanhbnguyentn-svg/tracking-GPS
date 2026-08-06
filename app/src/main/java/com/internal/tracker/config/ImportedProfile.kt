package com.internal.tracker.config

enum class Scheme { HTTP, HTTPS }

enum class TlsMode { SYSTEM, CUSTOM_CA, PINNING }

data class ImportedProfile(
    val name: String,
    val host: String,
    val port: Int,
    val scheme: Scheme,
    val intervalSeconds: Int,
    val tlsMode: TlsMode,
    val certificatePin: String? = null,
)
