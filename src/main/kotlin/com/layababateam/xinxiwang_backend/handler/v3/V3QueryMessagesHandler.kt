package com.layababateam.xinxiwang_backend.handler.v3

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.handler.MessageHandler
import com.layababateam.xinxiwang_backend.service.PaginationRules
import com.layababateam.xinxiwang_backend.service.V3MessageSyncPort
import com.layababateam.xinxiwang_backend.service.V3MessageSyncResponseSink
import com.layababateam.xinxiwang_backend.service.V3QueryMessagesRequest
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.springframework.stereotype.Component

@Component
class V3QueryMessagesHandler(
    private val v3MessageSyncPort: V3MessageSyncPort,
    private val objectMapper: ObjectMapper,
) : MessageHandler {
    override val type = "v3_query_messages"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String
            ?: throw IllegalArgumentException("会话ID不能为空")
        val request = V3QueryMessagesRequest(
            conversationId = conversationId,
            afterSeqId = (data["afterSeqId"] as? Number)?.toLong(),
            beforeSeqId = (data["beforeSeqId"] as? Number)?.toLong(),
            deliveryDateStart = (data["deliveryDateStart"] as? Number)?.toLong(),
            deliveryDateEnd = (data["deliveryDateEnd"] as? Number)?.toLong(),
            maxCount = (data["maxCount"] as? Number)?.toInt()?.let {
                PaginationRules.pageSize(it, MAX_COUNT)
            } ?: DEFAULT_MAX_COUNT,
            descending = data["descending"] as? Boolean ?: true,
            contentTypes = (data["contentTypes"] as? List<*>)?.filterIsInstance<Number>()?.map { it.toInt() },
            withTotal = data["withTotal"] as? Boolean ?: false,
            hasRequestId = (data["requestId"] as? String)?.isNotBlank() == true,
        )

        v3MessageSyncPort.queryMessages(
            ctx = ctx,
            userId = userId,
            request = request,
            responseSink = V3MessageSyncResponseSink { payload ->
                ctx.writeAndFlush(TextWebSocketFrame(objectMapper.writeValueAsString(payload)))
            },
        )
    }

    private companion object {
        const val DEFAULT_MAX_COUNT = 50
        const val MAX_COUNT = 100
    }
}
