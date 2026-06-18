package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.MomentLikeConsumerPort
import com.layababateam.xinxiwang_backend.service.MomentLikeEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 动态点赞消费壳。
 *
 * SDK 负责队列监听和 payload 解析；业务侧 [MomentLikeConsumerPort] 保留点赞落库规则。
 */
@Component
class MomentLikeConsumer(
    private val momentLikeConsumerPort: MomentLikeConsumerPort,
) {
    private val log = LoggerFactory.getLogger(MomentLikeConsumer::class.java)

    @RabbitListener(queues = ["#{@rabbitNames.momentLikeQueue}"])
    fun onMessage(payload: Map<String, Any?>) {
        try {
            val event = parseEvent(payload) ?: return
            momentLikeConsumerPort.persistMomentLike(event)
        } catch (e: Exception) {
            log.error("Failed to process moment like: {}", e.message, e)
            throw e
        }
    }

    private fun parseEvent(payload: Map<String, Any?>): MomentLikeEvent? {
        val action = payload["action"] as? String ?: return null
        val momentId = payload["momentId"] as? String ?: return null
        val userId = payload["userId"] as? String ?: return null
        return MomentLikeEvent(action = action, momentId = momentId, userId = userId)
    }
}
