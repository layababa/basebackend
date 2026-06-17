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
    val ip: String? = null,
    val lastActiveAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    @JsonIgnore
    val apnsToken: String? = null,
    @JsonIgnore
    val voipToken: String? = null,
    val iosVersion: String? = null,         // iOS 系統版本，例 "17.4.1"
    val supportsLiveComm: Boolean = false,  // 客戶端宣告是否支援 LiveCommunicationKit
    val notificationAuthorized: Boolean = true  // 通知權限是否授予，默認 true 向後兼容
)
