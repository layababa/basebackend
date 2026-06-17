package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.MeetingRealtimePort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class MeetingPermissionHandler(
    private val meetingRealtimePort: MeetingRealtimePort,
) : MessageHandler {
    override val type: String = "meeting_permission"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val action = data["action"] as? String
            ?: throw IllegalArgumentException("action 不能为空")
        val meetingId = data["meetingId"] as? String
            ?: throw IllegalArgumentException("meetingId 不能为空")
        meetingRealtimePort.handlePermissionSignal(userId, meetingId, action, data)
    }
}
