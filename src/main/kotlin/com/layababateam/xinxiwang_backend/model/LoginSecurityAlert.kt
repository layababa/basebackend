package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "login_security_alerts")
data class LoginSecurityAlert(
    @Id
    val id: String? = null,
    @Indexed
    val alertType: String,
    val severity: String = "WARNING",
    @Indexed
    val status: String = "OPEN",
    val title: String,
    val message: String,
    val count: Long = 0,
    val windowStart: Long? = null,
    val windowEnd: Long? = null,
    val topClientIp: String? = null,
    val topSubnet: String? = null,
    val topUsername: String? = null,
    val ackBy: String? = null,
    val ackAt: Long? = null,
    val resolvedBy: String? = null,
    val resolvedAt: Long? = null,
    @Indexed
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
