package com.layababateam.xinxiwang_backend.netty

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.service.UserSessionManager
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.springframework.stereotype.Component

@Component
class WsResponseHelper(
    private val userSessionManager: UserSessionManager,
    private val objectMapper: ObjectMapper
) {
    data class CoalescedResponseTarget(
        val ctx: ChannelHandlerContext,
        val requestId: String?
    )

    companion object {
        val CURRENT_REQUEST_ID: ThreadLocal<String?> = ThreadLocal()
        val CURRENT_COALESCED_TARGETS: ThreadLocal<List<CoalescedResponseTarget>> = ThreadLocal()
    }

    /**
     * 协议感知发送：根据 channel 的协议类型，选择 JSON 文本帧或 Protobuf 二进制帧
     */
    fun send(ctx: ChannelHandlerContext, data: Map<String, Any?>) {
        val requestId = CURRENT_REQUEST_ID.get()
        if (requestId != null) CURRENT_REQUEST_ID.set(null)
        sendWithRequestId(ctx, data, requestId)

        CURRENT_COALESCED_TARGETS.get()
            ?.filter { it.ctx.channel().isActive }
            ?.forEach { target ->
                sendWithRequestId(target.ctx, data, target.requestId)
            }
    }

    fun sendWithRequestId(ctx: ChannelHandlerContext, data: Map<String, Any?>, requestId: String?) {
        val payload = if (requestId != null) data + ("requestId" to requestId) else data
        userSessionManager.sendToChannel(ctx.channel(), objectMapper.writeValueAsString(payload))
    }

    /**
     * 协议感知发送（Channel 版本）：用于跨节点路由等不持有 ctx 的场景
     * jsonMessage 是已序列化的 JSON 字符串
     */
    fun sendRawJson(channel: Channel, jsonMessage: String) {
        userSessionManager.sendToChannel(channel, jsonMessage)
    }

    /**
     * 强制 JSON 发送（auth 阶段使用）
     */
    fun sendJson(ctx: ChannelHandlerContext, data: Any) {
        userSessionManager.sendJsonToChannel(ctx.channel(), objectMapper.writeValueAsString(data))
    }
}
