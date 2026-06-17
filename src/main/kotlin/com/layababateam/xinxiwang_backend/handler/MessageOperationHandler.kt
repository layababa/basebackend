package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.ChannelDeviceResolver
import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.MessageOperationPort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class MessageRecallHandler(
    private val messageOperationPort: MessageOperationPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "message_recall"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val messageId = data["messageId"] as? String
            ?: throw IllegalArgumentException("消息ID不能为空")
        try {
            messageOperationPort.recallMessage(userId, messageId)
        } catch (e: IllegalArgumentException) {
            wsResponseSender.send(ctx, mapOf("type" to "error", "message" to (e.message ?: "撤回失败")))
        }
    }
}

@Component
class MessageDeleteHandler(
    private val messageOperationPort: MessageOperationPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "message_delete"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val messageId = data["messageId"] as? String
            ?: throw IllegalArgumentException("消息ID不能为空")
        val forAll = data["forAll"] as? Boolean ?: false
        messageOperationPort.deleteMessage(userId, messageId, forAll, ctx.channel())
        wsResponseSender.send(
            ctx,
            mapOf("type" to "message_delete_success", "messageId" to messageId, "forAll" to forAll),
        )
    }
}

@Component
class MessageForwardHandler(
    private val messageOperationPort: MessageOperationPort,
) : MessageHandler {
    override val type: String = "message_forward"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val messageId = data["messageId"] as? String
            ?: throw IllegalArgumentException("消息ID不能为空")
        val toConversationId = data["toConversationId"] as? String
            ?: throw IllegalArgumentException("目标会话ID不能为空")
        messageOperationPort.forwardMessage(userId, messageId, toConversationId)
    }
}

@Component
class UpdateReadPointHandler(
    private val messageOperationPort: MessageOperationPort,
    private val channelDeviceResolver: ChannelDeviceResolver,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "update_read_point"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String
            ?: throw IllegalArgumentException("会话ID不能为空")
        val seqId = (data["seqId"] as? Number)?.toLong() ?: 0L
        val deviceId = (data["deviceId"] as? String)?.takeIf { it.isNotEmpty() }
            ?: channelDeviceResolver.getDeviceId(ctx.channel())
        val updated = messageOperationPort.updateReadPoint(userId, conversationId, seqId, deviceId)
        if (!updated) {
            return
        }
        wsResponseSender.send(
            ctx,
            mapOf(
                "type" to "read_point_updated",
                "conversationId" to conversationId,
                "readSeqId" to seqId,
                "userId" to userId,
            ),
        )
    }
}

@Component
class GetUserInfoHandler(
    private val messageOperationPort: MessageOperationPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "getUserInfo"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val targetUserId = data["userId"] as? String ?: userId
        val userInfo = messageOperationPort.getUserInfo(userId, targetUserId)
        if (userInfo != null) {
            wsResponseSender.send(ctx, mapOf("type" to "userInfo", "data" to userInfo))
        } else {
            wsResponseSender.send(ctx, mapOf("type" to "error", "message" to "用户不存在"))
        }
    }
}
