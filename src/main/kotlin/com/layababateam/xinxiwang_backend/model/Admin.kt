package com.layababateam.xinxiwang_backend.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "admins")
data class Admin(
    @Id
    val id: String? = null,
    @Indexed(unique = true) val username: String,
    @JsonIgnore
    val passwordHash: String,
    val role: String = "ADMIN",
    @JsonIgnore
    val totpSecret: String? = null,
    val totpEnabled: Boolean = false,
    val mustChangePassword: Boolean = false,
    val lastLoginAt: Long? = null,
    val lastLoginIp: String? = null,
    val lastLoginClientIp: String? = null,
    val lastLoginProxyChain: String? = null,
    val lastLoginDeviceId: String? = null,
    val lastLoginUserAgent: String? = null,
    val lastLoginDeviceSummary: String? = null,
    val tokenVersion: Long = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
