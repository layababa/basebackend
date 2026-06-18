package com.layababateam.xinxiwang_backend.service

interface MessageRecallConsumerPort {
    fun recallMessage(event: MessageRecallEvent)
}

data class MessageRecallEvent(
    val messageId: String,
)
