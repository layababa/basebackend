package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.MonitoringConfigUpdateRequest
import com.layababateam.xinxiwang_backend.dto.MonitoringConfigView
import com.layababateam.xinxiwang_backend.service.AdminMonitoringPort
import com.layababateam.xinxiwang_backend.service.AuditLogPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/monitoring")
class AdminMonitoringController(
    private val adminMonitoringPort: AdminMonitoringPort,
    private val auditLogPort: AuditLogPort,
    @Value("\${sentry.dsn:}") private val defaultDsn: String,
) {
    @RequireAdmin(role = "SUPER_ADMIN")
    @GetMapping("/config")
    fun getMonitoringConfig(): ResponseEntity<ApiResponse<MonitoringConfigView>> {
        return ResponseEntity.ok(ApiResponse.ok(loadConfig()))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PutMapping("/config")
    fun updateMonitoringConfig(
        request: HttpServletRequest,
        @RequestBody body: MonitoringConfigUpdateRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dsn = body.dsn?.trim()
        val serverDsn = body.serverDsn?.trim()
        for (value in listOf(dsn, serverDsn)) {
            if (value != null && value.isNotEmpty() && !isLikelyDsn(value)) {
                return ResponseEntity.badRequest().body(
                    ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "DSN 格式无效，应形如 https://<key>@<host>/<projectId>"),
                )
            }
        }

        val updates = buildUpdates(body, dsn, serverDsn)
        if (updates.isEmpty()) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "至少需要提供一个配置项"),
            )
        }

        adminMonitoringPort.saveConfigValues(updates, CONFIG_DESCRIPTIONS)
        if (updates.keys.any { it.startsWith(SERVER_KEY_PREFIX) }) {
            adminMonitoringPort.reloadBackendMonitoring()
        }

        auditLogPort.recordAudit(
            adminId = request.getAttribute("adminId") as? String ?: "",
            adminUsername = request.getAttribute("adminUsername") as? String ?: "",
            action = "UPDATE_MONITORING_CONFIG",
            targetType = "SYSTEM_CONFIG",
            targetId = null,
            details = "更新监控配置: ${updates.keys.joinToString(", ")}",
            ipAddress = null,
        )

        return ResponseEntity.ok(ApiResponse.ok(loadConfig(), "监控配置已更新"))
    }

    private fun buildUpdates(
        body: MonitoringConfigUpdateRequest,
        dsn: String?,
        serverDsn: String?,
    ): Map<String, String> = buildMap {
        if (dsn != null) put(KEY_GLITCHTIP_DSN, dsn)
        body.enabled?.let { put(KEY_GLITCHTIP_ENABLED, it.toString()) }
        body.environment?.let { put(KEY_GLITCHTIP_ENVIRONMENT, it.trim()) }
        body.tracesSampleRate?.let { put(KEY_GLITCHTIP_TRACES_SAMPLE_RATE, it.coerceIn(0.0, 1.0).toString()) }

        if (serverDsn != null) put(KEY_GLITCHTIP_SERVER_DSN, serverDsn)
        body.serverEnabled?.let { put(KEY_GLITCHTIP_SERVER_ENABLED, it.toString()) }
        body.serverEnvironment?.let { put(KEY_GLITCHTIP_SERVER_ENVIRONMENT, it.trim()) }
        body.serverTracesSampleRate?.let { put(KEY_GLITCHTIP_SERVER_TRACES_SAMPLE_RATE, it.coerceIn(0.0, 1.0).toString()) }
    }

    private fun loadConfig(): MonitoringConfigView {
        return MonitoringConfigView(
            dsn = adminMonitoringPort.getValue(KEY_GLITCHTIP_DSN)?.takeIf { it.isNotBlank() } ?: defaultDsn,
            enabled = adminMonitoringPort.getBooleanValue(KEY_GLITCHTIP_ENABLED, true),
            environment = adminMonitoringPort.getValue(KEY_GLITCHTIP_ENVIRONMENT) ?: "",
            tracesSampleRate = adminMonitoringPort.getValue(KEY_GLITCHTIP_TRACES_SAMPLE_RATE)?.toDoubleOrNull(),
            serverDsn = adminMonitoringPort.getValue(KEY_GLITCHTIP_SERVER_DSN)?.takeIf { it.isNotBlank() } ?: defaultDsn,
            serverEnabled = adminMonitoringPort.getBooleanValue(KEY_GLITCHTIP_SERVER_ENABLED, true),
            serverEnvironment = adminMonitoringPort.getValue(KEY_GLITCHTIP_SERVER_ENVIRONMENT) ?: "",
            serverTracesSampleRate = adminMonitoringPort.getValue(KEY_GLITCHTIP_SERVER_TRACES_SAMPLE_RATE)?.toDoubleOrNull(),
        )
    }

    private fun isLikelyDsn(value: String): Boolean = DSN_PATTERN.matches(value)

    companion object {
        const val KEY_GLITCHTIP_DSN = "monitoring.glitchtip.dsn"
        const val KEY_GLITCHTIP_ENABLED = "monitoring.glitchtip.enabled"
        const val KEY_GLITCHTIP_ENVIRONMENT = "monitoring.glitchtip.environment"
        const val KEY_GLITCHTIP_TRACES_SAMPLE_RATE = "monitoring.glitchtip.tracesSampleRate"

        const val SERVER_KEY_PREFIX = "monitoring.glitchtip.server."
        const val KEY_GLITCHTIP_SERVER_DSN = "monitoring.glitchtip.server.dsn"
        const val KEY_GLITCHTIP_SERVER_ENABLED = "monitoring.glitchtip.server.enabled"
        const val KEY_GLITCHTIP_SERVER_ENVIRONMENT = "monitoring.glitchtip.server.environment"
        const val KEY_GLITCHTIP_SERVER_TRACES_SAMPLE_RATE = "monitoring.glitchtip.server.tracesSampleRate"

        private val DSN_PATTERN = Regex("""^https?://[A-Za-z0-9]+@[^/\s]+/\d+$""")

        private val CONFIG_DESCRIPTIONS = mapOf(
            KEY_GLITCHTIP_DSN to "客户端 GlitchTip / Sentry 上报 DSN",
            KEY_GLITCHTIP_ENABLED to "客户端监控开关",
            KEY_GLITCHTIP_ENVIRONMENT to "客户端上报环境标签 (production/staging/...)",
            KEY_GLITCHTIP_TRACES_SAMPLE_RATE to "客户端性能采样率 0.0 ~ 1.0",
            KEY_GLITCHTIP_SERVER_DSN to "后端 GlitchTip / Sentry 上报 DSN",
            KEY_GLITCHTIP_SERVER_ENABLED to "后端监控开关",
            KEY_GLITCHTIP_SERVER_ENVIRONMENT to "后端上报环境标签 (production/staging/...)",
            KEY_GLITCHTIP_SERVER_TRACES_SAMPLE_RATE to "后端性能采样率 0.0 ~ 1.0",
        )
    }
}
