package com.layababateam.xinxiwang_backend.service

data class ConversationListResult(
    val data: Any?,
    val hasMore: Boolean? = null,
)

interface ConversationSyncPort {
    fun syncMessages(userId: String, conversationId: String, afterSeqId: Long): Any?

    fun getConversationList(
        userId: String,
        afterTimestamp: Long?,
        beforeTimestamp: Long?,
        limit: Int?,
        deviceId: String?,
    ): ConversationListResult

    fun getHistory(userId: String, conversationId: String, beforeSeqId: Long?, limit: Int): Any?

    fun getRecentHistory(userId: String, conversationId: String, limit: Int): Any?

    fun batchGetHistory(userId: String, conversationIds: List<String>, limit: Int): Any?

    fun setConversationPinned(userId: String, conversationId: String, pinned: Boolean)

    fun setConversationMuted(userId: String, conversationId: String, muted: Boolean)

    fun setConversationPeerRemark(userId: String, conversationId: String, remark: String?)

    fun batchUpdateReadPoints(
        userId: String,
        points: List<Map<String, Any?>>,
        fallbackDeviceId: String?,
    )
}
