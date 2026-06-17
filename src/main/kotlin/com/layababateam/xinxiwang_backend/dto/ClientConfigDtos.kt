package com.layababateam.xinxiwang_backend.dto

data class MonitoringConfigDto(
    val dsn: String,
    val enabled: Boolean,
    val environment: String?,
    val tracesSampleRate: Double?,
)

data class GroupMessageSignalClientConfigDto(
    val enabled: Boolean,
    val groupMemberThreshold: Int,
    val onlineMemberThreshold: Int,
    val minProtocolVersion: Int,
    val syncDefaultLimit: Int,
    val syncMaxLimit: Int,
    val rolloutPercent: Int,
    val deliveryMode: String,
    val deliveryReason: String,
)
