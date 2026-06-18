package com.layababateam.xinxiwang_backend.service

data class MeetingCleanupResult(
    val missedSchedules: Int = 0,
    val emptyMeetings: Int = 0,
    val inconsistentEmptyMeetings: Int = 0,
    val expiredMeetings: Int = 0,
)

interface MeetingCleanupPort {
    fun sendScheduleReminders(now: Long, reminderBeforeMs: Long): Int

    fun cleanupMeetings(
        now: Long,
        emptyTimeoutMs: Long,
        maxDurationMs: Long,
    ): MeetingCleanupResult
}
