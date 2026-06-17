package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.GroupMessageSignalAdminConfigRequest
import com.layababateam.xinxiwang_backend.dto.GroupMessageSignalAdminConfigResponse
import com.layababateam.xinxiwang_backend.service.AdminGroupMessageSignalPort
import com.layababateam.xinxiwang_backend.service.AuditLogPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/system/group-message-signal")
class AdminGroupMessageSignalController(
    private val adminGroupMessageSignalPort: AdminGroupMessageSignalPort,
    private val auditLogPort: AuditLogPort,
) {
    @RequireAdmin(role = "SUPER_ADMIN")
    @GetMapping
    fun getConfig(): ResponseEntity<ApiResponse<GroupMessageSignalAdminConfigResponse>> {
        return ResponseEntity.ok(ApiResponse.ok(readConfig()))
    }

    @RequireAdmin(role = "SUPER_ADMIN")
    @PutMapping
    fun updateConfig(
        request: HttpServletRequest,
        @RequestBody body: GroupMessageSignalAdminConfigRequest,
    ): ResponseEntity<ApiResponse<GroupMessageSignalAdminConfigResponse>> {
        val normalized = body.normalized()
        adminGroupMessageSignalPort.saveConfigValues(normalized.toConfigMap(), CONFIG_DESCRIPTIONS)

        auditLogPort.recordAudit(
            adminId = adminId(request),
            adminUsername = adminUsername(request),
            action = "UPDATE_GROUP_MESSAGE_SIGNAL_CONFIG",
            targetType = "SYSTEM_CONFIG",
            targetId = null,
            details = "更新消息分发灰度配置: enabled=${normalized.enabled}, rollout=${normalized.rolloutPercent}%",
            ipAddress = null,
        )

        return ResponseEntity.ok(ApiResponse.ok(normalized, "消息分发配置已更新"))
    }

    private fun readConfig(): GroupMessageSignalAdminConfigResponse {
        val configs = adminGroupMessageSignalPort.getConfigValues(CONFIG_KEYS)
        fun raw(key: String): String? = configs[key]
        fun int(key: String, default: Int, min: Int, max: Int = Int.MAX_VALUE): Int =
            (raw(key)?.toIntOrNull() ?: default).coerceIn(min, max)
        fun long(key: String, default: Long, min: Long): Long =
            maxOf(raw(key)?.toLongOrNull() ?: default, min)

        return GroupMessageSignalAdminConfigResponse(
            enabled = raw(KEY_ENABLED)?.toBooleanStrictOrNull() ?: false,
            groupMemberThreshold = int(KEY_GROUP_MEMBER_THRESHOLD, 500, min = 1),
            onlineMemberThreshold = int(KEY_ONLINE_MEMBER_THRESHOLD, 100, min = 1),
            minProtocolVersion = int(KEY_MIN_PROTOCOL_VERSION, 3, min = 1),
            syncDefaultLimit = int(KEY_SYNC_DEFAULT_LIMIT, 100, min = 1),
            syncMaxLimit = int(KEY_SYNC_MAX_LIMIT, 500, min = 1),
            rolloutPercent = int(KEY_ROLLOUT_PERCENT, 0, min = 0, max = 100),
            localConnectionOnly = raw(KEY_LOCAL_CONNECTION_ONLY)?.toBooleanStrictOrNull() ?: true,
            serverSignalCoalesceMs = long(KEY_SERVER_SIGNAL_COALESCE_MS, 100, min = 0),
            forceFullPushMessageTypes = normalizeMessageTypes(raw(KEY_FORCE_FULL_PUSH_MESSAGE_TYPES)),
        )
    }

    private fun GroupMessageSignalAdminConfigRequest.normalized(): GroupMessageSignalAdminConfigResponse =
        GroupMessageSignalAdminConfigResponse(
            enabled = enabled ?: false,
            groupMemberThreshold = (groupMemberThreshold ?: 500).coerceAtLeast(1),
            onlineMemberThreshold = (onlineMemberThreshold ?: 100).coerceAtLeast(1),
            minProtocolVersion = (minProtocolVersion ?: 3).coerceAtLeast(1),
            syncDefaultLimit = (syncDefaultLimit ?: 100).coerceAtLeast(1),
            syncMaxLimit = (syncMaxLimit ?: 500).coerceAtLeast(1),
            rolloutPercent = (rolloutPercent ?: 0).coerceIn(0, 100),
            localConnectionOnly = localConnectionOnly ?: true,
            serverSignalCoalesceMs = (serverSignalCoalesceMs ?: 100).coerceAtLeast(0),
            forceFullPushMessageTypes = normalizeMessageTypes(forceFullPushMessageTypes),
        )

    private fun GroupMessageSignalAdminConfigResponse.toConfigMap(): Map<String, String> =
        linkedMapOf(
            KEY_ENABLED to enabled.toString(),
            KEY_GROUP_MEMBER_THRESHOLD to groupMemberThreshold.toString(),
            KEY_ONLINE_MEMBER_THRESHOLD to onlineMemberThreshold.toString(),
            KEY_MIN_PROTOCOL_VERSION to minProtocolVersion.toString(),
            KEY_SYNC_DEFAULT_LIMIT to syncDefaultLimit.toString(),
            KEY_SYNC_MAX_LIMIT to syncMaxLimit.toString(),
            KEY_ROLLOUT_PERCENT to rolloutPercent.toString(),
            KEY_LOCAL_CONNECTION_ONLY to localConnectionOnly.toString(),
            KEY_SERVER_SIGNAL_COALESCE_MS to serverSignalCoalesceMs.toString(),
            KEY_FORCE_FULL_PUSH_MESSAGE_TYPES to forceFullPushMessageTypes,
        )

    private fun normalizeMessageTypes(raw: String?): String {
        val types = raw
            ?.split(',', '\n', ';', '，')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()
        return (types.ifEmpty { DEFAULT_FORCE_FULL_PUSH_MESSAGE_TYPES }).joinToString("\n")
    }

    private fun adminId(request: HttpServletRequest): String = request.getAttribute("adminId") as String

    private fun adminUsername(request: HttpServletRequest): String =
        request.getAttribute("adminUsername") as? String ?: ""

    companion object {
        const val KEY_ENABLED = "group_message_signal.enabled"
        const val KEY_GROUP_MEMBER_THRESHOLD = "group_message_signal.group_member_threshold"
        const val KEY_ONLINE_MEMBER_THRESHOLD = "group_message_signal.online_member_threshold"
        const val KEY_MIN_PROTOCOL_VERSION = "group_message_signal.min_protocol_version"
        const val KEY_SYNC_DEFAULT_LIMIT = "group_message_signal.sync_default_limit"
        const val KEY_SYNC_MAX_LIMIT = "group_message_signal.sync_max_limit"
        const val KEY_ROLLOUT_PERCENT = "group_message_signal.rollout_percent"
        const val KEY_LOCAL_CONNECTION_ONLY = "group_message_signal.local_connection_only"
        const val KEY_SERVER_SIGNAL_COALESCE_MS = "group_message_signal.server_signal_coalesce_ms"
        const val KEY_FORCE_FULL_PUSH_MESSAGE_TYPES = "group_message_signal.force_full_push_message_types"

        private val DEFAULT_FORCE_FULL_PUSH_MESSAGE_TYPES = listOf(
            "call_invite",
            "emergency",
            "system_critical",
            "red_packet",
            "mention",
        )

        val CONFIG_KEYS = listOf(
            KEY_ENABLED,
            KEY_GROUP_MEMBER_THRESHOLD,
            KEY_ONLINE_MEMBER_THRESHOLD,
            KEY_MIN_PROTOCOL_VERSION,
            KEY_SYNC_DEFAULT_LIMIT,
            KEY_SYNC_MAX_LIMIT,
            KEY_ROLLOUT_PERCENT,
            KEY_LOCAL_CONNECTION_ONLY,
            KEY_SERVER_SIGNAL_COALESCE_MS,
            KEY_FORCE_FULL_PUSH_MESSAGE_TYPES,
        )

        private val CONFIG_DESCRIPTIONS = mapOf(
            KEY_ENABLED to "消息分发新版 Signal Pull 总开关",
            KEY_GROUP_MEMBER_THRESHOLD to "启用 Signal Pull 的群成员阈值",
            KEY_ONLINE_MEMBER_THRESHOLD to "启用 Signal Pull 的在线成员阈值",
            KEY_MIN_PROTOCOL_VERSION to "客户端支持 Signal Pull 的最低协议版本",
            KEY_SYNC_DEFAULT_LIMIT to "Signal Pull 默认同步 limit",
            KEY_SYNC_MAX_LIMIT to "Signal Pull 最大同步 limit",
            KEY_ROLLOUT_PERCENT to "消息分发灰度比例",
            KEY_LOCAL_CONNECTION_ONLY to "是否仅本节点连接使用 Signal Pull",
            KEY_SERVER_SIGNAL_COALESCE_MS to "服务端 signal 合并窗口毫秒数",
            KEY_FORCE_FULL_PUSH_MESSAGE_TYPES to "强制完整推送的消息类型",
        )
    }
}
