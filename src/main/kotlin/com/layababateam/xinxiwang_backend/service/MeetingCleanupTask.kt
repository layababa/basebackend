package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 会议清理定时壳。
 *
 * SDK 只保留调度节奏和执行边界，具体会议状态变更由业务侧 [MeetingCleanupPort] 适配。
 */
@Component
class MeetingCleanupTask(
    private val meetingCleanupPort: MeetingCleanupPort,
) {
    private val log = LoggerFactory.getLogger(MeetingCleanupTask::class.java)

    companion object {
        private const val EMPTY_TIMEOUT_MS = 60_000L
        private const val MAX_DURATION_MS = 24 * 60 * 60 * 1000L
        private const val REMINDER_SCAN_RATE_MS = 15_000L
        private const val SCHEDULE_REMINDER_BEFORE_MS = 60_000L
    }

    @Scheduled(fixedRate = REMINDER_SCAN_RATE_MS)
    fun sendScheduleReminders() {
        runTask("sendScheduleReminders") {
            val reminded = meetingCleanupPort.sendScheduleReminders(
                now = System.currentTimeMillis(),
                reminderBeforeMs = SCHEDULE_REMINDER_BEFORE_MS,
            )
            if (reminded > 0) {
                log.info("[会议] 已发送预约提醒 count={}", reminded)
            }
        }
    }

    @Scheduled(fixedRate = 60_000L)
    fun cleanupMeetings() {
        runTask("cleanupMeetings") {
            val result = meetingCleanupPort.cleanupMeetings(
                now = System.currentTimeMillis(),
                emptyTimeoutMs = EMPTY_TIMEOUT_MS,
                maxDurationMs = MAX_DURATION_MS,
            )
            if (result != MeetingCleanupResult()) {
                log.info(
                    "[会议] 清理完成 missedSchedules={}, emptyMeetings={}, inconsistentEmptyMeetings={}, expiredMeetings={}",
                    result.missedSchedules,
                    result.emptyMeetings,
                    result.inconsistentEmptyMeetings,
                    result.expiredMeetings,
                )
            }
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
