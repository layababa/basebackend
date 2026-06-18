package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.GroupMessageDispatchConsumerPort
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 群消息分发消费壳。
 *
 * SDK 负责监听所有群消息分发队列；业务侧 [GroupMessageDispatchConsumerPort] 保留本地/跨节点分发和 ACK 策略。
 */
@Component
class GroupMessageDispatchConsumer(
    private val groupMessageDispatchConsumerPort: GroupMessageDispatchConsumerPort,
) {
    private val log = LoggerFactory.getLogger(GroupMessageDispatchConsumer::class.java)

    @RabbitListener(
        queues = [
            "#{@rabbitNames.groupMessageDispatchQueue0}",
            "#{@rabbitNames.groupMessageDispatchQueue1}",
            "#{@rabbitNames.groupMessageDispatchQueue2}",
            "#{@rabbitNames.groupMessageDispatchQueue3}",
            "#{@rabbitNames.groupMessageDispatchQueue4}",
            "#{@rabbitNames.groupMessageDispatchQueue5}",
            "#{@rabbitNames.groupMessageDispatchQueue6}",
            "#{@rabbitNames.groupMessageDispatchQueue7}",
            "#{@rabbitNames.groupMessageDispatchQueue}",
        ],
        containerFactory = "highThroughputContainerFactory",
    )
    fun onGroupMessageDispatch(payload: Map<String, Any?>) {
        try {
            groupMessageDispatchConsumerPort.dispatchGroupMessage(payload)
        } catch (e: Exception) {
            log.error("Failed to dispatch group message: {}", e.message, e)
            throw e
        }
    }
}
