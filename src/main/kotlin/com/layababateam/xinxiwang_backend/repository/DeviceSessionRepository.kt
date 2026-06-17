package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.DeviceSession
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface DeviceSessionRepository : MongoRepository<DeviceSession, String> {
    fun findByUserId(userId: String): List<DeviceSession>
    fun findByToken(token: String): DeviceSession?
    fun deleteByToken(token: String)
    fun deleteByIdAndUserId(id: String, userId: String): Long
    fun deleteByUserId(userId: String)
    fun findByUserIdAndApnsTokenNotNull(userId: String): List<DeviceSession>
    fun findByApnsToken(apnsToken: String): List<DeviceSession>
    fun findByUserIdAndDeviceId(userId: String, deviceId: String): DeviceSession?
    fun findByUserIdAndVoipTokenNotNull(userId: String): List<DeviceSession>
    fun findByVoipToken(voipToken: String): List<DeviceSession>
}
