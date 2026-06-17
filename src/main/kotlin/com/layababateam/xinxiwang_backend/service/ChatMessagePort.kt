package com.layababateam.xinxiwang_backend.service

import io.netty.channel.Channel

data class ChatMessageCommand(
    val conversationId: String,
    val clientMessageId: String?,
    val content: String,
    val contentType: Int,
    val mentions: List<String>,
    val replyToMessageId: String?,
)

data class ChatMessageHandleResult(
    val response: Map<String, Any?>? = null,
)

interface ChatMessagePort {
    fun sendChatMessage(userId: String, command: ChatMessageCommand, sourceChannel: Channel): ChatMessageHandleResult
}
