package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.MessageDeleteConsumerPort
import com.layababateam.xinxiwang_backend.service.MessageDeleteEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 消息删除消费壳。
 *
 * SDK 负责队列监听和 payload 解析；业务侧 [MessageDeleteConsumerPort] 保留消息存储更新逻辑。
 */
@Component
class MessageDeleteConsumer(
    private val messageDeleteConsumerPort: MessageDeleteConsumerPort,
) {
    private val log = LoggerFactory.getLogger(MessageDeleteConsumer::class.java)

    @RabbitListener(queues = ["#{@rabbitNames.messageDeleteQueue}"])
    fun onMessage(payload: Map<String, Any?>) {
        try {
            val event = parseEvent(payload) ?: return
            messageDeleteConsumerPort.deleteMessage(event)
        } catch (e: Exception) {
            log.error("Failed to delete message: {}", e.message, e)
            throw e
        }
    }

    private fun parseEvent(payload: Map<String, Any?>): MessageDeleteEvent? {
        val messageId = payload["messageId"] as? String ?: return null
        val userId = payload["userId"] as? String ?: return null
        val forAll = payload["forAll"] as? Boolean ?: false
        return MessageDeleteEvent(messageId = messageId, forAll = forAll, userId = userId)
    }
}
