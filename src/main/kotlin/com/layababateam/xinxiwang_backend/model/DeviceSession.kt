package com.layababateam.xinxiwang_backend.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "device_sessions")
data class DeviceSession(
    @Id val id: String? = null,
    @Indexed val userId: String,
    @JsonIgnore
    @Indexed(unique = true) val token: String,
    @Indexed val deviceId: String? = null,
    val deviceName: String = "Unknown Device",
    val platform: String = "unknown",
    val clientVersion: String = "unknown",
    val osVersion: String = "unknown",
    val ip: String? = null,
    val lastActiveAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    @JsonIgnore
    val apnsToken: String? = null,
    @JsonIgnore
    val voipToken: String? = null
)
