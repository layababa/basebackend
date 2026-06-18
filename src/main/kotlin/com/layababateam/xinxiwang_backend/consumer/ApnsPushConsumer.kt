package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.ApnsPushConsumerPort
import com.layababateam.xinxiwang_backend.service.ApnsPushEvent
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * APNs 推送消费壳。
 *
 * SDK 负责队列监听和必填字段解析；业务侧 [ApnsPushConsumerPort] 保留 APNs/VoIP/聚合推送策略。
 */
@Component
class ApnsPushConsumer(
    private val apnsPushConsumerPort: ApnsPushConsumerPort,
) {
    @RabbitListener(queues = ["#{@rabbitNames.apnsPushQueue}"])
    fun handleApnsPush(payload: Map<String, Any?>) {
        val userId = payload["userId"] as? String
            ?: throw IllegalArgumentException("Missing userId in APNs push payload")
        val wsMessage = payload["wsMessage"] as? String
            ?: throw IllegalArgumentException("Missing wsMessage in APNs push payload")
        val onlineAuthTokens = (payload["onlineAuthTokens"] as? List<*>)?.filterIsInstance<String>()?.toSet()
            ?: emptySet()

        apnsPushConsumerPort.handleApnsPush(ApnsPushEvent(userId, wsMessage, onlineAuthTokens))
    }
}
