package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 节点心跳定时壳。
 *
 * SDK 负责固定节奏触发；业务侧 [NodeHeartbeatPort] 保留具体注册中心/Redis 写入方式。
 */
@Component
class NodeHeartbeatSchedulerTask(
    private val nodeHeartbeatPort: NodeHeartbeatPort,
) {
    private val log = LoggerFactory.getLogger(NodeHeartbeatSchedulerTask::class.java)

    @Scheduled(fixedRate = 15_000L)
    fun heartbeat() {
        try {
            nodeHeartbeatPort.heartbeat()
        } catch (e: Exception) {
            log.warn("node heartbeat error: {}", e.message)
        }
    }
}
