package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 已读点落库定时壳。
 *
 * SDK 只负责触发节奏；业务侧 [ReadPointFlushPort] 保留 Redis/Mongo 批量落库细节。
 */
@Component
class ReadPointFlushSchedulerTask(
    private val readPointFlushPort: ReadPointFlushPort,
) {
    private val log = LoggerFactory.getLogger(ReadPointFlushSchedulerTask::class.java)

    @Scheduled(fixedDelay = 2_000L)
    fun flushReadPoints() {
        try {
            readPointFlushPort.flushReadPoints()
        } catch (e: Exception) {
            log.warn("flushReadPoints error: {}", e.message)
        }
    }
}
