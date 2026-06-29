package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.UserLogConfig
import org.springframework.stereotype.Service

@Service
class UserLogConfigPushService(
    private val port: UserLogConfigPushPort,
) {
    fun pushToEligibleDevices(userId: String, config: UserLogConfig): Int =
        port.pushClientLogConfigToEligibleUser(userId, toPayload(config))

    fun toView(userId: String, config: UserLogConfig): Map<String, Any?> {
        val eligibleDeviceIds = port.getEligibleClientLogDeviceIds(userId)
        return mapOf(
            "criticalLogEnabled" to config.criticalLogEnabled,
            "revision" to config.revision,
            "updatedAt" to config.updatedAt,
            "eligibleOnlineDevices" to eligibleDeviceIds.size,
            "ackedDevices" to config.ackedDeviceIds.intersect(eligibleDeviceIds).size,
        )
    }

    private fun toPayload(config: UserLogConfig): String =
        """{"type":"client_log_config_updated","data":{"criticalLogEnabled":${config.criticalLogEnabled},"revision":${config.revision},"updatedAt":${config.updatedAt}}}"""
}
