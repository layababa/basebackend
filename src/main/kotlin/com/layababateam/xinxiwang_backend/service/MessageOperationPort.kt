package com.layababateam.xinxiwang_backend.service

import io.netty.channel.Channel

interface MessageOperationPort {
    fun recallMessage(userId: String, messageId: String)

    fun deleteMessage(userId: String, messageId: String, forAll: Boolean, channel: Channel)

    fun forwardMessage(userId: String, messageId: String, toConversationId: String)

    fun updateReadPoint(userId: String, conversationId: String, seqId: Long, deviceId: String?): Boolean

    fun getUserInfo(userId: String, targetUserId: String): Map<String, Any?>?
}
