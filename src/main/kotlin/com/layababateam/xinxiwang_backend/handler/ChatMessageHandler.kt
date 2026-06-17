package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.ChatMessageCommand
import com.layababateam.xinxiwang_backend.service.ChatMessagePort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class ChatMessageHandler(
    private val chatMessagePort: ChatMessagePort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "chat_message"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String
            ?: throw IllegalArgumentException("会话ID不能为空")
        val content = data["content"] as? String
            ?: throw IllegalArgumentException("消息内容不能为空")
        if (content.length > CONTENT_LIMIT) {
            throw IllegalArgumentException("消息内容不能超过5000字")
        }

        val command = ChatMessageCommand(
            conversationId = conversationId,
            clientMessageId = data["clientMessageId"] as? String,
            content = content,
            contentType = (data["contentType"] as? Number)?.toInt() ?: 0,
            mentions = (data["mentions"] as? List<*>)?.filterIsInstance<String>()?.take(MENTION_LIMIT) ?: emptyList(),
            replyToMessageId = data["replyToMessageId"] as? String,
        )

        val result = chatMessagePort.sendChatMessage(userId, command, ctx.channel())
        if (result.response != null) {
            wsResponseSender.send(ctx, result.response)
        }
    }

    private companion object {
        const val CONTENT_LIMIT = 5000
        const val MENTION_LIMIT = 50
    }
}
