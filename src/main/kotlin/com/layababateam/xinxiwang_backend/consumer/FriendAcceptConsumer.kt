package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.FriendAcceptConsumerPort
import com.layababateam.xinxiwang_backend.service.FriendAcceptEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 好友接受消费壳。
 *
 * SDK 负责队列监听和 payload 解析；业务侧 [FriendAcceptConsumerPort] 保留会话关系和缓存更新逻辑。
 */
@Component
class FriendAcceptConsumer(
    private val friendAcceptConsumerPort: FriendAcceptConsumerPort,
) {
    private val log = LoggerFactory.getLogger(FriendAcceptConsumer::class.java)

    @RabbitListener(queues = ["#{@rabbitNames.friendAcceptQueue}"])
    fun onMessage(payload: Map<String, Any?>) {
        try {
            val event = parseEvent(payload) ?: return
            friendAcceptConsumerPort.acceptFriend(event)
        } catch (e: Exception) {
            log.error("Failed to process friend accept: {}", e.message, e)
            throw e
        }
    }

    private fun parseEvent(payload: Map<String, Any?>): FriendAcceptEvent? {
        val fromUserId = payload["fromUserId"] as? String ?: return null
        val toUserId = payload["toUserId"] as? String ?: return null
        val conversationId = payload["conversationId"] as? String ?: return null
        return FriendAcceptEvent(fromUserId = fromUserId, toUserId = toUserId, conversationId = conversationId)
    }
}
