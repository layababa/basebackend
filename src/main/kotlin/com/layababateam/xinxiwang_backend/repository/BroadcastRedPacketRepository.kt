package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.BroadcastRedPacket
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface BroadcastRedPacketRepository : MongoRepository<BroadcastRedPacket, String> {
    fun findByBroadcastIdAndStatus(broadcastId: String, status: String): List<BroadcastRedPacket>

    /** Admin 后台用：本场所有红包（含 active / empty / expired），不限 status。 */
    fun findByBroadcastIdOrderByCreatedAtDesc(broadcastId: String): List<BroadcastRedPacket>

    @Query("{ 'status': 'active', 'expiresAt': { '\$lte': ?0 } }")
    fun findExpired(now: Long): List<BroadcastRedPacket>
}
