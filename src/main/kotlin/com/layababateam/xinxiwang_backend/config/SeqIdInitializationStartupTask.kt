package com.layababateam.xinxiwang_backend.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * 会话 seqId 启动校准壳。
 *
 * SDK 保留启动顺序；业务侧 [SeqIdInitializationPort] 保留 Mongo/Redis 校准逻辑。
 */
@Component
class SeqIdInitializationStartupTask(
    private val seqIdInitializationPort: SeqIdInitializationPort,
) {
    private val log = LoggerFactory.getLogger(SeqIdInitializationStartupTask::class.java)

    @EventListener(ApplicationReadyEvent::class)
    @Order(1)
    fun syncSeqIds() {
        try {
            seqIdInitializationPort.syncSeqIds()
        } catch (e: Exception) {
            log.error("sync seq ids error: {}", e.message, e)
        }
    }
}
