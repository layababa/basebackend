package com.layababateam.xinxiwang_backend.dto

data class MonitoringConfigUpdateRequest(
    val dsn: String? = null,
    val enabled: Boolean? = null,
    val environment: String? = null,
    val tracesSampleRate: Double? = null,
    val serverDsn: String? = null,
    val serverEnabled: Boolean? = null,
    val serverEnvironment: String? = null,
    val serverTracesSampleRate: Double? = null,
)

data class MonitoringConfigView(
    val dsn: String,
    val enabled: Boolean,
    val environment: String,
    val tracesSampleRate: Double?,
    val serverDsn: String,
    val serverEnabled: Boolean,
    val serverEnvironment: String,
    val serverTracesSampleRate: Double?,
)
