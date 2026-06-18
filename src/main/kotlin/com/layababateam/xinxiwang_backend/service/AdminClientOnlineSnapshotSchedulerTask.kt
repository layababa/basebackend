package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 后台客户端在线快照定时壳。
 *
 * SDK 负责按小时触发；业务侧 [AdminClientOnlineSnapshotPort] 保留在线聚合、快照存储和过期策略。
 */
@Component
class AdminClientOnlineSnapshotSchedulerTask(
    private val adminClientOnlineSnapshotPort: AdminClientOnlineSnapshotPort,
) {
    private val log = LoggerFactory.getLogger(AdminClientOnlineSnapshotSchedulerTask::class.java)

    @Scheduled(cron = "\${xinxiwang.admin.client-online-snapshot-cron:0 0 * * * *}")
    fun snapshotOnlineCounts() {
        try {
            adminClientOnlineSnapshotPort.snapshotOnlineCounts()
        } catch (e: Exception) {
            log.warn("snapshotOnlineCounts error: {}", e.message)
        }
    }
}
