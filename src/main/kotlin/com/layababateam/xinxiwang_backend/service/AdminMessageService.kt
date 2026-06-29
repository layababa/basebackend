package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.repository.MessageRepository
import com.layababateam.xinxiwang_backend.service.cache.ConversationCacheService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import com.layababateam.xinxiwang_backend.model.ConversationType
import org.springframework.stereotype.Service

@Service
class AdminMessageService(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val conversationCacheService: ConversationCacheService,
    private val userSessionManager: UserSessionManager,
    private val messageBatchService: MessageBatchService,
    private val rabbitTemplate: RabbitTemplate,
    private val rabbitPublishService: RabbitPublishService,
    private val broadcastService: BroadcastService,
    private val officialNotificationService: OfficialNotificationService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(AdminMessageService::class.java)

    /**
     * 管理员撤回消息：标记 isRecalled=true 并推送撤回通知给会话参与者
     */
    fun recallMessage(messageId: String) {
        val msg = messageRepository.findById(messageId).orElse(null)
            ?: throw IllegalArgumentException("消息不存在")

        // 通过 RabbitMQ 异步持久化撤回状态
        rabbitPublishService.send(
            RabbitMQConfig.MESSAGE_RECALL_QUEUE,
            mapOf("messageId" to messageId),
            "admin_message_recall messageId=$messageId",
        )

        // 即时推送撤回通知给会话中的所有成员
        val conversation = conversationCacheService.getConversation(msg.conversationId)
        if (conversation != null) {
            val payload = objectMapper.writeValueAsString(mapOf(
                "type" to "message_recalled",
                "data" to mapOf(
                    "messageId" to messageId,
                    "conversationId" to msg.conversationId,
                    "seqId" to msg.seqId
                )
            ))
            conversation.members.forEach { memberId ->
                if (conversation.type == ConversationType.GROUP.value) {
                    messageBatchService.pushBatched(memberId, payload)
                } else {
                    userSessionManager.pushToUser(memberId, payload, messageType = "message_recalled")
                }
            }
        }

        log.info("Admin recalled message {}", messageId)
    }

    /**
     * 管理员删除消息：对所有人删除并推送删除通知
     */
    fun deleteMessage(messageId: String) {
        val msg = messageRepository.findById(messageId).orElse(null)
            ?: throw IllegalArgumentException("消息不存在")

        // 通过 RabbitMQ 异步持久化删除（对所有人删除）
        rabbitPublishService.send(
            RabbitMQConfig.MESSAGE_DELETE_QUEUE,
            mapOf(
                "messageId" to messageId,
                "forAll" to true,
                "userId" to "ADMIN",
            ),
            "admin_message_delete messageId=$messageId",
        )

        // 即时推送删除通知给会话中的所有成员
        val conversation = conversationCacheService.getConversation(msg.conversationId)
        if (conversation != null) {
            val payload = objectMapper.writeValueAsString(mapOf(
                "type" to "message_deleted",
                "data" to mapOf(
                    "messageId" to messageId,
                    "conversationId" to msg.conversationId,
                    "seqId" to msg.seqId
                )
            ))
            conversation.members.forEach { memberId ->
                if (conversation.type == ConversationType.GROUP.value) {
                    messageBatchService.pushBatched(memberId, payload)
                } else {
                    userSessionManager.pushToUser(memberId, payload, messageType = "message_deleted")
                }
            }
        }

        log.info("Admin deleted message {}", messageId)
    }

    /**
     * 广播消息：通过 RabbitMQ 异步发送给所有用户
     */
    fun broadcast(content: String, contentType: Int, adminId: String) {
        if (content.isBlank()) {
            throw IllegalArgumentException("消息内容不能为空")
        }
        // 使用现有 BroadcastService 通过 RabbitMQ 异步处理
        broadcastService.broadcast(content, adminId)
        log.info("Admin {} initiated broadcast, contentType={}", adminId, contentType)
    }

    /**
     * 组播消息：发送给指定用户列表
     */
    fun multicast(userIds: List<String>, content: String, contentType: Int, adminId: String) {
        if (userIds.isEmpty()) {
            throw IllegalArgumentException("用户列表不能为空")
        }
        if (content.isBlank()) {
            throw IllegalArgumentException("消息内容不能为空")
        }

        var successCount = 0
        var failCount = 0

        for (userId in userIds) {
            try {
                officialNotificationService.sendOfficialMessage(userId, content)
                successCount++
            } catch (e: Exception) {
                failCount++
                log.error("Failed to multicast to user {}: {}", userId, e.message)
            }
        }

        log.info(
            "Admin {} multicast complete: {} success, {} failed, total {}",
            adminId, successCount, failCount, userIds.size
        )
    }
}
