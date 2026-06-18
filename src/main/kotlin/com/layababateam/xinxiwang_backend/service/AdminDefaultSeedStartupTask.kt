package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 默认后台管理员启动种子壳。
 *
 * SDK 负责启动触发；业务侧 [AdminDefaultSeedPort] 保留账号策略和持久化逻辑。
 */
@Component
class AdminDefaultSeedStartupTask(
    private val adminDefaultSeedPort: AdminDefaultSeedPort,
) {
    private val log = LoggerFactory.getLogger(AdminDefaultSeedStartupTask::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun seedDefaultAdmin() {
        try {
            adminDefaultSeedPort.seedDefaultAdmin()
        } catch (e: Exception) {
            log.error("seed default admin error: {}", e.message, e)
        }
    }
}
