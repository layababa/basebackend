package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.DebugLogReport
import com.layababateam.xinxiwang_backend.repository.DebugLogReportRepository
import com.layababateam.xinxiwang_backend.repository.DeviceSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant

@Service
class PullLogCommandSender(
    private val deliveryPort: PullLogDeliveryPort,
    private val deviceSessionRepository: DeviceSessionRepository,
    @Suppress("unused") private val debugLogReportRepository: DebugLogReportRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: JsonMapper,
    private val mongoTemplate: MongoTemplate,
) {
    private val log = LoggerFactory.getLogger(PullLogCommandSender::class.java)

    companion object {
        private const val PROTOCOL_VERSION = 1
        private val PENDING_TTL: Duration = Duration.ofDays(7)
        const val PENDING_KEY_PREFIX = "pending_log_cmd:"
        const val PENDING_KEY_PREFIX_V2 = "pending_log_cmd_v2:"
        const val TIMEOUT_ZSET_KEY = "debug_log_timeout"

        fun pendingKey(userId: String, deviceId: String) = "$PENDING_KEY_PREFIX$userId:$deviceId"
        fun pendingKeyV2(userId: String) = "$PENDING_KEY_PREFIX_V2$userId"
    }

    fun buildCmdPayload(report: DebugLogReport): String {
        val payload = linkedMapOf<String, Any?>(
            "type" to "pull_log_cmd",
            "v" to PROTOCOL_VERSION,
            "requestId" to (report.id ?: ""),
            "serverTime" to System.currentTimeMillis(),
            "expireAt" to report.expireAt.toEpochMilli(),
            "targetDeviceId" to report.targetDeviceId,
            "timeRangeDays" to report.timeRangeDays,
            "logLevel" to report.logLevel,
        )
        return objectMapper.writeValueAsString(payload)
    }

    fun trySend(report: DebugLogReport): Boolean {
        val reportId = report.id ?: error("DebugLogReport.id is null")
        redisTemplate.opsForZSet().add(TIMEOUT_ZSET_KEY, reportId, report.expireAt.toEpochMilli().toDouble())
        redisTemplate.expire(TIMEOUT_ZSET_KEY, Duration.ofDays(7))

        val payload = buildCmdPayload(report)
        val channels = deliveryPort.getChannels(report.userId)
        if (channels.isNotEmpty()) {
            val exactMatch = channels.firstOrNull { channel -> deviceIdOfChannel(channel) == report.targetDeviceId }
            val chosenChannel = exactMatch?.takeIf { it.isActive }
                ?: channels.firstOrNull { it.isActive }
            if (chosenChannel != null) {
                val deliveredDeviceId = deviceIdOfChannel(chosenChannel) ?: report.targetDeviceId
                val now = Instant.now()
                val casQuery = Query(Criteria.where("_id").`is`(reportId).and("status").`is`("pending"))
                val update = Update().set("status", "sent").set("sentAt", now).set("updatedAt", now)
                val updated = mongoTemplate.updateFirst(casQuery, update, DebugLogReport::class.java)
                if (updated.modifiedCount == 0L) {
                    log.info("pull_log_cmd CAS miss (already sent/drained): requestId={}", reportId)
                    return true
                }
                val finalPayload = if (deliveredDeviceId != report.targetDeviceId) {
                    log.info(
                        "pull_log_cmd fallback delivery: requestId={} target={} delivered={}",
                        reportId,
                        report.targetDeviceId,
                        deliveredDeviceId,
                    )
                    rewriteTargetDeviceId(payload, deliveredDeviceId)
                } else {
                    payload
                }
                deliveryPort.sendJsonToChannel(chosenChannel, finalPayload)
                log.info(
                    "pull_log_cmd sent immediately: requestId={} userId={} deviceId={}",
                    reportId,
                    report.userId,
                    deliveredDeviceId,
                )
                return true
            }
        }

        val key = pendingKeyV2(report.userId)
        redisTemplate.opsForList().rightPush(key, payload)
        redisTemplate.expire(key, PENDING_TTL)
        log.info(
            "pull_log_cmd queued (target offline): requestId={} userId={} deviceId={} key={}",
            reportId,
            report.userId,
            report.targetDeviceId,
            key,
        )
        return false
    }

    fun drainPending(userId: String, deviceId: String?, sender: (String) -> Unit) {
        val ops = redisTemplate.opsForList()

        val v2Key = pendingKeyV2(userId)
        drainKey(ops, v2Key, userId, deviceId, sender)

        try {
            val pattern = "$PENDING_KEY_PREFIX$userId:*"
            val oldKeys = mutableListOf<String>()
            redisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build(),
            ).use { cursor ->
                while (cursor.hasNext()) {
                    oldKeys.add(cursor.next())
                }
            }
            for (key in oldKeys) {
                drainKey(ops, key, userId, deviceId, sender)
            }
        } catch (e: Exception) {
            log.debug("drainPending: failed to scan legacy V1 keys for userId={}: {}", userId, e.message)
        }
    }

    private fun drainKey(
        ops: org.springframework.data.redis.core.ListOperations<String, String>,
        key: String,
        userId: String,
        deviceId: String?,
        sender: (String) -> Unit,
    ) {
        while (true) {
            val payload = ops.leftPop(key) ?: break
            try {
                val node = readPayload(payload)
                val requestId = node["requestId"] as? String
                val originalTarget = (node["targetDeviceId"] as? String).orEmpty()
                if (requestId.isNullOrBlank()) {
                    sender(if (deviceId != null) rewriteTargetDeviceId(payload, deviceId) else payload)
                    continue
                }

                val now = Instant.now()
                val casQuery = Query(Criteria.where("_id").`is`(requestId).and("status").`is`("pending"))
                val update = Update().set("status", "sent").set("sentAt", now).set("updatedAt", now)
                val updated = mongoTemplate.updateFirst(casQuery, update, DebugLogReport::class.java)
                if (updated.modifiedCount == 0L) {
                    log.info("drainPending CAS miss; skip rid={}", requestId)
                    continue
                }
                val finalPayload = if (deviceId != null && originalTarget != deviceId) {
                    rewriteTargetDeviceId(payload, deviceId)
                } else {
                    payload
                }
                sender(finalPayload)
                log.info(
                    "pull_log_cmd drained: userId={} key={} originalTarget={} deliveredTo={} rid={}",
                    userId,
                    key,
                    originalTarget,
                    deviceId ?: originalTarget,
                    requestId,
                )
            } catch (e: Exception) {
                log.warn("drain failed userId={} key={}: {}", userId, key, e.message)
            }
        }
    }

    private fun deviceIdOfChannel(channel: io.netty.channel.Channel): String? {
        val token = deliveryPort.getTokenForChannel(channel) ?: return null
        return deviceSessionRepository.findByToken(token)?.deviceId
    }

    private fun rewriteTargetDeviceId(payload: String, newDeviceId: String): String = try {
        val node = readPayload(payload).toMutableMap()
        node["targetDeviceId"] = newDeviceId
        objectMapper.writeValueAsString(node)
    } catch (e: Exception) {
        log.warn("rewriteTargetDeviceId failed: {}", e.message)
        payload
    }

    @Suppress("UNCHECKED_CAST")
    private fun readPayload(payload: String): Map<String, Any?> =
        objectMapper.readValue(payload, Map::class.java) as Map<String, Any?>
}
