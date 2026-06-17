package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ConversationDto

interface V3ConversationPort {
    fun getConversationListPaginated(
        userId: String,
        limit: Int,
        beforeTimestamp: Long?,
        requesterDeviceId: String?,
    ): Pair<List<ConversationDto>, Boolean>

    fun getConversationListAfter(
        userId: String,
        afterTimestamp: Long,
        requesterDeviceId: String?,
    ): List<ConversationDto>
}
