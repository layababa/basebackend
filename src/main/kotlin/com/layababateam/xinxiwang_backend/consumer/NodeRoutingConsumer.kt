package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.CrossNodeMessagePort
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 节点路由消费壳。
 *
 * SDK 负责监听当前节点队列；业务侧 [CrossNodeMessagePort] 保留 WebSocket 会话、断连和群消息本地投递细节。
 */
@Component
class NodeRoutingConsumer(
    private val crossNodeMessagePort: CrossNodeMessagePort,
) {
    @RabbitListener(queues = ["#{@rabbitNames.nodeQueueName}"])
    fun onCrossNodeMessage(payload: Map<String, Any?>) {
        crossNodeMessagePort.handleCrossNodeMessage(payload)
    }
}
