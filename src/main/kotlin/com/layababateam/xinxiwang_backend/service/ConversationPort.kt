package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ConversationDto
import com.layababateam.xinxiwang_backend.dto.MessageDto

/**
 * 会话 HTTP 能力端口。
 *
 * SDK 复用会话列表、历史消息、媒体消息和本地删除语义；
 * 消息读取、会话隐藏水位线和好友关系判断由接入方实现。
 */
interface ConversationPort {
    fun getConversationList(userId: String): List<ConversationDto>

    fun getHistory(userId: String, conversationId: String, beforeSeqId: Long?, limit: Int): List<MessageDto>

    fun getMediaMessages(userId: String, conversationId: String, contentTypes: List<Int>, limit: Int): List<MessageDto>

    fun updateReadPoint(userId: String, conversationId: String, seqId: Long)

    fun getUnreadCount(userId: String, conversationId: String): Long

    fun deleteConversation(userId: String, conversationId: String): ConversationDeleteResult

    fun clearHistory(userId: String, conversationId: String): ConversationDeleteResult
}

data class ConversationDeleteResult(
    val isFriend: Boolean,
    val hiddenBeforeSeqId: Long,
    val deleted: Boolean,
)
