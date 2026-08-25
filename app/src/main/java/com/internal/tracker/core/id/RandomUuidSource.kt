package com.internal.tracker.core.id

import java.util.UUID

object RandomUuidSource : UuidSource {
    override fun newUuid(): UUID = UUID.randomUUID()
}
