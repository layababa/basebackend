package com.layababateam.xinxiwang_backend.handler.v3

import tools.jackson.databind.json.JsonMapper
import com.layababateam.xinxiwang_backend.handler.MessageHandler
import com.layababateam.xinxiwang_backend.service.PaginationRules
import com.layababateam.xinxiwang_backend.service.V3MessageSyncPort
import com.layababateam.xinxiwang_backend.service.V3MessageSyncResponseSink
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.springframework.stereotype.Component

@Component
class V3SyncHandler(
    private val v3MessageSyncPort: V3MessageSyncPort,
    private val objectMapper: JsonMapper,
) : MessageHandler {
    override val type = "v3_sync"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String
            ?: throw IllegalArgumentException("会话ID不能为空")
        val afterSeqId = (data["afterSeqId"] as? Number)?.toLong() ?: 0L
        val limit = (data["limit"] as? Number)?.toInt()?.let {
            PaginationRules.pageSize(it, MAX_PAGE_SIZE)
        } ?: DEFAULT_PAGE_SIZE

        v3MessageSyncPort.syncMessages(
            ctx = ctx,
            userId = userId,
            conversationId = conversationId,
            afterSeqId = afterSeqId,
            limit = limit,
            hasRequestId = (data["requestId"] as? String)?.isNotBlank() == true,
            responseSink = sink(ctx),
        )
    }

    private fun sink(ctx: ChannelHandlerContext): V3MessageSyncResponseSink =
        V3MessageSyncResponseSink { payload ->
            ctx.writeAndFlush(TextWebSocketFrame(objectMapper.writeValueAsString(payload)))
        }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 100
        const val MAX_PAGE_SIZE = 100
    }
}
