package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 已删除用户标记校准定时壳。
 *
 * SDK 负责按配置触发；业务侧 [DeletedUserMarkerReconcilePort] 保留 Mongo 查询和恢复逻辑。
 */
@Component
class DeletedUserMarkerReconcileSchedulerTask(
    private val deletedUserMarkerReconcilePort: DeletedUserMarkerReconcilePort,
) {
    private val log = LoggerFactory.getLogger(DeletedUserMarkerReconcileSchedulerTask::class.java)

    @Scheduled(
        fixedDelayString = "\${xinxiwang.auth.deleted-marker-reconcile-delay-ms:3600000}",
        initialDelayString = "\${xinxiwang.auth.deleted-marker-reconcile-delay-ms:3600000}",
    )
    fun reconcileRecentDeletedUsers() {
        try {
            deletedUserMarkerReconcilePort.reconcileRecentDeletedUsers()
        } catch (e: Exception) {
            log.warn("reconcileRecentDeletedUsers error: {}", e.message)
        }
    }
}
