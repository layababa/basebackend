package com.layababateam.xinxiwang_backend.service

interface GroupSystemMessagePort {
    fun sendGroupSystemMessage(
        senderId: String,
        conversationId: String,
        content: String,
        contentType: Int,
    )
}
