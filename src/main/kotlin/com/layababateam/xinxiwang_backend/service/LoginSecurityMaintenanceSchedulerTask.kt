package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 登录安全维护定时壳。
 *
 * SDK 负责周期触发；业务侧 [LoginSecurityMaintenancePort] 保留封禁缓存刷新和事件清理规则。
 */
@Component
class LoginSecurityMaintenanceSchedulerTask(
    private val loginSecurityMaintenancePort: LoginSecurityMaintenancePort,
) {
    private val log = LoggerFactory.getLogger(LoginSecurityMaintenanceSchedulerTask::class.java)

    @Scheduled(fixedDelay = 60_000L, initialDelay = 0L)
    fun refreshActiveBlocks() {
        runTask("refreshActiveBlocks") {
            loginSecurityMaintenancePort.refreshActiveBlocks()
        }
    }

    @Scheduled(fixedDelay = 6 * 60 * 60 * 1_000L, initialDelay = 5 * 60 * 1_000L)
    fun cleanupOldEvents() {
        runTask("cleanupOldEvents") {
            loginSecurityMaintenancePort.cleanupOldEvents()
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
