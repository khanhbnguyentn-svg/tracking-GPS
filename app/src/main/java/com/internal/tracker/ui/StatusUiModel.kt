package com.internal.tracker.ui

data class StatusUiRow(val label: String, val value: String)

data class StatusUiModel(val rows: List<StatusUiRow>) {
    companion object {
        fun create(
            tracking: Boolean,
            deviceNumber: String,
            lastLocationTime: Long,
            lastSendTime: Long,
            nextRunTime: Long,
            formatTime: (Long) -> String = Long::toString,
        ) = StatusUiModel(
            listOf(
                StatusUiRow("Trạng thái", if (tracking) "Đang theo dõi" else "Đã dừng"),
                StatusUiRow("Thiết bị", deviceNumber),
                StatusUiRow("GPS cuối", formatTime(lastLocationTime)),
                StatusUiRow("Email cuối", formatTime(lastSendTime)),
                StatusUiRow("Kỳ gửi dự kiến", formatTime(nextRunTime)),
            ),
        )
    }
}
