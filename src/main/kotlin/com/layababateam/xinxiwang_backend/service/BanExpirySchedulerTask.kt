package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 临时封禁到期处理定时壳。
 *
 * SDK 负责调度，业务侧 [BanExpiryPort] 负责实际解封、审计、通知和会话处理。
 */
@Component
class BanExpirySchedulerTask(
    private val banExpiryPort: BanExpiryPort,
) {
    private val log = LoggerFactory.getLogger(BanExpirySchedulerTask::class.java)

    @Scheduled(fixedRate = 60_000L)
    fun processExpiredBans() {
        try {
            banExpiryPort.processExpiredBans()
        } catch (e: Exception) {
            log.warn("processExpiredBans error: {}", e.message)
        }
    }
}
