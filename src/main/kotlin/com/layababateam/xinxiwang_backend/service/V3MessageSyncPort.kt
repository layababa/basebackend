package com.layababateam.xinxiwang_backend.service

import io.netty.channel.ChannelHandlerContext

interface V3MessageSyncPort {
    fun syncMessages(
        ctx: ChannelHandlerContext,
        userId: String,
        conversationId: String,
        afterSeqId: Long,
        limit: Int,
        hasRequestId: Boolean,
        responseSink: V3MessageSyncResponseSink,
    )

    fun batchSyncMessages(
        ctx: ChannelHandlerContext,
        userId: String,
        requests: List<V3BatchSyncRequest>,
        responseSink: V3MessageSyncResponseSink,
    )

    fun queryMessages(
        ctx: ChannelHandlerContext,
        userId: String,
        request: V3QueryMessagesRequest,
        responseSink: V3MessageSyncResponseSink,
    )
}

fun interface V3MessageSyncResponseSink {
    fun send(data: Map<String, Any?>)
}

data class V3BatchSyncRequest(
    val conversationId: String,
    val afterSeqId: Long,
)

data class V3QueryMessagesRequest(
    val conversationId: String,
    val afterSeqId: Long?,
    val beforeSeqId: Long?,
    val deliveryDateStart: Long?,
    val deliveryDateEnd: Long?,
    val maxCount: Int,
    val descending: Boolean,
    val contentTypes: List<Int>?,
    val withTotal: Boolean,
    val hasRequestId: Boolean,
)
