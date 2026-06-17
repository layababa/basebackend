package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.MessageAckPort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class MessageAckHandler(
    private val messageAckPort: MessageAckPort,
) : MessageHandler {
    override val type = "message_ack"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val seqId = (data["seqId"] as? Number)?.toLong() ?: return
        if (seqId <= 0) return
        messageAckPort.confirmAck(userId, seqId)
    }
}

@Component
class BatchAckHandler(
    private val messageAckPort: MessageAckPort,
) : MessageHandler {
    override val type = "batch_ack"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        ackSeqIdsFrom(data).forEach { seqId ->
            messageAckPort.confirmAck(userId, seqId)
        }
    }

    private fun ackSeqIdsFrom(data: Map<String, Any?>): List<Long> {
        val raw = data["acks"] as? List<*> ?: return emptyList()
        val seen = LinkedHashSet<Long>()
        for (item in raw) {
            val ack = item as? Map<*, *> ?: continue
            val seqId = (ack["seqId"] as? Number)?.toLong() ?: continue
            if (seqId <= 0) continue
            seen.add(seqId)
            if (seen.size >= MAX_ACKS_PER_BATCH) break
        }
        return seen.toList()
    }

    private companion object {
        const val MAX_ACKS_PER_BATCH = 200
    }
}
