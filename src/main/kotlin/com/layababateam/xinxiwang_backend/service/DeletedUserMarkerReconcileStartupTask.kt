package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 已删除用户标记启动校准壳。
 *
 * SDK 负责启动触发；业务侧 [DeletedUserMarkerReconcilePort] 保留 Mongo 查询和恢复逻辑。
 */
@Component
class DeletedUserMarkerReconcileStartupTask(
    private val deletedUserMarkerReconcilePort: DeletedUserMarkerReconcilePort,
) {
    private val log = LoggerFactory.getLogger(DeletedUserMarkerReconcileStartupTask::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun reconcileRecentDeletedUsers() {
        try {
            deletedUserMarkerReconcilePort.reconcileRecentDeletedUsers()
        } catch (e: Exception) {
            log.warn("reconcile deleted user markers on startup error: {}", e.message)
        }
    }
}
