package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.Meeting
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 会议预约与展示状态纯规则。
 *
 * 业务侧负责持久化会议状态；这里统一预约状态常量、循环规则和客户端展示映射。
 */
object MeetingScheduleRules {
    const val STATUS_ACTIVE = 0
    const val STATUS_ENDED = 1
    const val STATUS_SCHEDULED = 2
    const val STATUS_CANCELED = 3

    const val RECURRING_NEVER = "never"
    const val RECURRING_DAILY = "daily"
    const val RECURRING_WEEKLY = "weekly"
    const val RECURRING_BIWEEKLY = "biweekly"
    const val RECURRING_MONTHLY = "monthly"

    val recurringRules = setOf(
        RECURRING_NEVER,
        RECURRING_DAILY,
        RECURRING_WEEKLY,
        RECURRING_BIWEEKLY,
        RECURRING_MONTHLY,
    )

    fun normalizeRecurringRule(rule: String?, recurring: Boolean): String {
        val value = rule?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (value != null && value in recurringRules) return value
        // 旧客户端只会传 recurring=true/false；true 时给一个可展示的默认循环规则。
        return if (recurring) RECURRING_WEEKLY else RECURRING_NEVER
    }

    fun effectiveRecurringRule(meeting: Meeting): String =
        normalizeRecurringRule(meeting.recurringRule, meeting.recurring)

    fun nextRecurringStartAt(
        scheduledStartAt: Long?,
        recurringRule: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val start = scheduledStartAt ?: return null
        return when (recurringRule) {
            RECURRING_DAILY -> start + TimeUnit.DAYS.toMillis(1)
            RECURRING_WEEKLY -> start + TimeUnit.DAYS.toMillis(7)
            RECURRING_BIWEEKLY -> start + TimeUnit.DAYS.toMillis(14)
            RECURRING_MONTHLY -> Instant.ofEpochMilli(start)
                .atZone(zoneId)
                .plusMonths(1)
                .toInstant()
                .toEpochMilli()
            else -> null
        }
    }

    fun nextRecurringStartAt(meeting: Meeting, zoneId: ZoneId = ZoneId.systemDefault()): Long? =
        nextRecurringStartAt(meeting.scheduledStartAt, effectiveRecurringRule(meeting), zoneId)

    /**
     * 旧客户端只认识 active/ended；预约和取消都压成 ended。
     */
    fun legacyStatus(status: Int): Int =
        if (status == STATUS_ACTIVE) STATUS_ACTIVE else STATUS_ENDED

    fun shareCardStatus(status: Int): String =
        if (status == STATUS_ACTIVE) "active" else "ended"

    fun scheduleStatus(status: Int): String = when (status) {
        STATUS_ACTIVE -> "active"
        STATUS_SCHEDULED -> "scheduled"
        STATUS_CANCELED -> "canceled"
        else -> "ended"
    }
}
