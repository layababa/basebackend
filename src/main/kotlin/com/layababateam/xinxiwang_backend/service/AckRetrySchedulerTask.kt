package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * ACK 重试维护定时壳。
 *
 * SDK 负责触发节奏；业务侧 [AckRetrySchedulerPort] 保留 Redis 扫描、TTL 修复和消息重推细节。
 */
@Component
class AckRetrySchedulerTask(
    private val ackRetrySchedulerPort: AckRetrySchedulerPort,
) {
    private val log = LoggerFactory.getLogger(AckRetrySchedulerTask::class.java)

    @Scheduled(fixedDelay = 30_000L)
    fun scanPendingAcks() {
        runTask("scanPendingAcks") {
            ackRetrySchedulerPort.scanPendingAcks()
        }
    }

    @Scheduled(fixedDelay = 600_000L, initialDelay = 45_000L)
    fun cleanupRetryKeysWithoutTtl() {
        runTask("cleanupRetryKeysWithoutTtl") {
            ackRetrySchedulerPort.cleanupRetryKeysWithoutTtl()
        }
    }

    private inline fun runTask(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn("{} error: {}", name, e.message)
        }
    }
}
