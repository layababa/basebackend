package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.netty.ChannelDeviceResolver
import com.layababateam.xinxiwang_backend.netty.WsResponseSender
import com.layababateam.xinxiwang_backend.service.ConversationSyncPort
import io.netty.channel.ChannelHandlerContext
import org.springframework.stereotype.Component

@Component
class SyncRequestHandler(
    private val conversationSyncPort: ConversationSyncPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "sync"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String
            ?: throw IllegalArgumentException("会话ID不能为空")
        val afterSeqId = (data["afterSeqId"] as? Number)?.toLong() ?: 0L
        val messages = conversationSyncPort.syncMessages(userId, conversationId, afterSeqId)
        wsResponseSender.send(
            ctx,
            mapOf("type" to "sync_response", "conversationId" to conversationId, "data" to messages),
        )
    }
}

@Component
class ConversationListHandler(
    private val conversationSyncPort: ConversationSyncPort,
    private val wsResponseSender: WsResponseSender,
    private val channelDeviceResolver: ChannelDeviceResolver,
) : MessageHandler {
    override val type: String = "conversation_list"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val afterTimestamp = (data["afterTimestamp"] as? Number)?.toLong()
        val beforeTimestamp = (data["beforeTimestamp"] as? Number)?.toLong()
        val limit = (data["limit"] as? Number)?.toInt()?.coerceIn(1, CONVERSATION_LIST_LIMIT_MAX)
        val result = conversationSyncPort.getConversationList(
            userId = userId,
            afterTimestamp = afterTimestamp,
            beforeTimestamp = beforeTimestamp,
            limit = limit,
            deviceId = channelDeviceResolver.getDeviceId(ctx.channel()),
        )
        val response = buildMap {
            put("type", "conversation_list_response")
            put("data", result.data)
            if (result.hasMore != null) {
                put("hasMore", result.hasMore)
            }
        }
        wsResponseSender.send(ctx, response)
    }

    private companion object {
        const val CONVERSATION_LIST_LIMIT_MAX = 50
    }
}

@Component
class GetHistoryHandler(
    private val conversationSyncPort: ConversationSyncPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "get_history"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String
            ?: throw IllegalArgumentException("会话ID不能为空")
        val beforeSeqId = (data["beforeSeqId"] as? Number)?.toLong()
        val limit = (data["limit"] as? Number)?.toInt()?.coerceIn(1, HISTORY_LIMIT_MAX) ?: HISTORY_LIMIT_DEFAULT
        val messages = conversationSyncPort.getHistory(userId, conversationId, beforeSeqId, limit)
        wsResponseSender.send(
            ctx,
            mapOf("type" to "history_response", "conversationId" to conversationId, "data" to messages),
        )
    }

    private companion object {
        const val HISTORY_LIMIT_DEFAULT = 30
        const val HISTORY_LIMIT_MAX = 100
    }
}

/**
 * 首屏“最新 N 条”接口：用于长离线重进聊天时只拉最新 K 条，避免 sync 循环补齐几万条消息。
 */
@Component
class GetRecentHistoryHandler(
    private val conversationSyncPort: ConversationSyncPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "get_recent_history"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String
            ?: throw IllegalArgumentException("会话ID不能为空")
        val limit = (data["limit"] as? Number)?.toInt()?.coerceIn(1, RECENT_HISTORY_LIMIT_MAX)
            ?: RECENT_HISTORY_LIMIT_DEFAULT
        val messages = conversationSyncPort.getRecentHistory(userId, conversationId, limit)
        wsResponseSender.send(
            ctx,
            mapOf("type" to "recent_history_response", "conversationId" to conversationId, "data" to messages),
        )
    }

    private companion object {
        const val RECENT_HISTORY_LIMIT_DEFAULT = 100
        const val RECENT_HISTORY_LIMIT_MAX = 100
    }
}

@Component
class BatchGetHistoryHandler(
    private val conversationSyncPort: ConversationSyncPort,
    private val wsResponseSender: WsResponseSender,
) : MessageHandler {
    override val type: String = "batch_get_history"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationIds = (data["conversationIds"] as? List<*>)?.filterIsInstance<String>()
        if (conversationIds.isNullOrEmpty()) {
            throw IllegalArgumentException("会话ID列表不能为空")
        }
        if (conversationIds.size > MAX_BATCH_SIZE) {
            throw IllegalArgumentException("批次查询上限为 ${MAX_BATCH_SIZE} 个会话，当前请求 ${conversationIds.size} 个")
        }
        val limit = (data["limit"] as? Number)?.toInt()?.coerceIn(1, BATCH_HISTORY_LIMIT_MAX)
            ?: BATCH_HISTORY_LIMIT_DEFAULT
        val result = conversationSyncPort.batchGetHistory(userId, conversationIds, limit)
        wsResponseSender.send(ctx, mapOf("type" to "batch_history_response", "data" to result))
    }

    private companion object {
        const val MAX_BATCH_SIZE = 20
        const val BATCH_HISTORY_LIMIT_DEFAULT = 30
        const val BATCH_HISTORY_LIMIT_MAX = 50
    }
}

@Component
class SetConversationPinHandler(
    private val conversationSyncPort: ConversationSyncPort,
) : MessageHandler {
    override val type: String = "set_conversation_pin"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String
            ?: throw IllegalArgumentException("会话ID不能为空")
        val pinned = data["pinned"] as? Boolean
            ?: throw IllegalArgumentException("置顶参数不能为空")
        conversationSyncPort.setConversationPinned(userId, conversationId, pinned)
    }
}

@Component
class SetConversationMuteHandler(
    private val conversationSyncPort: ConversationSyncPort,
) : MessageHandler {
    override val type: String = "set_conversation_mute"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String
            ?: throw IllegalArgumentException("会话ID不能为空")
        val muted = data["muted"] as? Boolean
            ?: throw IllegalArgumentException("静音参数不能为空")
        conversationSyncPort.setConversationMuted(userId, conversationId, muted)
    }
}

@Component
class SetConversationPeerRemarkHandler(
    private val conversationSyncPort: ConversationSyncPort,
) : MessageHandler {
    override val type: String = "set_conversation_peer_remark"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val conversationId = data["conversationId"] as? String
            ?: throw IllegalArgumentException("会话ID不能为空")
        val remark = data["remark"] as? String
        conversationSyncPort.setConversationPeerRemark(userId, conversationId, remark)
    }
}

@Component
class BatchUpdateReadPointsHandler(
    private val conversationSyncPort: ConversationSyncPort,
    private val channelDeviceResolver: ChannelDeviceResolver,
) : MessageHandler {
    override val type: String = "batch_update_read_points"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val points = (data["points"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
        if (points.isNullOrEmpty()) {
            return
        }
        val fallbackDeviceId = (data["deviceId"] as? String)?.takeIf { it.isNotEmpty() }
            ?: channelDeviceResolver.getDeviceId(ctx.channel())
        conversationSyncPort.batchUpdateReadPoints(userId, points, fallbackDeviceId)
    }
}
