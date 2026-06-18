package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 登录安全告警扫描定时壳。
 *
 * SDK 只负责触发节奏；业务侧 [LoginSecurityAlertSchedulerPort] 保留规则、查询和告警上报逻辑。
 */
@Component
class LoginSecurityAlertSchedulerTask(
    private val loginSecurityAlertSchedulerPort: LoginSecurityAlertSchedulerPort,
) {
    private val log = LoggerFactory.getLogger(LoginSecurityAlertSchedulerTask::class.java)

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    fun scan() {
        try {
            loginSecurityAlertSchedulerPort.scanLoginSecurityAlerts()
        } catch (e: Exception) {
            log.warn("scanLoginSecurityAlerts error: {}", e.message)
        }
    }
}
