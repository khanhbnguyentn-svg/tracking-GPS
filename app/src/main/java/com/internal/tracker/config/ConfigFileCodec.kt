package com.internal.tracker.config

import java.util.Base64
import org.json.JSONObject

class ConfigFileCodec {
    fun encodeTemplate(): String = JSONObject()
        .put("version", VERSION)
        .put("name", "Production")
        .put("host", "traccar.internal.company.com")
        .put("port", 443)
        .put("scheme", "https")
        .put("intervalSeconds", 60)
        .put("tlsMode", "system")
        .toString(2)

    fun decode(text: String): Result<ImportedProfile> = runCatching {
        val json = JSONObject(text)
        require(ALLOWED_FIELDS.containsAll(json.keys().asSequence().toSet())) { "File có trường không hỗ trợ" }
        require(json.getInt("version") == VERSION) { "Phiên bản file không được hỗ trợ" }

        val name = json.getString("name").trim()
        val host = json.getString("host").trim()
        val port = json.getInt("port")
        val interval = json.getInt("intervalSeconds")
        val scheme = enumValueOf<Scheme>(json.getString("scheme").uppercase())
        val tlsMode = when (json.getString("tlsMode")) {
            "system" -> TlsMode.SYSTEM
            "customCa" -> TlsMode.CUSTOM_CA
            "pinning" -> TlsMode.PINNING
            else -> error("Chế độ TLS không hợp lệ")
        }
        val pin = json.optString("certificatePin").takeIf(String::isNotBlank)

        require(name.isNotBlank()) { "Tên profile không được trống" }
        require(host.isNotBlank() && "://" !in host && host.none(Char::isWhitespace)) { "Host không hợp lệ" }
        require(port in 1..65535) { "Port phải từ 1 đến 65535" }
        require(interval in 15..86400) { "Chu kỳ gửi phải từ 15 đến 86400 giây" }
        require(scheme == Scheme.HTTPS || tlsMode == TlsMode.SYSTEM) { "HTTP không dùng cấu hình TLS" }
        if (tlsMode == TlsMode.PINNING) requireValidPin(pin)

        ImportedProfile(name, host, port, scheme, interval, tlsMode, pin)
    }

    private fun requireValidPin(pin: String?) {
        require(pin?.startsWith("sha256/") == true) { "Certificate pin phải bắt đầu bằng sha256/" }
        val decoded = runCatching { Base64.getDecoder().decode(pin.removePrefix("sha256/")) }.getOrNull()
        require(decoded?.size == 32) { "Certificate pin phải chứa SHA-256 hợp lệ" }
    }

    private companion object {
        const val VERSION = 1
        val ALLOWED_FIELDS = setOf(
            "version", "name", "host", "port", "scheme", "intervalSeconds", "tlsMode", "certificatePin",
        )
    }
}
