package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 红包过期退款定时壳。
 *
 * SDK 只负责触发节奏；业务侧 [RedPacketRefundSchedulerPort] 保留资金、锁和通知逻辑。
 */
@Component
class RedPacketRefundSchedulerTask(
    private val redPacketRefundSchedulerPort: RedPacketRefundSchedulerPort,
) {
    private val log = LoggerFactory.getLogger(RedPacketRefundSchedulerTask::class.java)

    @Scheduled(fixedRate = 60_000L)
    fun refundExpiredRedPackets() {
        try {
            redPacketRefundSchedulerPort.refundExpiredRedPackets()
        } catch (e: Exception) {
            log.warn("refundExpiredRedPackets error: {}", e.message)
        }
    }
}
