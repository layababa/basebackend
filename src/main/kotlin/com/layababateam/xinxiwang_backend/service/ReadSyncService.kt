package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.repository.UserConversationRepository
import com.layababateam.xinxiwang_backend.service.cache.ConversationCacheService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class ReadSyncService(
    private val redisTemplate: StringRedisTemplate,
    private val userConversationRepository: UserConversationRepository,
    private val conversationRepository: ConversationRepository,
    private val userSessionManager: UserSessionManager,
    private val objectMapper: ObjectMapper,
    private val readPointFlushService: ReadPointFlushService,
    private val conversationCacheService: ConversationCacheService,
    private val messageBatchService: MessageBatchService
) {
    private val log = LoggerFactory.getLogger(ReadSyncService::class.java)

    companion object {
        private const val DEBOUNCE_PREFIX = "rentmsg:debounce:readpush:"
        private const val READ_SNAPSHOT_PREFIX = "rentmsg:group:read:snapshot:"
        private val DEBOUNCE_TTL = Duration.ofSeconds(3)
        private const val PUSH_DELAY_MS = 3000L
        private const val MAX_BATCH_READ_POINTS = 50
        /** 群组已读位点 HASH TTL，7 天无活动后自然淘汰，防止无界增长撑爆 Redis */
        private val GROUP_READ_TTL: Duration = Duration.ofDays(7)
    }

    private val scheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("read-sync-")
        initialize()
    }

    /**
     * 批次更新已读位点（用于客户端重连后批量同步）。
     * 上限 [MAX_BATCH_READ_POINTS] 个会话。
     *
     * @param fallbackDeviceId 当 point 未带 deviceId 时的兜底值（通常来自 WS session）
     */
    fun batchUpdateReadPoints(
        userId: String,
        points: List<Map<String, Any?>>,
        fallbackDeviceId: String? = null
    ) {
        val limited = points.take(MAX_BATCH_READ_POINTS)
        val unreadKeysToDelete = mutableListOf<String>()
        for (point in limited) {
            val convId = point["conversationId"] as? String ?: continue
            val seqId = (point["seqId"] as? Number)?.toLong() ?: continue
            if (convId.isBlank() || seqId <= 0) continue

            // Per-device: 优先 point 内 deviceId，回退 fallback（整批共用）
            val deviceId = (point["deviceId"] as? String)?.takeIf { it.isNotEmpty() }
                ?: fallbackDeviceId

            // Delegate to Redis buffer instead of direct MongoDB write
            readPointFlushService.bufferReadPoint(userId, convId, seqId, deviceId)
            unreadKeysToDelete.add("rentmsg:unread:$userId:$convId")
        }
        deleteUnreadKeys(unreadKeysToDelete)
        log.debug("Batch buffered {} read points for user {} (fallbackDevice={})", limited.size, userId, fallbackDeviceId)
    }

    private fun deleteUnreadKeys(keys: Collection<String>) {
        val distinctKeys = keys.distinct()
        if (distinctKeys.isEmpty()) return
        try {
            @Suppress("UNCHECKED_CAST")
            val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
            redisTemplate.executePipelined { connection ->
                distinctKeys.forEach { key ->
                    connection.keyCommands().del(keySerializer.serialize(key)!!)
                }
                null
            }
        } catch (e: Exception) {
            log.debug("Pipeline unread delete failed, falling back to collection delete: {}", e.message)
            try {
                redisTemplate.delete(distinctKeys)
            } catch (ex: Exception) {
                log.warn("Failed to delete unread keys in batch: {}", ex.message)
            }
        }
    }

    fun onGroupReadPointUpdate(convId: String, userId: String, seqId: Long) {
        val groupReadKey = "rentmsg:group:read:$convId"
        redisTemplate.opsForHash<String, String>().put(groupReadKey, userId, seqId.toString())
        // 防止群组已读位点 HASH 无限增长：每次更新顺延 7 天 TTL
        redisTemplate.expire(groupReadKey, GROUP_READ_TTL)

        // Delegate to Redis buffer instead of direct MongoDB write
        readPointFlushService.bufferReadPoint(userId, convId, seqId)

        val acquired = redisTemplate.opsForValue()
            .setIfAbsent("$DEBOUNCE_PREFIX$convId", "1", DEBOUNCE_TTL) ?: false
        if (!acquired) return

        scheduler.schedule({
            try {
                val readMap = redisTemplate.opsForHash<String, String>().entries("rentmsg:group:read:$convId")
                val conv = conversationCacheService.getConversation(convId) ?: return@schedule
                val snapshot = readMap.entries
                    .sortedBy { it.key }
                    .joinToString("|") { "${it.key}:${it.value}" }
                val snapshotKey = "$READ_SNAPSHOT_PREFIX$convId"
                if (redisTemplate.opsForValue().get(snapshotKey) == snapshot) {
                    return@schedule
                }
                redisTemplate.opsForValue().set(snapshotKey, snapshot, GROUP_READ_TTL)

                val payload = objectMapper.writeValueAsString(mapOf(
                    "type" to "group_read_update",
                    "data" to mapOf(
                        "conversationId" to convId,
                        "readMap" to readMap.mapValues { it.value.toLongOrNull() ?: 0L }
                    )
                ))
                val onlineMembers = conv.members.filter { userSessionManager.isOnlineGlobally(it) }
                if (onlineMembers.isEmpty()) return@schedule
                if (conv.members.size > 10) {
                    onlineMembers.forEach { messageBatchService.pushBatched(it, payload) }
                } else {
                    onlineMembers.forEach { userSessionManager.pushToUser(it, payload, messageType = "group_read_update") }
                }
            } catch (e: Exception) {
                log.warn("Failed to push group read update for {}: {}", convId, e.message)
            }
        }, Instant.now().plusMillis(PUSH_DELAY_MS))
    }
}
