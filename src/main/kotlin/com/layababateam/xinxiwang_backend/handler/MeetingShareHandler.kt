package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.MeetingRealtimePort
import io.netty.channel.ChannelHandlerContext
import io.netty.util.AttributeKey
import org.springframework.stereotype.Component

@Component
class MeetingShareHandler(
    private val meetingRealtimePort: MeetingRealtimePort,
) : MessageHandler {
    override val type: String = "meeting_share"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val meetingId = data["meetingId"] as? String
            ?: throw IllegalArgumentException("meetingId不能为空")
        val toConversationId = data["toConversationId"] as? String
            ?: throw IllegalArgumentException("toConversationId不能为空")
        val backendCompatVersion = ctx.channel()
            .attr(BACKEND_COMPAT_VERSION_KEY)
            .get() ?: UNKNOWN_BACKEND_COMPAT_VERSION
        meetingRealtimePort.shareMeeting(userId, meetingId, toConversationId, backendCompatVersion)
    }

    private companion object {
        const val UNKNOWN_BACKEND_COMPAT_VERSION = 0
        val BACKEND_COMPAT_VERSION_KEY: AttributeKey<Int> = AttributeKey.valueOf("backend.compat.version")
    }
}
