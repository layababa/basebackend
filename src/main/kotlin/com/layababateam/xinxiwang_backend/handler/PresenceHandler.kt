package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.PresenceLookupPort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class PresenceHandler(
    private val presenceLookupPort: PresenceLookupPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "presence_query"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val targetUserId = data["targetUserId"] as? String ?: return
        val response = mapOf(
            "type" to "presence_response",
            "data" to mapOf(
                "userId" to targetUserId,
                "online" to presenceLookupPort.isOnlineGlobally(targetUserId),
            ),
        )
        wsResponseSender.send(ctx, response)
    }
}
