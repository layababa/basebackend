package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.ChannelDeviceResolver
import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.ConversationInfoPort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class GetConversationInfoHandler(
    private val conversationInfoPort: ConversationInfoPort,
    private val wsResponseSender: WsResponseSender,
    private val channelDeviceResolver: ChannelDeviceResolver,
) : MessageHandler {
    override val type = "get_conversation_info"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String
            ?: throw IllegalArgumentException("\u4f1a\u8bddID\u4e0d\u80fd\u4e3a\u7a7a")
        val deviceId = channelDeviceResolver.getDeviceId(ctx.channel())
        val dto = conversationInfoPort.getConversationInfo(userId, conversationId, deviceId)
        if (dto != null) {
            wsResponseSender.send(
                ctx,
                mapOf(
                    "type" to "conversation_info_response",
                    "data" to dto,
                ),
            )
        } else {
            wsResponseSender.send(
                ctx,
                mapOf(
                    "type" to "conversation_info_response",
                    "data" to null,
                    "error" to "\u4f1a\u8bdd\u4e0d\u5b58\u5728\u6216\u65e0\u6743\u8bbf\u95ee",
                ),
            )
        }
    }
}
