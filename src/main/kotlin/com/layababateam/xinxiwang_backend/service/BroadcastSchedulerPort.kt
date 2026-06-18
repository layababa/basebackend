package com.layababateam.xinxiwang_backend.service

interface BroadcastSchedulerPort {
    fun transitionScheduledDue(now: Long): Int

    fun dispatchReminders(now: Long, windowEnd: Long): Int

    fun expireRedPackets(now: Long): Int

    fun drawLuckyBags(now: Long): Int

    fun archiveOldEnded(threshold: Long): Int
}
