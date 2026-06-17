package com.layababateam.xinxiwang_backend.handler.v3

import com.layababateam.xinxiwang_backend.handler.MessageHandler
import com.layababateam.xinxiwang_backend.netty.ChannelDeviceResolver
import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.V3ConversationPort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class V3ConversationListHandler(
    private val v3ConversationPort: V3ConversationPort,
    private val wsResponseSender: WsResponseSender,
    private val channelDeviceResolver: ChannelDeviceResolver,
) : MessageHandler {
    override val type = "v3_conversation_list"

    companion object {
        private const val DEFAULT_LIMIT = 50
        private const val MAX_LIMIT = 200
    }

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val afterTimestamp = (data["afterTimestamp"] as? Number)?.toLong()
        val beforeTimestamp = (data["beforeTimestamp"] as? Number)?.toLong()
        val limit = (data["limit"] as? Number)?.toInt()?.coerceIn(1, MAX_LIMIT) ?: DEFAULT_LIMIT
        val deviceId = channelDeviceResolver.getDeviceId(ctx.channel())

        val (conversations, hasMore) = v3ConversationPort.getConversationListPaginated(
            userId,
            limit,
            beforeTimestamp,
            deviceId,
        )

        val response = mutableMapOf<String, Any?>(
            "type" to "v3_conversation_list_response",
            "data" to conversations,
            "hasMore" to hasMore,
        )
        if (afterTimestamp != null) {
            response["afterTimestamp"] = afterTimestamp
        }
        wsResponseSender.send(ctx, response)
    }
}

@Component
class V3ConversationSyncHandler(
    private val v3ConversationPort: V3ConversationPort,
    private val wsResponseSender: WsResponseSender,
    private val channelDeviceResolver: ChannelDeviceResolver,
) : MessageHandler {
    override val type = "v3_conversation_sync"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val afterTimestamp = (data["afterTimestamp"] as? Number)?.toLong() ?: 0L
        val deviceId = channelDeviceResolver.getDeviceId(ctx.channel())
        val conversations = v3ConversationPort.getConversationListAfter(userId, afterTimestamp, deviceId)

        wsResponseSender.send(
            ctx,
            mapOf(
                "type" to "v3_conversation_sync_response",
                "data" to conversations,
                "syncTimestamp" to System.currentTimeMillis(),
            ),
        )
    }
}
