package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 登录安全缓存启动预热壳。
 *
 * SDK 负责启动触发；业务侧 [LoginSecurityMaintenancePort] 保留封禁缓存刷新规则。
 */
@Component
class LoginSecurityWarmupStartupTask(
    private val loginSecurityMaintenancePort: LoginSecurityMaintenancePort,
) {
    private val log = LoggerFactory.getLogger(LoginSecurityWarmupStartupTask::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun warmActiveBlocks() {
        try {
            loginSecurityMaintenancePort.refreshActiveBlocks()
        } catch (e: Exception) {
            log.warn("warm active login security blocks error: {}", e.message)
        }
    }
}
