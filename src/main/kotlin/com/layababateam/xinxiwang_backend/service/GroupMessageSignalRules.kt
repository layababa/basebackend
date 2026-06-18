package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ContentType

data class GroupMessageSignalConfig(
    val enabled: Boolean = false,
    val groupMemberThreshold: Int = 500,
    val onlineMemberThreshold: Int = 100,
    val minProtocolVersion: Int = 3,
    val syncDefaultLimit: Int = 100,
    val syncMaxLimit: Int = 500,
    val rolloutPercent: Int = 0,
    val localConnectionOnly: Boolean = true,
    val serverSignalCoalesceMs: Long = 100,
    val forceFullPushMessageTypes: Set<String> = setOf(
        "call_invite",
        "emergency",
        "system_critical",
        "red_packet",
        "mention",
    ),
)

/**
 * 群消息 signal/pull 公共判定规则。
 *
 * 接入方负责提供配置和连接上下文，SDK 只维护纯规则，避免绑定业务仓储或会话实现。
 */
object GroupMessageSignalRules {
    fun classifyMessageType(contentType: Int, mentions: List<String> = emptyList()): String {
        if (mentions.isNotEmpty()) return "mention"
        if (contentType == LEGACY_SYSTEM_CONTENT_TYPE) return "system_critical"
        return when (ContentType.fromValue(contentType)) {
            ContentType.RED_PACKET -> "red_packet"
            ContentType.CALL -> "call_invite"
            ContentType.SYSTEM -> "system_critical"
            ContentType.MEETING -> "system_critical"
            ContentType.TRANSFER -> "system_critical"
            else -> ContentType.fromValue(contentType)?.name?.lowercase() ?: "content_type_$contentType"
        }
    }

    fun shouldUseSignalForGroup(
        config: GroupMessageSignalConfig,
        groupId: String,
        messageType: String,
        memberCount: Int,
        onlineCount: Int,
    ): Boolean {
        if (!config.enabled) return false
        if (messageType in config.forceFullPushMessageTypes) return false
        if (!rolloutHit(groupId, config.rolloutPercent)) return false
        return memberCount >= config.groupMemberThreshold || onlineCount >= config.onlineMemberThreshold
    }

    fun rolloutHit(groupId: String, rolloutPercent: Int): Boolean {
        if (rolloutPercent <= 0) return false
        if (rolloutPercent >= 100) return true
        val bucket = Math.floorMod(groupId.hashCode(), 100)
        return bucket < rolloutPercent
    }

    fun supportsSignalDelivery(
        config: GroupMessageSignalConfig,
        isOnline: Boolean,
        isLocalConnection: Boolean,
        protocolVersion: Int,
        supportsGroupMessageSignal: Boolean,
        supportsV3Sync: Boolean,
        supportsGroupSeqCursor: Boolean,
    ): Boolean {
        if (!isOnline) return false
        if (config.localConnectionOnly && !isLocalConnection) return false
        if (protocolVersion < config.minProtocolVersion) return false
        return supportsGroupMessageSignal && supportsV3Sync && supportsGroupSeqCursor
    }

    private const val LEGACY_SYSTEM_CONTENT_TYPE = 6
}
