package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.ProfileUpdateConsumerPort
import com.layababateam.xinxiwang_backend.service.ProfileUpdateEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 用户资料更新消费壳。
 *
 * SDK 负责队列监听和 payload 解析；业务侧 [ProfileUpdateConsumerPort] 保留缓存失效、好友查询和推送逻辑。
 */
@Component
class ProfileUpdateConsumer(
    private val profileUpdateConsumerPort: ProfileUpdateConsumerPort,
) {
    private val log = LoggerFactory.getLogger(ProfileUpdateConsumer::class.java)

    @RabbitListener(queues = ["#{@rabbitNames.profileUpdateQueue}"])
    fun onMessage(payload: Map<String, Any?>) {
        try {
            val userId = payload["userId"] as? String ?: return
            profileUpdateConsumerPort.handleProfileUpdate(ProfileUpdateEvent(userId))
        } catch (e: Exception) {
            log.error("Failed to process profile update: {}", e.message, e)
            throw e
        }
    }
}
