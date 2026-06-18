package com.layababateam.xinxiwang_backend.service

/**
 * 时间范围纯规则。
 *
 * 这里只处理非负时长、剩余时间和窗口重叠；业务侧负责时间戳来源和领域含义。
 */
object TimeRangeRules {
    fun nonNegativeMillis(startAt: Long, endAt: Long): Long =
        (endAt - startAt).coerceAtLeast(0)

    fun nonNegativeSeconds(startAt: Long, endAt: Long): Long =
        nonNegativeMillis(startAt, endAt) / 1000

    fun remainingMillis(expiresAt: Long, now: Long): Long =
        nonNegativeMillis(now, expiresAt)

    fun overlapSeconds(startAt: Long, endAt: Long, windowStart: Long, windowEnd: Long): Long {
        val boundedStart = maxOf(startAt, windowStart)
        val boundedEnd = minOf(endAt, windowEnd)
        return nonNegativeSeconds(boundedStart, boundedEnd)
    }
}
