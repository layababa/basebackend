package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.DeviceReadPoint
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface DeviceReadPointRepository : MongoRepository<DeviceReadPoint, String> {
    fun findFirstByUserIdAndDeviceIdAndConversationId(
        userId: String,
        deviceId: String,
        conversationId: String
    ): DeviceReadPoint?

    fun findByUserIdAndDeviceIdAndConversationIdIn(
        userId: String,
        deviceId: String,
        conversationIds: List<String>
    ): List<DeviceReadPoint>

    fun deleteByUserIdAndConversationId(userId: String, conversationId: String)
    fun deleteByUserIdInAndConversationId(userIds: List<String>, conversationId: String)
    fun deleteByConversationId(conversationId: String)
    fun deleteByUserId(userId: String)
}
