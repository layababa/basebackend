package com.layababateam.xinxiwang_backend.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 后端 Sentry 配置启动重载壳。
 *
 * SDK 负责 ApplicationReady 触发；业务侧 [BackendSentryReloadPort] 保留配置读取和重建逻辑。
 */
@Component
class BackendSentryReloadStartupTask(
    private val backendSentryReloadPort: BackendSentryReloadPort,
) {
    private val log = LoggerFactory.getLogger(BackendSentryReloadStartupTask::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun reloadFromDb() {
        try {
            backendSentryReloadPort.reloadFromDb()
        } catch (e: Exception) {
            log.warn("reload backend sentry config error: {}", e.message)
        }
    }
}
