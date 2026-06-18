package com.layababateam.xinxiwang_backend.scheduler

import com.layababateam.xinxiwang_backend.model.DebugLogReport
import com.layababateam.xinxiwang_backend.service.DebugLogTimeoutQueueKeys
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 扫描调试日志拉取超时队列，并用 CAS 将仍处于活跃状态的报告标记为 timeout。
 */
@Component
class DebugLogTimeoutScanner(
    private val redisTemplate: StringRedisTemplate,
    private val mongoTemplate: MongoTemplate,
) {
    private val log = LoggerFactory.getLogger(DebugLogTimeoutScanner::class.java)
    private val activeStatuses = setOf("pending", "sent", "acked")

    companion object {
        private const val LOCK_KEY = "xinxiwang:lock:debug-log-timeout-scan"
        private val LOCK_TTL = Duration.ofSeconds(60)
    }

    @Scheduled(fixedDelay = 30_000)
    fun scan() {
        val locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL) ?: false
        if (!locked) return
        try {
            val expired = expiredReportIds(System.currentTimeMillis().toDouble())
            if (expired.isEmpty()) return
            expired.forEach(::markTimeoutIfActive)
        } finally {
            redisTemplate.delete(LOCK_KEY)
        }
    }

    private fun expiredReportIds(now: Double): List<String> {
        val expired = mutableListOf<String>()
        for (shard in 0 until DebugLogTimeoutQueueKeys.SHARD_COUNT) {
            val shardKey = "${DebugLogTimeoutQueueKeys.TIMEOUT_ZSET_KEY}:$shard"
            val slice = redisTemplate.opsForZSet().rangeByScore(shardKey, 0.0, now)
            if (!slice.isNullOrEmpty()) expired.addAll(slice)
        }
        return expired
    }

    private fun markTimeoutIfActive(reportId: String) {
        try {
            val updateResult = mongoTemplate.updateFirst(
                Query(Criteria.where("_id").`is`(reportId).and("status").`in`(activeStatuses)),
                Update()
                    .set("status", "timeout")
                    .set("errorCode", "TIMEOUT")
                    .set("errorMsg", "no client response within expireAt window")
                    .set("updatedAt", Instant.now()),
                DebugLogReport::class.java,
            )
            if (updateResult.modifiedCount > 0) {
                log.info("DebugLogReport timed out: requestId={}", reportId)
            }
            redisTemplate.opsForZSet().remove(DebugLogTimeoutQueueKeys.shardKey(reportId), reportId)
        } catch (e: Exception) {
            // Mongo 更新失败时保留 ZSET 项，等待下次扫描重试。
            log.warn("Failed to mark timeout for requestId={}: {}", reportId, e.message)
        }
    }
}
