package com.layababateam.xinxiwang_backend.handler.v3

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.handler.MessageHandler
import com.layababateam.xinxiwang_backend.service.V3BatchSyncRequest
import com.layababateam.xinxiwang_backend.service.V3MessageSyncPort
import com.layababateam.xinxiwang_backend.service.V3MessageSyncResponseSink
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.springframework.stereotype.Component

@Component
class V3BatchSyncHandler(
    private val v3MessageSyncPort: V3MessageSyncPort,
    private val objectMapper: ObjectMapper,
) : MessageHandler {
    override val type = "v3_batch_sync"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val rawRequests = (data["conversations"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
        if (rawRequests.isNullOrEmpty()) {
            throw IllegalArgumentException("conversations 不能为空")
        }
        if (rawRequests.size > MAX_CONVERSATIONS) {
            throw IllegalArgumentException("单次批量同步上限 $MAX_CONVERSATIONS 个会话")
        }
        val requests = rawRequests.mapNotNull { item ->
            val conversationId = item["conversationId"] as? String ?: return@mapNotNull null
            V3BatchSyncRequest(
                conversationId = conversationId,
                afterSeqId = (item["afterSeqId"] as? Number)?.toLong() ?: 0L,
            )
        }

        v3MessageSyncPort.batchSyncMessages(
            ctx = ctx,
            userId = userId,
            requests = requests,
            responseSink = V3MessageSyncResponseSink { payload ->
                ctx.writeAndFlush(TextWebSocketFrame(objectMapper.writeValueAsString(payload)))
            },
        )
    }

    private companion object {
        const val MAX_CONVERSATIONS = 20
    }
}
