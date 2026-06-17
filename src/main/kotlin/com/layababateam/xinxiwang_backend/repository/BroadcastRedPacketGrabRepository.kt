package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.BroadcastRedPacketGrab
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface BroadcastRedPacketGrabRepository : MongoRepository<BroadcastRedPacketGrab, String> {
    fun findByRedPacketIdAndUserId(redPacketId: String, userId: String): BroadcastRedPacketGrab?
    fun findByRedPacketIdOrderByGrabbedAtAsc(redPacketId: String): List<BroadcastRedPacketGrab>
    fun countByRedPacketId(redPacketId: String): Long
}
