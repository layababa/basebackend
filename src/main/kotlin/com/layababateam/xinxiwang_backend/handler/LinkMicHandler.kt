package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.MeetingRealtimePort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

/**
 * 宣讲会连麦信令入口。
 *
 * SDK 负责 WebSocket 消息类型和基础参数解析，业务侧负责会议权限、连麦状态和推送细节。
 */
@Component
class LinkMicHandler(
    private val meetingRealtimePort: MeetingRealtimePort,
) : MessageHandler {
    override val type: String = "link_mic"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val action = data["action"] as? String
            ?: throw IllegalArgumentException("action 不能为空")
        val meetingId = data["meetingId"] as? String
            ?: throw IllegalArgumentException("meetingId 不能为空")

        meetingRealtimePort.handleLinkMic(
            userId = userId,
            meetingId = meetingId,
            action = action,
            data = data,
        )
    }
}
