package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.BroadcastParticipant
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface BroadcastParticipantRepository : MongoRepository<BroadcastParticipant, String> {
    fun findByBroadcastIdAndUserId(broadcastId: String, userId: String): BroadcastParticipant?
    fun findByBroadcastId(broadcastId: String): List<BroadcastParticipant>
    fun findByBroadcastId(broadcastId: String, pageable: Pageable): Page<BroadcastParticipant>
    fun findByUserId(userId: String): List<BroadcastParticipant>
    fun deleteByBroadcastIdAndUserId(broadcastId: String, userId: String): Long
    fun deleteByBroadcastId(broadcastId: String): Long
    fun countByBroadcastId(broadcastId: String): Long
    fun countByBroadcastIdAndIsOnMicTrue(broadcastId: String): Long
    fun findByBroadcastIdAndIsOnMicTrue(broadcastId: String): List<BroadcastParticipant>

    // 活跃参与者查询（leftAt == null 表示仍在房间内）
    fun findByBroadcastIdAndLeftAtIsNull(broadcastId: String): List<BroadcastParticipant>
    fun findByUserIdAndLeftAtIsNull(userId: String): List<BroadcastParticipant>
    fun countByBroadcastIdAndLeftAtIsNull(broadcastId: String): Long
    fun countByBroadcastIdAndIsOnMicTrueAndLeftAtIsNull(broadcastId: String): Long
    fun findByBroadcastIdAndIsOnMicTrueAndLeftAtIsNull(broadcastId: String): List<BroadcastParticipant>

    @Query("{ 'broadcastId': ?0, 'raiseHandAt': { '\$ne': null }, 'leftAt': null }")
    fun findActiveRaiseHandQueue(broadcastId: String): List<BroadcastParticipant>

    @Query("{ 'broadcastId': ?0, 'raiseHandAt': { '\$ne': null } }")
    fun findRaiseHandQueue(broadcastId: String): List<BroadcastParticipant>
}
