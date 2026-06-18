package com.layababateam.xinxiwang_backend.service

interface MessagePersistConsumerPort {
    fun persistMessage(event: MessagePersistEvent)
}

data class MessagePersistEvent(
    val messageId: String?,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val contentType: Int,
    val seqId: Long,
    val createdAt: Long,
    val mentions: List<String>,
    val replyToMessageId: String?,
    val isGroupChat: Boolean,
)
