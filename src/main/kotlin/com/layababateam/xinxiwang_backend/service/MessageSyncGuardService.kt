package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration

@Service
class MessageSyncGuardService(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${xinxiwang.message-sync.guard.enabled:true}") private val enabled: Boolean,
    @Value("\${xinxiwang.message-sync.guard.duplicate-window-ms:250}") private val duplicateWindowMs: Long,
    @Value("\${xinxiwang.message-sync.guard.v3-query-user-per-second:30}") private val v3QueryUserPerSecond: Int,
    @Value("\${xinxiwang.message-sync.guard.v3-query-conversation-per-second:8}") private val v3QueryConversationPerSecond: Int,
    @Value("\${xinxiwang.message-sync.guard.v3-sync-user-per-second:40}") private val v3SyncUserPerSecond: Int,
    @Value("\${xinxiwang.message-sync.guard.v3-sync-conversation-per-second:10}") private val v3SyncConversationPerSecond: Int,
    @Value("\${xinxiwang.message-sync.guard.v3-batch-sync-per-second:5}") private val v3BatchSyncPerSecond: Int,
    @Value("\${xinxiwang.message-sync.guard.read-point-batch-per-second:10}") private val readPointBatchPerSecond: Int,
) {
    private val log = LoggerFactory.getLogger(MessageSyncGuardService::class.java)

    enum class Decision {
        ALLOW,
        SUPPRESS_DUPLICATE,
        RATE_LIMITED,
    }

    data class GuardResult(
        val decision: Decision,
        val reason: String? = null,
    ) {
        val allowed: Boolean get() = decision == Decision.ALLOW
    }

    fun checkV3Sync(
        userId: String,
        deviceId: String?,
        conversationId: String,
        afterSeqId: Long,
        limit: Int,
        hasRequestId: Boolean,
    ): GuardResult = checkFetch(
        type = "v3_sync",
        userId = userId,
        deviceId = deviceId,
        conversationId = conversationId,
        fingerprintParts = listOf(conversationId, afterSeqId.toString(), limit.toString()),
        perUserLimit = v3SyncUserPerSecond,
        perConversationLimit = v3SyncConversationPerSecond,
        hasRequestId = hasRequestId,
    )

    fun checkV3Query(
        userId: String,
        deviceId: String?,
        conversationId: String,
        afterSeqId: Long?,
        beforeSeqId: Long?,
        maxCount: Int,
        descending: Boolean,
        contentTypes: List<Int>?,
        hasRequestId: Boolean,
    ): GuardResult = checkFetch(
        type = "v3_query",
        userId = userId,
        deviceId = deviceId,
        conversationId = conversationId,
        fingerprintParts = listOf(
            conversationId,
            afterSeqId?.toString() ?: "",
            beforeSeqId?.toString() ?: "",
            maxCount.toString(),
            descending.toString(),
            contentTypes?.joinToString(",") ?: "",
        ),
        perUserLimit = v3QueryUserPerSecond,
        perConversationLimit = v3QueryConversationPerSecond,
        hasRequestId = hasRequestId,
    )

    fun checkV3BatchSync(userId: String, deviceId: String?): GuardResult {
        if (!enabled) return GuardResult(Decision.ALLOW)
        return try {
            val scope = scope(userId, deviceId)
            if (!incrementWithinLimit("xinxiwang:imreq:rate:v3_batch:$scope", v3BatchSyncPerSecond, ONE_SECOND)) {
                GuardResult(Decision.RATE_LIMITED, "v3_batch_sync_rate")
            } else {
                GuardResult(Decision.ALLOW)
            }
        } catch (e: Exception) {
            log.debug("message sync batch guard fail-open: {}", e.message)
            GuardResult(Decision.ALLOW)
        }
    }

    fun checkReadPointBatch(userId: String, deviceId: String?): GuardResult {
        if (!enabled) return GuardResult(Decision.ALLOW)
        return try {
            val scope = scope(userId, deviceId)
            if (!incrementWithinLimit("xinxiwang:imreq:rate:read_batch:$scope", readPointBatchPerSecond, ONE_SECOND)) {
                GuardResult(Decision.RATE_LIMITED, "read_point_batch_rate")
            } else {
                GuardResult(Decision.ALLOW)
            }
        } catch (e: Exception) {
            log.debug("read point batch guard fail-open: {}", e.message)
            GuardResult(Decision.ALLOW)
        }
    }

    private fun checkFetch(
        type: String,
        userId: String,
        deviceId: String?,
        conversationId: String,
        fingerprintParts: List<String>,
        perUserLimit: Int,
        perConversationLimit: Int,
        hasRequestId: Boolean,
    ): GuardResult {
        if (!enabled) return GuardResult(Decision.ALLOW)
        return try {
            val scope = scope(userId, deviceId)
            val duplicateWindow = duplicateWindow()
            if (!hasRequestId && duplicateWindow != null) {
                val duplicateKey = "xinxiwang:imreq:dup:$type:${hash(scope, fingerprintParts.joinToString("|"))}"
                val first = redisTemplate.opsForValue().setIfAbsent(duplicateKey, "1", duplicateWindow) ?: true
                if (!first) {
                    return GuardResult(Decision.SUPPRESS_DUPLICATE, "duplicate")
                }
            }

            if (!incrementWithinLimit("xinxiwang:imreq:rate:$type:user:$scope", perUserLimit, ONE_SECOND)) {
                return GuardResult(Decision.RATE_LIMITED, "user_rate")
            }
            val convScope = hash(scope, conversationId)
            if (!incrementWithinLimit("xinxiwang:imreq:rate:$type:conv:$convScope", perConversationLimit, ONE_SECOND)) {
                return GuardResult(Decision.RATE_LIMITED, "conversation_rate")
            }
            GuardResult(Decision.ALLOW)
        } catch (e: Exception) {
            log.debug("message sync guard fail-open type={} user={} err={}", type, userId, e.message)
            GuardResult(Decision.ALLOW)
        }
    }

    private fun incrementWithinLimit(key: String, limit: Int, window: Duration): Boolean {
        if (limit <= 0) return true
        val current = redisTemplate.opsForValue().increment(key) ?: 1L
        if (current == 1L) redisTemplate.expire(key, window)
        return current <= limit
    }

    private fun duplicateWindow(): Duration? =
        if (duplicateWindowMs > 0) Duration.ofMillis(duplicateWindowMs) else null

    private fun scope(userId: String, deviceId: String?): String =
        hash(userId, deviceId?.takeIf { it.isNotBlank() } ?: "unknown")

    companion object {
        private val ONE_SECOND: Duration = Duration.ofSeconds(1)
        const val MAX_ACKS_PER_BATCH = 200
        const val MAX_READ_POINTS_PER_BATCH = 50

        fun ackSeqIdsFrom(data: Map<String, Any?>, maxCount: Int = MAX_ACKS_PER_BATCH): List<Long> {
            val raw = data["acks"] as? List<*> ?: return emptyList()
            val seen = LinkedHashSet<Long>()
            for (item in raw) {
                val ack = item as? Map<*, *> ?: continue
                val seqId = (ack["seqId"] as? Number)?.toLong() ?: continue
                if (seqId <= 0) continue
                seen.add(seqId)
                if (seen.size >= maxCount) break
            }
            return seen.toList()
        }

        fun compactReadPoints(
            points: List<Map<String, Any?>>,
            fallbackDeviceId: String? = null,
            maxCount: Int = MAX_READ_POINTS_PER_BATCH,
        ): List<Map<String, Any?>> {
            val compacted = LinkedHashMap<String, MutableMap<String, Any?>>()
            for (point in points) {
                val conversationId = (point["conversationId"] as? String)?.takeIf { it.isNotBlank() } ?: continue
                val seqId = (point["seqId"] as? Number)?.toLong() ?: continue
                if (seqId <= 0) continue
                val effectiveDeviceId = (point["deviceId"] as? String)?.takeIf { it.isNotBlank() }
                    ?: fallbackDeviceId?.takeIf { it.isNotBlank() }
                    ?: ""
                val key = "$conversationId\u001F$effectiveDeviceId"
                val existing = compacted[key]
                val existingSeq = (existing?.get("seqId") as? Number)?.toLong() ?: 0L
                if (existing == null || seqId > existingSeq) {
                    if (existing == null && compacted.size >= maxCount) continue
                    compacted[key] = point.toMutableMap().apply {
                        this["conversationId"] = conversationId
                        this["seqId"] = seqId
                    }
                }
            }
            return compacted.values.toList()
        }

        private fun hash(vararg parts: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(parts.joinToString("\u001F").toByteArray(Charsets.UTF_8))
            return digest.take(16).joinToString("") { "%02x".format(it) }
        }
    }
}
