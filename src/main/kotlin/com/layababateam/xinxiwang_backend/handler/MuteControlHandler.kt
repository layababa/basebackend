package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.MeetingRealtimePort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

/**
 * 宣讲会静音控制信令入口。
 *
 * SDK 负责基础协议校验，业务侧负责会议状态、主持人权限和目标用户管控规则。
 */
@Component
class MuteControlHandler(
    private val meetingRealtimePort: MeetingRealtimePort,
) : MessageHandler {
    override val type: String = "mute_control"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val meetingId = data["meetingId"] as? String
            ?: throw IllegalArgumentException("meetingId 不能为空")
        val action = data["action"] as? String
            ?: throw IllegalArgumentException("action 不能为空")
        val muteTypes = (data["muteTypes"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?: listOf(TYPE_AUDIO)

        require(action in VALID_ACTIONS) { "无效的 action: $action" }
        require(muteTypes.isNotEmpty()) { "muteTypes 不能为空" }
        require(muteTypes.all { it in VALID_MUTE_TYPES }) { "无效的 muteTypes: $muteTypes" }

        val targetUserId = if (action in SINGLE_USER_ACTIONS) {
            data["targetUserId"] as? String
                ?: throw IllegalArgumentException("targetUserId 不能为空")
        } else {
            null
        }
        val operatorName = data["operatorName"] as? String ?: ""

        meetingRealtimePort.handleMuteControl(
            userId = userId,
            meetingId = meetingId,
            action = action,
            muteTypes = muteTypes,
            targetUserId = targetUserId,
            operatorName = operatorName,
        )
    }

    private companion object {
        const val ACTION_MUTE_ALL = "mute_all"
        const val ACTION_UNMUTE_ALL = "unmute_all"
        const val ACTION_MUTE_USER = "mute_user"
        const val ACTION_UNMUTE_USER = "unmute_user"
        const val TYPE_AUDIO = "audio"
        const val TYPE_TEXT = "text"
        const val TYPE_CAMERA = "camera"
        const val TYPE_SCREEN_SHARE = "screen_share"

        val VALID_ACTIONS = setOf(
            ACTION_MUTE_ALL,
            ACTION_UNMUTE_ALL,
            ACTION_MUTE_USER,
            ACTION_UNMUTE_USER,
        )
        val SINGLE_USER_ACTIONS = setOf(ACTION_MUTE_USER, ACTION_UNMUTE_USER)
        val VALID_MUTE_TYPES = setOf(TYPE_AUDIO, TYPE_TEXT, TYPE_CAMERA, TYPE_SCREEN_SHARE)
    }
}
