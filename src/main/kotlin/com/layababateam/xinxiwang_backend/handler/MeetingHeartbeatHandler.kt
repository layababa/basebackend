package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.MeetingRealtimePort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class MeetingHeartbeatHandler(
    private val meetingRealtimePort: MeetingRealtimePort,
) : MessageHandler {
    override val type: String = "meeting_heartbeat"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val meetingId = data["meetingId"]?.toString()?.takeIf { it.isNotBlank() } ?: return
        val roomId = (data["roomId"] as? Number)?.toInt() ?: return
        meetingRealtimePort.updateHeartbeat(userId, meetingId, roomId)
    }
}
