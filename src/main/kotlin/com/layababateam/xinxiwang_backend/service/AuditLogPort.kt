package com.layababateam.xinxiwang_backend.service

interface AuditLogPort {
    fun recordAudit(
        adminId: String,
        adminUsername: String,
        action: String,
        targetType: String,
        targetId: String?,
        details: String?,
        ipAddress: String?
    )
}
