package com.layababateam.xinxiwang_backend.service

interface AuditLogPort {
    fun log(
        adminId: String,
        adminUsername: String,
        action: String,
        targetType: String,
        targetId: String? = null,
        details: String? = null,
        ipAddress: String? = null
    )
}
