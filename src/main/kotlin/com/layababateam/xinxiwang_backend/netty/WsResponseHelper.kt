package com.layababateam.xinxiwang_backend.netty

import tools.jackson.databind.json.JsonMapper
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class WsResponseHelper(
    private val channelMessageSender: ChannelMessageSender,
    private val objectMapper: JsonMapper,
) : WsResponseSender {
    companion object {
        val CURRENT_REQUEST_ID: ThreadLocal<String?> = ThreadLocal()
    }

    override fun send(ctx: ChannelHandlerContext, data: Map<String, Any?>) {
        val requestId = CURRENT_REQUEST_ID.get()
        if (requestId != null) CURRENT_REQUEST_ID.set(null)
        val payload = if (requestId != null) data + ("requestId" to requestId) else data
        channelMessageSender.sendToChannel(ctx.channel(), objectMapper.writeValueAsString(payload))
    }

    fun sendRawJson(channel: Channel, jsonMessage: String) {
        channelMessageSender.sendToChannel(channel, jsonMessage)
    }

    fun sendJson(ctx: ChannelHandlerContext, data: Any) {
        channelMessageSender.sendJsonToChannel(ctx.channel(), objectMapper.writeValueAsString(data))
    }
}
