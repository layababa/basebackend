package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.MeetingRealtimePort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class MeetingChatHandler(
    private val meetingRealtimePort: MeetingRealtimePort,
) : MessageHandler {
    override val type: String = "meeting_chat"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val meetingId = data["meetingId"] as? String
            ?: throw IllegalArgumentException("meetingId 不能为空")
        val content = data["content"] as? String
            ?: throw IllegalArgumentException("content 不能为空")
        meetingRealtimePort.sendChatMessage(userId, meetingId, content)
    }
}
