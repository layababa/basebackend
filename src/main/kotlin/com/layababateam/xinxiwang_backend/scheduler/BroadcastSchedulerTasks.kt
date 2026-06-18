package com.layababateam.xinxiwang_backend.scheduler

import com.layababateam.xinxiwang_backend.service.BroadcastSchedulerPort
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("legacy-broadcast")
class BroadcastSchedulerTasks(
    private val broadcastSchedulerPort: BroadcastSchedulerPort,
) {
    private val log = LoggerFactory.getLogger(BroadcastSchedulerTasks::class.java)

    @Scheduled(fixedDelay = 30_000L)
    fun transitionScheduled() {
        runTask("transitionScheduled") {
            val transitioned = broadcastSchedulerPort.transitionScheduledDue(System.currentTimeMillis())
            if (transitioned > 0) log.info("broadcast scheduler: transitioned {} scheduled->waiting", transitioned)
        }
    }

    @Scheduled(fixedDelay = 60_000L)
    fun dispatchReminders() {
        runTask("dispatchReminders") {
            val now = System.currentTimeMillis()
            val dispatched = broadcastSchedulerPort.dispatchReminders(now, now + 15L * 60 * 1000)
            if (dispatched > 0) log.info("broadcast scheduler: dispatched {} reminders", dispatched)
        }
    }

    @Scheduled(fixedDelay = 10_000L)
    fun expireRedPackets() {
        runTask("expireRedPackets") {
            val expired = broadcastSchedulerPort.expireRedPackets(System.currentTimeMillis())
            if (expired > 0) log.info("broadcast scheduler: expired {} red packets", expired)
        }
    }

    @Scheduled(fixedDelay = 10_000L)
    fun drawLuckyBags() {
        runTask("drawLuckyBags") {
            val drawn = broadcastSchedulerPort.drawLuckyBags(System.currentTimeMillis())
            if (drawn > 0) log.info("broadcast scheduler: drew {} lucky bags", drawn)
        }
    }

    @Scheduled(fixedDelay = 60_000L)
    fun archiveOldEnded() {
        runTask("archiveOldEnded") {
            val threshold = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            val archived = broadcastSchedulerPort.archiveOldEnded(threshold)
            if (archived > 0) log.info("broadcast scheduler: archived {} ended broadcasts", archived)
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
