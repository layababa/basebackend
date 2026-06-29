package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.repository.DeviceSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OfficialNotificationService(
    private val systemUserService: SystemUserService,
    private val conversationRepository: ConversationRepository,
    private val messageService: MessageService,
    private val apnsPushService: ApnsPushService,
    private val deviceSessionRepository: DeviceSessionRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(OfficialNotificationService::class.java)

    /**
     * Send a notification to a user via the official account conversation.
     * APNs push is already handled internally by messageService.sendMessage().
     */
    fun notifyUser(userId: String, content: String) {
        sendOfficialMessage(userId, content)
    }

    /**
     * Send a message via the official account conversation (type=2).
     */
    fun sendOfficialMessage(userId: String, content: String) {
        try {
            val sysId = systemUserService.getOfficialUserId()
            val convs = conversationRepository.findByMembersContainingAndType(userId, 2)
            val conv = convs.find { sysId in it.members }
            if (conv == null) {
                log.warn("No official conversation found for userId={}", userId)
                return
            }
            messageService.sendMessage(
                senderId = sysId,
                conversationId = conv.id!!,
                content = content,
                contentType = 0
            )
        } catch (e: Exception) {
            log.error("Failed to send official notification to userId={}: {}", userId, e.message, e)
        }
    }

    /**
     * Send APNs push notification to all offline devices of a user.
     */
    fun sendApnsPush(userId: String, title: String, body: String) {
        try {
            val wsPayload = objectMapper.writeValueAsString(mapOf(
                "type" to "new_message",
                "data" to mapOf(
                    "senderName" to title,
                    "contentType" to 0,
                    "content" to body
                )
            ))
            apnsPushService.pushOfflineNotification(userId, wsPayload)
        } catch (e: Exception) {
            log.error("Failed to send APNs push to userId={}: {}", userId, e.message, e)
        }
    }

    /**
     * Send APNs push directly to all devices of a user (bypassing online check).
     */
    fun sendDirectApnsPush(userId: String, title: String, body: String) {
        try {
            val sessions = deviceSessionRepository.findByUserIdAndApnsTokenNotNull(userId)
            for (session in sessions) {
                val token = session.apnsToken ?: continue
                apnsPushService.sendPush(token, title, body, mapOf("type" to "system_notification"))
            }
        } catch (e: Exception) {
            log.error("Failed to send direct APNs push to userId={}: {}", userId, e.message, e)
        }
    }
}
