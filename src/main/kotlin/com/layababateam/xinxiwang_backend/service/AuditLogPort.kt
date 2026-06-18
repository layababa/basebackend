package com.layababateam.xinxiwang_backend.service

interface AuditLogPort {
    companion object {
        const val EXPLICIT_AUDIT_ATTR = "adminExplicitAuditLogged"
    }

    fun recordAudit(
        adminId: String,
        adminUsername: String,
        action: String,
        targetType: String,
        targetId: String?,
        details: String?,
        ipAddress: String?
    )

    fun recordHttpAudit(event: AdminHttpAuditEvent)
}

data class AdminHttpAuditEvent(
    val adminId: String,
    val adminUsername: String,
    val eventType: String,
    val action: String,
    val targetType: String,
    val targetId: String?,
    val details: String?,
    val method: String,
    val path: String,
    val status: Int,
    val durationMs: Long,
    val metadata: RequestMetadata,
)
