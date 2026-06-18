package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.MessageRecallConsumerPort
import com.layababateam.xinxiwang_backend.service.MessageRecallEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 消息撤回消费壳。
 *
 * SDK 负责队列监听和 payload 解析；业务侧 [MessageRecallConsumerPort] 保留消息、会话和置顶状态更新逻辑。
 */
@Component
class MessageRecallConsumer(
    private val messageRecallConsumerPort: MessageRecallConsumerPort,
) {
    private val log = LoggerFactory.getLogger(MessageRecallConsumer::class.java)

    @RabbitListener(queues = ["#{@rabbitNames.messageRecallQueue}"])
    fun onMessage(payload: Map<String, Any?>) {
        try {
            val messageId = payload["messageId"] as? String ?: return
            messageRecallConsumerPort.recallMessage(MessageRecallEvent(messageId))
        } catch (e: Exception) {
            log.error("Failed to recall message: {}", e.message, e)
            throw e
        }
    }
}
