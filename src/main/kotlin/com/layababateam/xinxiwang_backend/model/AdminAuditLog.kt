package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "admin_audit_logs")
data class AdminAuditLog(
    @Id
    val id: String? = null,
    @Indexed
    val adminId: String,
    val adminUsername: String,
    val action: String,
    val eventType: String = "CHANGE",
    val targetType: String,
    val targetId: String? = null,
    val details: String? = null,
    val ipAddress: String? = null,
    val method: String? = null,
    val path: String? = null,
    val status: Int? = null,
    val durationMs: Long? = null,
    val clientIp: String? = null,
    val remoteAddr: String? = null,
    val forwardedFor: String? = null,
    val realIp: String? = null,
    val forwarded: String? = null,
    val userAgent: String? = null,
    val deviceId: String? = null,
    val deviceSummary: String? = null,
    @Indexed
    val createdAt: Long = System.currentTimeMillis()
)
