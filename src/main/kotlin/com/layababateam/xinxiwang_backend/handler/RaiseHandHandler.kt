package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.MeetingRealtimePort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

/**
 * 宣讲会举手信令入口。
 *
 * SDK 负责 action 和基础参数解析，业务侧负责主持人权限、会议状态和广播范围。
 */
@Component
class RaiseHandHandler(
    private val meetingRealtimePort: MeetingRealtimePort,
) : MessageHandler {
    override val type: String = "raise_hand"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val meetingId = data["meetingId"] as? String
            ?: throw IllegalArgumentException("meetingId 不能为空")
        val action = data["action"] as? String
            ?: throw IllegalArgumentException("action 不能为空")

        require(action in VALID_ACTIONS) { "无效的 action: $action" }

        val targetUserId = if (action == ACTION_ALLOW) {
            data["targetUserId"] as? String
                ?: throw IllegalArgumentException("允许发言时 targetUserId 不能为空")
        } else {
            null
        }
        val userName = data["userName"] as? String ?: ""

        meetingRealtimePort.handleRaiseHand(userId, meetingId, action, targetUserId, userName)
    }

    private companion object {
        const val ACTION_ALLOW = "allow"
        val VALID_ACTIONS = setOf("raise", "lower", ACTION_ALLOW)
    }
}
