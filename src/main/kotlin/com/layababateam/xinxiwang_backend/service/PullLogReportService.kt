package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.DebugLogReport
import com.layababateam.xinxiwang_backend.repository.DebugLogReportRepository
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class PullLogReportService(
    private val debugLogReportRepository: DebugLogReportRepository,
    private val redisTemplate: StringRedisTemplate,
    private val mongoTemplate: MongoTemplate,
) : PullLogReportPort {
    private val log = LoggerFactory.getLogger(PullLogReportService::class.java)

    override fun acknowledgePullLog(userId: String, requestId: String) {
        val report = findOwnedReport("pull_log_ack", userId, requestId) ?: return
        val now = Instant.now()
        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(requestId)),
            Update().set("status", "acked").set("ackedAt", now).set("updatedAt", now),
            DebugLogReport::class.java,
        )
        log.info("pull_log_ack received: requestId={} userId={}", report.id, userId)
    }

    override fun completePullLog(
        userId: String,
        requestId: String,
        logObjectKey: String?,
        fileSize: Long?,
        fileCount: Int?,
    ) {
        findOwnedReport("pull_log_done", userId, requestId) ?: return
        val now = Instant.now()
        val update = Update()
            .set("status", "uploaded")
            .set("uploadedAt", now)
            .set("updatedAt", now)
        if (logObjectKey != null) update.set("logObjectKey", logObjectKey)
        if (fileSize != null) update.set("fileSize", fileSize)
        if (fileCount != null) update.set("fileCount", fileCount)
        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(requestId)),
            update,
            DebugLogReport::class.java,
        )
        redisTemplate.opsForZSet().remove(PullLogCommandSender.TIMEOUT_ZSET_KEY, requestId)
        log.info("pull_log_done received: requestId={} key={} size={}", requestId, logObjectKey, fileSize)
    }

    override fun failPullLog(
        userId: String,
        requestId: String,
        errorCode: String?,
        errorMsg: String?,
    ) {
        findOwnedReport("pull_log_failed", userId, requestId) ?: return
        val now = Instant.now()
        val update = Update().set("status", "failed").set("updatedAt", now)
        if (errorCode != null) update.set("errorCode", errorCode)
        if (errorMsg != null) update.set("errorMsg", errorMsg)
        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(requestId)),
            update,
            DebugLogReport::class.java,
        )
        redisTemplate.opsForZSet().remove(PullLogCommandSender.TIMEOUT_ZSET_KEY, requestId)
        log.info("pull_log_failed received: requestId={} errorCode={} msg={}", requestId, errorCode, errorMsg)
    }

    private fun findOwnedReport(action: String, userId: String, requestId: String): DebugLogReport? {
        val report = debugLogReportRepository.findById(requestId).orElse(null) ?: run {
            log.warn("{}: report not found requestId={}", action, requestId)
            return null
        }
        if (report.userId != userId) {
            log.warn("{}: userId mismatch requestId={} expected={} got={}", action, requestId, report.userId, userId)
            return null
        }
        return report
    }
}
