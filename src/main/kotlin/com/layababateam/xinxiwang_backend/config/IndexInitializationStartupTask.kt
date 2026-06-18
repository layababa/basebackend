package com.layababateam.xinxiwang_backend.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * 索引初始化启动壳。
 *
 * SDK 保留启动顺序；业务侧 [IndexInitializationPort] 保留集合清理和索引定义。
 */
@Component
class IndexInitializationStartupTask(
    private val indexInitializationPort: IndexInitializationPort,
) {
    private val log = LoggerFactory.getLogger(IndexInitializationStartupTask::class.java)

    @EventListener(ApplicationReadyEvent::class)
    @Order(0)
    fun initIndexes() {
        try {
            indexInitializationPort.initIndexes()
        } catch (e: Exception) {
            log.error("init indexes error: {}", e.message, e)
        }
    }
}
