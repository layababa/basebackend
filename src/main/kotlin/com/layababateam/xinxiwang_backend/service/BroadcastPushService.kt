package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.repository.BroadcastParticipantRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 宣讲大会 WebSocket 推送辅助。
 *
 * 统一宣讲场景的 WS payload 格式，并通过业务侧适配的 [BroadcastUserPushPort]
 * 下发到在线用户。
 */
@Service
class BroadcastPushService(
    private val userPushPort: BroadcastUserPushPort,
    private val participantRepository: BroadcastParticipantRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(BroadcastPushService::class.java)

    fun pushToUser(userId: String, broadcastId: String, type: String, data: Any) {
        val payload = serialize(type, broadcastId, data)
        userPushPort.pushToUser(userId, payload, skipApns = true)
    }

    fun pushToUsers(userIds: Collection<String>, broadcastId: String, type: String, data: Any) {
        val payload = serialize(type, broadcastId, data)
        userIds.toSet().forEach { uid ->
            try {
                userPushPort.pushToUser(uid, payload, skipApns = true)
            } catch (e: Exception) {
                log.warn("broadcast push failed: type={} user={} err={}", type, uid, e.message)
            }
        }
    }

    fun pushToRoom(broadcastId: String, type: String, data: Any) {
        val userIds = participantRepository.findByBroadcastIdAndLeftAtIsNull(broadcastId)
            .mapNotNull { it.userId }
        pushToUsers(userIds, broadcastId, type, data)
    }

    fun pushToAdmins(broadcastId: String, adminIds: Collection<String>, type: String, data: Any) {
        pushToUsers(adminIds, broadcastId, type, data)
    }

    private fun serialize(type: String, broadcastId: String, data: Any): String =
        objectMapper.writeValueAsString(
            mapOf(
                "type" to type,
                "broadcastId" to broadcastId,
                "data" to data,
            )
        )
}
