package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.Meeting
import com.layababateam.xinxiwang_backend.model.MeetingPermissionSettings

/**
 * 会议权限纯规则。
 *
 * 主持人/管理员身份由业务侧结合实时管理权计算后传入，这里只处理权限类型、
 * 禁止名单、授权列表和会议级开关的组合规则。
 */
object MeetingPermissionRules {
    const val PERMISSION_CHAT = "chat"
    const val PERMISSION_LINK_MIC = "link_mic"
    const val PERMISSION_SCREEN_SHARE = "screen_share"

    val permissionTypes = setOf(
        PERMISSION_CHAT,
        PERMISSION_LINK_MIC,
        PERMISSION_SCREEN_SHARE,
    )

    fun isValidPermissionType(type: String): Boolean =
        type in permissionTypes

    fun validatePermissionType(type: String) {
        require(isValidPermissionType(type)) { "无效的权限类型: $type" }
    }

    fun permissionLabel(type: String): String = when (type) {
        PERMISSION_CHAT -> "聊天"
        PERMISSION_LINK_MIC -> "连麦"
        PERMISSION_SCREEN_SHARE -> "共享"
        else -> "会议"
    }

    fun isDenied(settings: MeetingPermissionSettings, userId: String, type: String): Boolean = when (type) {
        PERMISSION_CHAT -> userId in settings.deniedChatUsers
        PERMISSION_LINK_MIC -> userId in settings.deniedLinkMicUsers
        PERMISSION_SCREEN_SHARE -> userId in settings.deniedScreenShareUsers
        else -> false
    }

    fun clearDeny(
        settings: MeetingPermissionSettings,
        userId: String,
        type: String,
    ): MeetingPermissionSettings = when (type) {
        PERMISSION_CHAT -> settings.copy(deniedChatUsers = settings.deniedChatUsers.filter { it != userId })
        PERMISSION_LINK_MIC -> settings.copy(deniedLinkMicUsers = settings.deniedLinkMicUsers.filter { it != userId })
        PERMISSION_SCREEN_SHARE -> settings.copy(
            deniedScreenShareUsers = settings.deniedScreenShareUsers.filter { it != userId },
        )
        else -> settings
    }

    fun updateDeniedList(current: List<String>, userId: String, denied: Boolean): List<String> =
        if (denied) {
            (current + userId).distinct()
        } else {
            current.filter { it != userId }
        }

    fun hasExplicitGrant(meeting: Meeting, userId: String, type: String): Boolean =
        meeting.permissionGrants.any { it.userId == userId && it.type == type } ||
            (type == PERMISSION_LINK_MIC && meeting.approvedLinkMicUsers.any { it.userId == userId })

    fun canUsePermission(
        meeting: Meeting,
        userId: String,
        type: String,
        isModerator: Boolean,
    ): Boolean {
        validatePermissionType(type)
        if (isModerator) return true
        if (userId !in meeting.participants) return false

        val settings = meeting.permissionSettings
        if (isDenied(settings, userId, type)) return false
        if (type == PERMISSION_LINK_MIC && settings.linkMicLockedByQuickAction) return false
        if (hasExplicitGrant(meeting, userId, type)) return true

        return when (type) {
            PERMISSION_CHAT -> settings.allowChat
            PERMISSION_LINK_MIC -> settings.allowLinkMic
            // 屏幕共享必须单独批准；公共开关只控制申请入口。
            PERMISSION_SCREEN_SHARE -> false
            else -> false
        }
    }
}
