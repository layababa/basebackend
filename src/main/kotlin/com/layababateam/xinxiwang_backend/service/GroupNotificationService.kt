package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GroupNotificationService(
    private val messagePort: GroupSystemMessagePort,
) {
    private val log = LoggerFactory.getLogger(GroupNotificationService::class.java)

    fun sendSystemMessage(operatorId: String, conversationId: String, content: String) {
        try {
            messagePort.sendGroupSystemMessage(
                senderId = operatorId,
                conversationId = conversationId,
                content = content,
                contentType = 6,
            )
        } catch (e: Exception) {
            log.warn("Failed to send group system notification in conversation {}: {}", conversationId, e.message)
        }
    }
}
