package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.CallingHeartbeatPort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class CallingHandler(
    private val callingHeartbeatPort: CallingHeartbeatPort,
) : MessageHandler {
    override val type = "calling"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val roomId = (data["roomId"] as? Number)?.toInt() ?: return
        callingHeartbeatPort.updateCallingHeartbeat(userId, roomId)
    }
}
