package com.layababateam.xinxiwang_backend.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.service.RealtimeEventDispatchPort
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 群事件消费壳。
 *
 * SDK 负责队列监听和 payload 契约解析；业务侧 [RealtimeEventDispatchPort] 保留在线推送与批量推送策略。
 */
@Component
class GroupEventConsumer(
    private val realtimeEventDispatchPort: RealtimeEventDispatchPort,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(GroupEventConsumer::class.java)

    @RabbitListener(queues = ["#{@rabbitNames.groupEventQueueName}"])
    fun onGroupEvent(payload: Map<String, Any?>) {
        try {
            val type = payload["type"] as? String ?: return
            val memberIds = (payload["memberIds"] as? List<*>)?.filterIsInstance<String>() ?: return
            val data = payload["data"] ?: return
            val payloadJson = objectMapper.writeValueAsString(mapOf("type" to type, "data" to data))

            realtimeEventDispatchPort.pushToGroupMembers(type, memberIds, payloadJson)
            log.debug("Group event '{}' pushed to {} members", type, memberIds.size)
        } catch (e: Exception) {
            log.warn("Failed to process group event: {}", e.message)
        }
    }
}
