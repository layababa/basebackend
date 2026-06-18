package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.BroadcastPermissions

/**
 * 宣讲会成员角色与权限规则。
 *
 * 这里只保留不依赖仓储和推送通道的纯计算，业务侧负责读取会议/用户上下文。
 */
object BroadcastMeetingRules {
    const val ROLE_SPEAKER = "speaker"
    const val ROLE_ADMIN = "admin"
    const val ROLE_VIEWER = "viewer"

    fun roleOf(
        userId: String,
        speakerId: String,
        adminIds: Collection<String>,
    ): String = when {
        speakerId == userId -> ROLE_SPEAKER
        adminIds.contains(userId) -> ROLE_ADMIN
        else -> ROLE_VIEWER
    }

    fun permissionsOf(
        userId: String,
        speakerId: String,
        adminIds: Collection<String>,
        mutedUserIds: Collection<String>,
        allMuted: Boolean,
    ): BroadcastPermissions {
        val isAdmin = adminIds.contains(userId)
        val isSpeaker = speakerId == userId
        val muted = allMuted || mutedUserIds.contains(userId)
        return BroadcastPermissions(
            canEnd = isAdmin,
            canKick = isAdmin,
            canMute = isAdmin,
            canMuteAll = isAdmin,
            canTransferSpeaker = isAdmin,
            canSendRedPacket = isAdmin,
            canStartScreenShare = isSpeaker,
            canSendBarrage = !muted,
            canRaiseHand = !isAdmin && !isSpeaker,
        )
    }
}
