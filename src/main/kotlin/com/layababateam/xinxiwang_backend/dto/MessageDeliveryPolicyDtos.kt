package com.layababateam.xinxiwang_backend.dto

import com.layababateam.xinxiwang_backend.model.MessageDeliveryMode
import com.layababateam.xinxiwang_backend.model.MessageDeliveryPolicy
import com.layababateam.xinxiwang_backend.model.MessageDeliveryPolicyScope

data class MessageDeliveryPolicyRequest(
    val name: String? = null,
    val scope: MessageDeliveryPolicyScope,
    val mode: MessageDeliveryMode,
    val enabled: Boolean = true,
    val userId: String? = null,
    val userIds: List<String> = emptyList(),
    val platform: String? = null,
    val minClientVersion: String? = null,
    val maxClientVersion: String? = null,
    val priority: Int = 0,
    val note: String? = null,
) {
    fun validationError(): String? {
        if (mode == MessageDeliveryMode.AUTO && scope != MessageDeliveryPolicyScope.PLATFORM_VERSION) {
            return "单用户或批量用户策略请选择旧版完整推送或新版 Signal Pull"
        }
        return when (scope) {
            MessageDeliveryPolicyScope.USER -> {
                if (userId.isNullOrBlank()) "单用户策略必须填写 userId" else null
            }
            MessageDeliveryPolicyScope.USER_BATCH -> {
                if (normalizedUserIds().isEmpty()) "批量用户策略必须填写 userIds" else null
            }
            MessageDeliveryPolicyScope.PLATFORM_VERSION -> {
                if (platform.isNullOrBlank()) "平台版本策略必须填写 platform" else null
            }
        }
    }

    fun toPolicy(
        id: String? = null,
        createdBy: String? = null,
        createdAt: Long = System.currentTimeMillis(),
        updatedBy: String? = null,
    ): MessageDeliveryPolicy =
        MessageDeliveryPolicy(
            id = id,
            name = name?.trim().orEmpty(),
            scope = scope,
            mode = mode,
            enabled = enabled,
            userId = userId?.trim()?.takeIf { it.isNotEmpty() },
            userIds = normalizedUserIds(),
            platform = platform?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            minClientVersion = minClientVersion?.trim()?.takeIf { it.isNotEmpty() },
            maxClientVersion = maxClientVersion?.trim()?.takeIf { it.isNotEmpty() },
            priority = priority.coerceIn(-10_000, 10_000),
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            createdBy = createdBy,
            updatedBy = updatedBy,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
        )

    private fun normalizedUserIds(): List<String> =
        userIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_BATCH_USERS)

    companion object {
        private const val MAX_BATCH_USERS = 10_000
    }
}
