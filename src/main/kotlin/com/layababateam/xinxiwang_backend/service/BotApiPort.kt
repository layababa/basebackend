package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ConversationDto

object BotApiAuthAttributes {
    const val BOT_USER_ID_ATTR = "botUserId"
}

interface BotApiCredentialResolver {
    fun resolveBotUserId(apiKey: String): String?
}

data class BotApiProfile(
    val id: String?,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val description: String,
    val status: Int,
)

data class BotMessageSendResult(
    val messageId: String?,
    val conversationId: String,
    val seqId: Long,
)

interface BotApiPort {
    fun getBotProfile(userId: String): BotApiProfile?

    fun sendBotMessage(
        userId: String,
        conversationId: String,
        content: String,
        contentType: Int,
    ): BotMessageSendResult

    fun getBotConversations(userId: String): List<ConversationDto>
}
