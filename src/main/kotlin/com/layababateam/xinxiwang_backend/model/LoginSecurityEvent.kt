package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "login_security_events")
data class LoginSecurityEvent(
    @Id
    val id: String? = null,
    @Indexed
    val eventType: String,
    @Indexed
    val username: String? = null,
    @Indexed
    val userId: String? = null,
    @Indexed
    val clientIp: String? = null,
    @Indexed
    val subnet: String? = null,
    val remoteAddr: String? = null,
    val forwardedFor: String? = null,
    val realIp: String? = null,
    val forwarded: String? = null,
    val proxyChain: String? = null,
    val userAgent: String? = null,
    @Indexed
    val deviceId: String? = null,
    val deviceName: String? = null,
    val platform: String? = null,
    val clientVersion: String? = null,
    val deviceSummary: String? = null,
    val method: String? = null,
    val path: String? = null,
    val status: Int? = null,
    val riskScore: Int = 0,
    @Indexed
    val riskLevel: String = "LOW",
    val riskReasons: List<String> = emptyList(),
    val blocked: Boolean = false,
    @Indexed
    val createdAt: Long = System.currentTimeMillis()
)
