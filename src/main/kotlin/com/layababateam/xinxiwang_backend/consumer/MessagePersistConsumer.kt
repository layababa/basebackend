package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.MessagePersistConsumerPort
import com.layababateam.xinxiwang_backend.service.MessagePersistEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 消息持久化消费壳。
 *
 * SDK 负责队列监听和 payload 契约解析；业务侧 [MessagePersistConsumerPort] 保留消息、会话和缓存更新逻辑。
 */
@Component
class MessagePersistConsumer(
    private val messagePersistConsumerPort: MessagePersistConsumerPort,
) {
    private val log = LoggerFactory.getLogger(MessagePersistConsumer::class.java)

    @RabbitListener(queues = ["#{@rabbitNames.messagePersistQueue}"])
    fun onMessage(payload: Map<String, Any?>) {
        try {
            val event = parseEvent(payload) ?: return
            messagePersistConsumerPort.persistMessage(event)
        } catch (e: Exception) {
            log.error("Failed to persist message: {}", e.message, e)
            throw e
        }
    }

    private fun parseEvent(payload: Map<String, Any?>): MessagePersistEvent? {
        val conversationId = payload["conversationId"] as? String ?: return null
        val senderId = payload["senderId"] as? String ?: return null
        val seqId = (payload["seqId"] as? Number)?.toLong() ?: return null
        val mentions = (payload["mentions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return MessagePersistEvent(
            messageId = payload["id"] as? String,
            conversationId = conversationId,
            senderId = senderId,
            content = payload["content"] as? String ?: "",
            contentType = (payload["contentType"] as? Number)?.toInt() ?: 0,
            seqId = seqId,
            createdAt = (payload["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            mentions = mentions,
            replyToMessageId = payload["replyToMessageId"] as? String,
            isGroupChat = payload["isGroupChat"] == true,
        )
    }
}
