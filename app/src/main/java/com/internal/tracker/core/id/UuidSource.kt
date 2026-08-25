package com.internal.tracker.core.id

import java.util.UUID

fun interface UuidSource {
    fun newUuid(): UUID
}
