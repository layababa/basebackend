package com.layababateam.xinxiwang_backend.consumer

import tools.jackson.databind.json.JsonMapper
import com.layababateam.xinxiwang_backend.service.RealtimeEventDispatchPort
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 好友事件消费壳。
 *
 * SDK 负责队列监听和 payload 契约解析；业务侧 [RealtimeEventDispatchPort] 保留实际在线推送实现。
 */
@Component
class FriendEventConsumer(
    private val realtimeEventDispatchPort: RealtimeEventDispatchPort,
    private val objectMapper: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(FriendEventConsumer::class.java)

    @RabbitListener(queues = ["#{@rabbitNames.friendEventQueueName}"])
    fun onFriendEvent(payload: Map<String, Any?>) {
        try {
            val event = parseTargetEvent(payload) ?: return
            realtimeEventDispatchPort.pushToUsers(event.type, event.targetIds, event.payloadJson)
            log.debug("Friend event '{}' pushed to {} users", event.type, event.targetIds.size)
        } catch (e: Exception) {
            log.warn("Failed to process friend event: {}", e.message)
        }
    }

    private fun parseTargetEvent(payload: Map<String, Any?>): TargetEvent? {
        val type = payload["type"] as? String ?: return null
        val targetIds = (payload["targetUserIds"] as? List<*>)?.filterIsInstance<String>() ?: return null
        val data = payload["data"] ?: return null
        val payloadJson = objectMapper.writeValueAsString(mapOf("type" to type, "data" to data))
        return TargetEvent(type, targetIds, payloadJson)
    }

    private data class TargetEvent(
        val type: String,
        val targetIds: List<String>,
        val payloadJson: String,
    )
}
