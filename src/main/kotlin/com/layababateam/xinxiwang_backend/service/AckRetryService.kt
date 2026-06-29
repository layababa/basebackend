package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.core.ScanOptions
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.LinkedHashSet

@Service
class AckRetryService(
    private val redisTemplate: StringRedisTemplate,
    private val userSessionManager: UserSessionManager,
    private val objectMapper: ObjectMapper,
    @Value("\${rentmsg.node.id:node-default}") private val nodeId: String
) {
    private val log = LoggerFactory.getLogger(AckRetryService::class.java)

    companion object {
        private const val PENDING_PREFIX = "rentmsg:pending:"
        private const val RETRY_PREFIX = "rentmsg:retry:"
        private const val SCAN_LOCK_KEY = "rentmsg:lock:ack-scan"
        private const val MAX_RETRIES = 3
        private const val ACK_TIMEOUT_MS = 10_000L
        /** 每轮扫描最多处理的用户 key 数，防止大规模扫描阻塞 */
        private const val MAX_KEYS_PER_SCAN = 500
        /** 每个用户每轮最多重试的消息条数 */
        private const val MAX_RETRIES_PER_USER = 20
        /** 登录后即时回放上限；剩余消息由 v3 sync 补偿，避免登录风暴挤占 WS 业务线程 */
        private const val MAX_REPLAY_ON_AUTH = 10L
        /** pending key 过期时间：24 小时，离线超过 24h 的消息由客户端 sync 补偿 */
        private val PENDING_TTL = Duration.ofHours(24)
        /** 每个用户最多保留 200 条 pending，超出则丢弃最旧的 */
        private const val MAX_PENDING_PER_USER = 200L
        /** ACK storm smoothing: collapse per-message ACKs into one Redis pipeline per user. */
        private const val ACK_FLUSH_DELAY_MS = 100L
        private const val MAX_BUFFERED_ACKS_PER_USER = 5_000
        /** retry counter TTL：pending 消息已由 sync 兜底，retry key 不允许永久驻留 */
        private val RETRY_COUNTER_TTL: Duration = Duration.ofMinutes(30)
        private const val RETRY_CLEANUP_LOCK_KEY = "rentmsg:lock:ack-retry-cleanup"
        private const val MAX_RETRY_KEYS_PER_CLEANUP = 10_000
    }

    data class AckEntry(
        val userId: String,
        val seqId: Long,
        val conversationId: String,
        val messageJson: String
    )

    fun addPendingAck(userId: String, seqId: Long, conversationId: String, messageJson: String) {
        addPendingAckBatch(listOf(AckEntry(userId, seqId, conversationId, messageJson)))
    }

    fun addPendingAckBatch(entries: List<AckEntry>) {
        if (entries.isEmpty()) return
        val now = System.currentTimeMillis()
        val serializedEntries = entries.map { entry ->
            entry to objectMapper.writeValueAsString(mapOf(
                "seqId" to entry.seqId,
                "conversationId" to entry.conversationId,
                "message" to entry.messageJson,
                "timestamp" to now
            ))
        }
        val entriesByUser = serializedEntries.groupBy { it.first.userId }

        @Suppress("UNCHECKED_CAST")
        val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
        @Suppress("UNCHECKED_CAST")
        val valueSerializer = redisTemplate.valueSerializer as RedisSerializer<String>
        val zCardResultIndexes = mutableListOf<Pair<String, Int>>()
        var commandIndex = 0

        val pipelineResults = redisTemplate.executePipelined { connection ->
            entriesByUser.forEach { (userId, userEntries) ->
                val key = "$PENDING_PREFIX$userId"
                val keyBytes = keySerializer.serialize(key)!!
                userEntries.forEach { (entry, ackEntryJson) ->
                    connection.zSetCommands().zAdd(
                        keyBytes,
                        entry.seqId.toDouble(),
                        valueSerializer.serialize(ackEntryJson)!!
                    )
                    commandIndex++
                }
                connection.keyCommands().expire(keyBytes, PENDING_TTL.seconds)
                commandIndex++
                zCardResultIndexes.add(key to commandIndex)
                connection.zSetCommands().zCard(keyBytes)
                commandIndex++
            }
            null
        }

        // Trim oversized keys after the write pipeline. Most keys stay below the limit,
        // so this preserves the exact cap without adding per-user round trips.
        val trimTargets = mutableListOf<Pair<String, Long>>()  // key to excess count
        zCardResultIndexes.forEach { (key, resultIndex) ->
            val size = (pipelineResults.getOrNull(resultIndex) as? Number)?.toLong() ?: 0L
            if (size > MAX_PENDING_PER_USER) {
                trimTargets.add(key to (size - MAX_PENDING_PER_USER - 1))
            }
        }
        if (trimTargets.isNotEmpty()) {
            try {
                redisTemplate.executePipelined { connection ->
                    trimTargets.forEach { (key, endIndex) ->
                        connection.zSetCommands().zRemRange(keySerializer.serialize(key)!!, 0, endIndex)
                    }
                    null
                }
            } catch (e: Exception) {
                log.warn("addPendingAckBatch pipeline ZREMRANGE failed: {}", e.message)
            }
        }
    }

    fun confirmAck(userId: String, seqId: Long) {
        confirmAckBatch(userId, listOf(seqId))
        log.debug("ACK confirmed for user {}, seqId {}", userId, seqId)
    }

    private val bufferedAcks = ConcurrentHashMap<String, MutableSet<Long>>()
    private val ackFlushScheduled = AtomicBoolean(false)
    private val ackFlushExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ack-buffer-flush").apply { isDaemon = true }
    }
    @Volatile private var ackFlushFuture: ScheduledFuture<*>? = null

    fun bufferAck(userId: String, seqId: Long) {
        bufferAckBatch(userId, listOf(seqId))
    }

    fun bufferAckBatch(userId: String, seqIds: Collection<Long>) {
        if (seqIds.isEmpty()) return
        val bucket = bufferedAcks.computeIfAbsent(userId) {
            java.util.Collections.synchronizedSet(LinkedHashSet<Long>())
        }
        synchronized(bucket) {
            seqIds.forEach { seqId ->
                if (seqId > 0) bucket.add(seqId)
            }
            while (bucket.size > MAX_BUFFERED_ACKS_PER_USER) {
                val iterator = bucket.iterator()
                if (!iterator.hasNext()) break
                iterator.next()
                iterator.remove()
            }
        }
        scheduleAckFlush()
    }

    private fun scheduleAckFlush() {
        if (!ackFlushScheduled.compareAndSet(false, true)) return
        ackFlushFuture = ackFlushExecutor.schedule({
            flushBufferedAcks()
        }, ACK_FLUSH_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun flushBufferedAcks() {
        ackFlushScheduled.set(false)
        val snapshot = mutableMapOf<String, List<Long>>()
        bufferedAcks.forEach { (userId, bucket) ->
            val seqIds = synchronized(bucket) {
                if (bucket.isEmpty()) {
                    emptyList()
                } else {
                    val values = bucket.toList()
                    bucket.clear()
                    values
                }
            }
            if (seqIds.isNotEmpty()) {
                snapshot[userId] = seqIds
            }
        }
        snapshot.forEach { (userId, seqIds) ->
            try {
                confirmAckBatch(userId, seqIds)
            } catch (e: Exception) {
                log.warn("Buffered ACK flush failed for user {}, count={}: {}", userId, seqIds.size, e.message)
                bufferAckBatch(userId, seqIds)
            }
        }
        if (bufferedAcks.values.any { bucket -> synchronized(bucket) { bucket.isNotEmpty() } }) {
            scheduleAckFlush()
        }
    }

    fun confirmAckBatch(userId: String, seqIds: Collection<Long>) {
        if (seqIds.isEmpty()) return
        val key = "$PENDING_PREFIX$userId"
        val uniqueSeqIds = seqIds.distinct()
        @Suppress("UNCHECKED_CAST")
        val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
        redisTemplate.executePipelined { connection ->
            val pendingKeyBytes = keySerializer.serialize(key)!!
            uniqueSeqIds.forEach { seqId ->
                connection.zSetCommands().zRemRangeByScore(pendingKeyBytes, seqId.toDouble(), seqId.toDouble())
            }
            uniqueSeqIds.forEach { seqId ->
                connection.keyCommands().del(keySerializer.serialize("$RETRY_PREFIX$userId:$seqId")!!)
            }
            null
        }
        log.debug("Batch ACK confirmed for user {}, count={}", userId, uniqueSeqIds.size)
    }

    @PreDestroy
    fun shutdownAckBuffer() {
        ackFlushFuture?.cancel(false)
        flushBufferedAcks()
        ackFlushExecutor.shutdown()
    }

    /**
     * 去重推送：60 秒内同一 userId + seqId 只推送一次。
     * 用于 replayPendingToUser 和 scanPendingAcks 两路推送的去重，
     * 防止 auth 回放和定时扫描在同一时间窗口内对同一条消息重复推送。
     *
     * @return true 如果本次推送成功（即 60s 内首次），false 如果已被去重跳过
     */
    fun pushPendingSafely(userId: String, seqId: Long, wsMessage: String, skipApns: Boolean = true): Boolean {
        val dedupKey = "rentmsg:replayed:$userId:$seqId"
        val result = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofSeconds(8))
        when (result) {
            true -> {
                try {
                    userSessionManager.pushToUser(userId, wsMessage, skipApns = skipApns)
                } catch (e: Exception) {
                    // Push failed — roll back dedup key so next retry can re-attempt
                    try { redisTemplate.delete(dedupKey) } catch (_: Exception) {}
                    throw e
                }
                return true
            }
            false -> return false
            null -> {
                log.warn("[AckRetry] Redis setIfAbsent returned null for user={} seq={}, pushing without dedup", userId, seqId)
                return try {
                    userSessionManager.pushToUser(userId, wsMessage, skipApns = skipApns)
                    true
                } catch (e: Exception) {
                    log.warn("[AckRetry] Push failed (null dedup) for user={} seq={}: {}", userId, seqId, e.message)
                    false
                }
            }
        }
    }

    /**
     * Auth 成功后回放 pending 消息。
     * 遍历该用户的 pending ZSet，逐条通过 pushPendingSafely 去重推送，
     * 确保新连接的设备能收到离线期间堆积的消息。
     */
    fun replayPendingToUser(userId: String) {
        val key = "$PENDING_PREFIX$userId"
        val maxReplayBatch = MAX_REPLAY_ON_AUTH
        val entries = redisTemplate.opsForZSet().range(key, 0, maxReplayBatch - 1) ?: return
        if (entries.size.toLong() >= maxReplayBatch) {
            // 设计内行为：pending 超过单次回放上限时，剩余消息转由后续 sync 补偿，并非错误。
            // 记 INFO 而非 WARN，避免被 Sentry appender 当成告警上报到 GlitchTip。
            log.info("[AckRetry] replayPending for user {} hit auth replay limit {}, remaining deferred to sync", userId, maxReplayBatch)
        }
        var replayed = 0
        var skipped = 0
        for (entryJson in entries) {
            try {
                val data: Map<String, Any?> = objectMapper.readValue(
                    entryJson, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {}
                )
                val seqId = (data["seqId"] as? Number)?.toLong()
                if (seqId == null) {
                    log.warn("[AckRetry] Removing malformed pending entry (missing seqId) for user {}", userId)
                    redisTemplate.opsForZSet().remove(key, entryJson)
                    continue
                }
                val messageJson = data["message"] as? String
                if (messageJson == null) {
                    log.warn("[AckRetry] Removing malformed pending entry (missing message) for user {} seqId {}", userId, seqId)
                    redisTemplate.opsForZSet().remove(key, entryJson)
                    continue
                }
                if (pushPendingSafely(userId, seqId, messageJson)) {
                    replayed++
                } else {
                    skipped++
                }
            } catch (e: Exception) {
                log.warn("Failed to replay pending entry for user {}: {}", userId, e.message)
            }
        }
        if (replayed > 0 || skipped > 0) {
            log.info("[AckRetry] replayPending for user {}: replayed={}, deduped={}, total={}", userId, replayed, skipped, entries.size)
        }
    }

    /**
     * 每 5 秒扫描 pending ACK，对超时未确认的消息进行重推。
     * 使用分布式锁确保多节点下仅一个节点执行扫描，避免线程爆炸。
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    fun scanPendingAcks() {
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(SCAN_LOCK_KEY, nodeId, Duration.ofSeconds(4))
        if (acquired != true) return
        try {
            doScan()
        } catch (e: Exception) {
            log.warn("[AckRetry] scan failed: {}", e.message)
        }
    }

    /**
     * 每 10 分钟扫描 pending key，清理超过 24h 的积压数据并设置 TTL。
     * 用于清理历史遗留的无 TTL pending 数据。
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 30_000)
    fun cleanupStalePendingKeys() {
        val lockKey = "rentmsg:lock:pending-cleanup"
        val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, nodeId, Duration.ofMinutes(9))
        if (acquired != true) return

        try {
            val scanOptions = ScanOptions.scanOptions().match("$PENDING_PREFIX*").count(200).build()
            var cleaned = 0
            var ttlFixed = 0
            var expiredPurged = 0L
            val cutoffMs = System.currentTimeMillis() - PENDING_TTL.toMillis()
            redisTemplate.execute { connection ->
                val cursor = connection.scan(scanOptions)
                cursor.use {
                    while (it.hasNext() && cleaned + ttlFixed < 1000) {
                        val keyBytes = it.next()
                        // 设置 TTL（如果没有）
                        val ttl = connection.keyCommands().ttl(keyBytes)
                        if (ttl != null && ttl < 0) {
                            connection.keyCommands().expire(keyBytes, PENDING_TTL.seconds)
                            ttlFixed++
                        }
                        // 裁剪超出上限的条目
                        val size = connection.zSetCommands().zCard(keyBytes) ?: 0
                        if (size > MAX_PENDING_PER_USER) {
                            connection.zSetCommands().zRemRange(keyBytes, 0, size - MAX_PENDING_PER_USER - 1)
                            cleaned++
                        }
                    }
                }
                null
            }
            // 主动扫描 pending entries 中 timestamp 已超 TTL 的（24h），统计并清理
            // 使用 SCAN cursor 替代危险的 KEYS 命令，避免阻塞 Redis 主线程
            try {
                val expiredScanOptions = ScanOptions.scanOptions().match("$PENDING_PREFIX*").count(200).build()
                redisTemplate.execute { connection ->
                    connection.keyCommands().scan(expiredScanOptions).use { cursor ->
                        var keysWalked = 0
                        while (cursor.hasNext() && keysWalked < MAX_KEYS_PER_SCAN) {
                            keysWalked++
                            val rawKey = cursor.next()
                            val key = String(rawKey)
                            val entries = redisTemplate.opsForZSet().range(key, 0, -1) ?: continue
                            // 先收集需要移除的 entry，再用 pipeline 批量 ZREM
                            val toRemove = mutableListOf<String>()
                            for (entry in entries) {
                                try {
                                    val data: Map<String, Any?> = objectMapper.readValue(entry, object : TypeReference<Map<String, Any?>>() {})
                                    val ts = (data["timestamp"] as? Number)?.toLong() ?: continue
                                    if (ts < cutoffMs) toRemove.add(entry)
                                } catch (_: Exception) {}
                            }
                            if (toRemove.isNotEmpty()) {
                                redisTemplate.executePipelined { conn ->
                                    @Suppress("UNCHECKED_CAST")
                                    val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
                                    @Suppress("UNCHECKED_CAST")
                                    val valueSerializer = redisTemplate.valueSerializer as RedisSerializer<String>
                                    val keyBytes = keySerializer.serialize(key)!!
                                    toRemove.forEach { e ->
                                        conn.zSetCommands().zRem(keyBytes, valueSerializer.serialize(e)!!)
                                    }
                                    null
                                }
                                expiredPurged += toRemove.size
                            }
                        }
                    }
                    null
                }
            } catch (e: Exception) {
                log.warn("[PendingCleanup] expired-scan failed: {}", e.message)
            }
            if (cleaned > 0 || ttlFixed > 0 || expiredPurged > 0) {
                log.info("[PendingCleanup] trimmed={} ttlFixed={} expiredPurged={}", cleaned, ttlFixed, expiredPurged)
            }
            // 埋點 B3：pending 隊列過期清理
            if (expiredPurged > 0) {
                com.layababateam.xinxiwang_backend.extensions.SentryReporter.captureSampled(
                    dedupKey = "im_pending_expired_cleanup",
                    message = "[IM_SYNC] pending expired count=$expiredPurged",
                    level = io.sentry.SentryLevel.WARNING,
                    tags = mapOf("im_event" to "pending_expired"),
                    extras = mapOf(
                        "expiredCount" to expiredPurged,
                        "trimmed" to cleaned,
                        "ttlFixed" to ttlFixed,
                        "ttlHours" to PENDING_TTL.toHours()
                    )
                )
            }
        } catch (e: Exception) {
            log.warn("[PendingCleanup] Failed: {}", e.message)
        }
    }

    @Scheduled(fixedDelay = 600_000L, initialDelay = 45_000L)
    fun cleanupRetryKeysWithoutTtl() {
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(RETRY_CLEANUP_LOCK_KEY, nodeId, Duration.ofMinutes(9))
        if (acquired != true) return

        try {
            val scanOptions = ScanOptions.scanOptions().match("$RETRY_PREFIX*").count(500).build()
            val ttlSeconds = RETRY_COUNTER_TTL.seconds
            var scanned = 0
            var ttlFixed = 0

            redisTemplate.execute { connection ->
                connection.keyCommands().scan(scanOptions).use { cursor ->
                    for (rawKey in cursor) {
                        if (scanned >= MAX_RETRY_KEYS_PER_CLEANUP) break
                        scanned++
                        val ttl = connection.keyCommands().ttl(rawKey)
                        if (ttl == -1L) {
                            connection.keyCommands().expire(rawKey, ttlSeconds)
                            ttlFixed++
                        }
                    }
                }
                null
            }

            if (ttlFixed > 0) {
                log.info("[AckRetry] retry key TTL fixed: scanned={} ttlFixed={} ttl={}s", scanned, ttlFixed, ttlSeconds)
            }
        } catch (e: Exception) {
            log.warn("[AckRetry] retry key cleanup failed: {}", e.message)
        }
    }

    private fun expireRetryKeys(retryKeys: List<String>) {
        if (retryKeys.isEmpty()) return
        try {
            val ttlSeconds = RETRY_COUNTER_TTL.seconds
            redisTemplate.executePipelined { conn ->
                @Suppress("UNCHECKED_CAST")
                val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
                retryKeys.forEach { retryKey ->
                    conn.keyCommands().expire(keySerializer.serialize(retryKey)!!, ttlSeconds)
                }
                null
            }
        } catch (e: Exception) {
            log.warn("[AckRetry] set retry key TTL failed: {}", e.message)
        }
    }

    private fun doScan() {
        val scanOptions = ScanOptions.scanOptions().match("$PENDING_PREFIX*").count(200).build()
        val now = System.currentTimeMillis()
        var keysProcessed = 0

        redisTemplate.execute { connection ->
            connection.keyCommands().scan(scanOptions).use { cursor ->
                for (rawKey in cursor) {
                    if (keysProcessed >= MAX_KEYS_PER_SCAN) break
                    keysProcessed++

                    val key = String(rawKey)
                    val userId = key.removePrefix(PENDING_PREFIX)
                    val entries = redisTemplate.opsForZSet().range(key, 0, -1) ?: continue

                    // 先本地解析，筛选出需要重试的 entries，减少 Redis 往返
                    data class RetryCandidate(
                        val entry: String,
                        val data: Map<String, Any?>,
                        val seqId: Long,
                        val messageJson: String
                    )

                    val candidates = mutableListOf<RetryCandidate>()
                    for (entry in entries) {
                        if (candidates.size >= MAX_RETRIES_PER_USER) break
                        try {
                            val data: Map<String, Any?> = objectMapper.readValue(entry, object : TypeReference<Map<String, Any?>>() {})
                            val timestamp = (data["timestamp"] as? Number)?.toLong() ?: continue
                            val seqId = (data["seqId"] as? Number)?.toLong() ?: continue
                            val messageJson = data["message"] as? String ?: continue
                            if (now - timestamp < ACK_TIMEOUT_MS) continue
                            candidates.add(RetryCandidate(entry, data, seqId, messageJson))
                        } catch (e: Exception) {
                            log.warn("Failed to parse pending ACK entry: {}", e.message)
                        }
                    }

                    if (candidates.isEmpty()) continue

                    // Pipeline 批量 increment retry counts
                    val retryKeys = candidates.map { "$RETRY_PREFIX$userId:${it.seqId}" }
                    val retryCounts = redisTemplate.executePipelined { conn ->
                        @Suppress("UNCHECKED_CAST")
                        val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
                        retryKeys.forEach { retryKey ->
                            conn.stringCommands().incr(keySerializer.serialize(retryKey)!!)
                        }
                        null
                    }
                    expireRetryKeys(retryKeys)

                    // 分类处理：exhausted vs need-retry
                    val toRemoveEntries = mutableListOf<String>()
                    val toRemoveRetryKeys = mutableListOf<String>()
                    val toRetry = mutableListOf<Pair<RetryCandidate, Long>>()

                    candidates.forEachIndexed { index, candidate ->
                        val retryCount = (retryCounts.getOrNull(index) as? Long) ?: 1L
                        if (retryCount > MAX_RETRIES) {
                            toRemoveEntries.add(candidate.entry)
                            toRemoveRetryKeys.add(retryKeys[index])
                            log.info("ACK retry exhausted for user {}, seqId {}. Will be picked up on sync.", userId, candidate.seqId)
                        } else {
                            toRetry.add(candidate to retryCount)
                        }
                    }

                    // Pipeline 批量清理 exhausted entries
                    if (toRemoveEntries.isNotEmpty() || toRetry.isNotEmpty()) {
                        redisTemplate.executePipelined { conn ->
                            @Suppress("UNCHECKED_CAST")
                            val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
                            @Suppress("UNCHECKED_CAST")
                            val valueSerializer = redisTemplate.valueSerializer as RedisSerializer<String>
                            val keyBytes = keySerializer.serialize(key)!!

                            // 删除超限 entries
                            toRemoveEntries.forEach { entry ->
                                conn.zSetCommands().zRem(keyBytes, valueSerializer.serialize(entry)!!)
                            }
                            toRemoveRetryKeys.forEach { retryKey ->
                                conn.keyCommands().del(keySerializer.serialize(retryKey)!!)
                            }

                            // 更新需要重试的 entries（remove old + add updated）
                            toRetry.forEach { (candidate, _) ->
                                conn.zSetCommands().zRem(keyBytes, valueSerializer.serialize(candidate.entry)!!)
                                val updated = objectMapper.writeValueAsString(candidate.data + ("timestamp" to now))
                                conn.zSetCommands().zAdd(keyBytes, candidate.seqId.toDouble(), valueSerializer.serialize(updated)!!)
                            }
                            null
                        }
                    }

                    // 批量推送重试消息（在 pipeline 之外，避免阻塞 Redis 连接）
                    // 使用 pushPendingSafely 去重，防止与 replayPendingToUser 在同一窗口内重复推送
                    toRetry.forEach { (candidate, retryCount) ->
                        try {
                            val pushed = pushPendingSafely(userId, candidate.seqId, candidate.messageJson)
                            if (pushed) {
                                log.debug("Retrying message to user {}, seqId {}, attempt {}", userId, candidate.seqId, retryCount)
                            } else {
                                log.debug("Retry deduped for user {}, seqId {} (already pushed within 60s)", userId, candidate.seqId)
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to push retry for user {}, seqId {}: {}", userId, candidate.seqId, e.message)
                        }
                    }
                }
            }
            null
        }
    }
}
