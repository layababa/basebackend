package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.BroadcastConsumerPort
import com.layababateam.xinxiwang_backend.service.BroadcastMessageEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 全员广播消费壳。
 *
 * SDK 负责队列监听和 payload 解析；业务侧 [BroadcastConsumerPort] 保留用户分页和官方消息投递逻辑。
 */
@Component
class BroadcastConsumer(
    private val broadcastConsumerPort: BroadcastConsumerPort,
) {
    private val log = LoggerFactory.getLogger(BroadcastConsumer::class.java)

    @RabbitListener(queues = ["#{@rabbitNames.broadcastQueue}"])
    fun handleBroadcast(payload: Map<String, Any?>) {
        try {
            val message = payload["message"] as? String ?: return
            val adminId = payload["adminId"] as? String ?: "SYSTEM"
            val broadcastId = payload["broadcastId"] as? String ?: "unknown"
            broadcastConsumerPort.handleBroadcast(BroadcastMessageEvent(message, adminId, broadcastId))
        } catch (e: Exception) {
            log.error("Failed to process broadcast: {}", e.message, e)
            throw e
        }
    }
}
