package com.layababateam.xinxiwang_backend.service

interface MessageDeleteConsumerPort {
    fun deleteMessage(event: MessageDeleteEvent)
}

data class MessageDeleteEvent(
    val messageId: String,
    val forAll: Boolean,
    val userId: String,
)
