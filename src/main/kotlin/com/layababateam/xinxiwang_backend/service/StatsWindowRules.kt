package com.layababateam.xinxiwang_backend.service

/**
 * 统计窗口纯规则。
 *
 * 这里只归一化窗口天数和起始时间；业务侧负责具体聚合口径。
 */
object StatsWindowRules {
    const val DAY_MS: Long = 24 * 60 * 60 * 1000L

    fun days(days: Int, max: Int, min: Int = 1): Int =
        days.coerceIn(min, max)

    fun startAt(now: Long, days: Int, maxDays: Int, minDays: Int = 1): Long =
        now - days(days, maxDays, minDays).toLong() * DAY_MS
}
