package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CallSessionAudit {
    private val log = LoggerFactory.getLogger(LOGGER_NAME)

    fun log(event: String, roomId: Int?, userId: String?, meta: Map<String, Any?> = emptyMap()) {
        log.info(
            "[CALL-AUDIT] event={} roomId={} userId={} {}",
            event,
            roomId ?: "-",
            userId ?: "-",
            formatMeta(meta),
        )
    }

    private fun formatMeta(meta: Map<String, Any?>): String {
        if (meta.isEmpty()) return ""
        return meta.entries.joinToString(" ") { (key, value) -> "$key=${value ?: "null"}" }
    }

    private companion object {
        const val LOGGER_NAME = "call-audit"
    }
}
