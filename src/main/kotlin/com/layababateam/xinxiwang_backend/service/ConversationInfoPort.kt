package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ConversationDto

interface ConversationInfoPort {
    fun getConversationInfo(
        userId: String,
        conversationId: String,
        requesterDeviceId: String?,
    ): ConversationDto?
}
