package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.RedPacket
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface RedPacketRepository : MongoRepository<RedPacket, String> {
    fun findByConversationIdOrderByCreatedAtDesc(conversationId: String): List<RedPacket>
    fun findByExpiredAtLessThanAndRefundedFalseAndRemainingCountGreaterThan(expiredAt: Long, remainingCount: Int): List<RedPacket>
    fun findByRefundedFalseAndRemainingCountGreaterThanOrderByCreatedAtDesc(
        remainingCount: Int,
        pageable: Pageable
    ): Page<RedPacket>
}
