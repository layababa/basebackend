package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.repository.UserConversationRepository
import com.layababateam.xinxiwang_backend.service.cache.UserConversationCacheService
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ReadPointFlushService(
    private val redisTemplate: StringRedisTemplate,
    private val userConversationRepository: UserConversationRepository,
    private val mongoTemplate: MongoTemplate,
    private val distributedLockService: DistributedLockService,
    private val userConversationCacheService: UserConversationCacheService
) {
    private val log = LoggerFactory.getLogger(ReadPointFlushService::class.java)

    companion object {
        /**
         * v2 prefix：per-device 改造后 buffer 结构从 String 变为 Hash。
         * 老 prefix 数据按 1h TTL 自然消亡，不做主动清理（迁移期最多丢失 5s 未 flush 的已读点，影响极小）。
         * Hash Tag {rentmsg:readbuf:v2} 确保 cluster 场景下同 slot。
         */
        private const val BUFFER_PREFIX = "{rentmsg:readbuf:v2}:"
        private const val BUFFER_INDEX_KEY = "{rentmsg:readbuf:v2}:index"
        private const val FIELD_ACCOUNT = "account"
        private const val FIELD_DEVICE_PREFIX = "dev:"
    }

    /**
     * Lua 脚本：Hash field 级 CAS，只在新 seqId 更大时才 HSET。
     * KEYS[1]=bufferKey(hash), KEYS[2]=indexKey(set)
     * ARGV[1]=fieldName, ARGV[2]=newSeqId, ARGV[3]=indexMember
     */
    private val bufferScript = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            local current = redis.call("HGET", KEYS[1], ARGV[1])
            if current == false or tonumber(ARGV[2]) > tonumber(current) then
                redis.call("HSET", KEYS[1], ARGV[1], ARGV[2])
                redis.call("SADD", KEYS[2], ARGV[3])
                redis.call("EXPIRE", KEYS[1], 3600)
                redis.call("EXPIRE", KEYS[2], 3600)
                return 1
            end
            return 0
            """.trimIndent()
        )
        resultType = Long::class.java
    }

    /** Lua：原子 HGETALL + DEL，避免 flush 期间 race 丢数据 */
    private val drainScript = DefaultRedisScript<List<String>>().apply {
        setScriptText(
            """
            local data = redis.call("HGETALL", KEYS[1])
            redis.call("DEL", KEYS[1])
            return data
            """.trimIndent()
        )
        @Suppress("UNCHECKED_CAST")
        resultType = List::class.java as Class<List<String>>
    }

    /**
     * Buffer a read point update in Redis instead of writing to MongoDB directly.
     *
     * @param deviceId 新客户端必传（从 WS session 或 payload 取），旧客户端为 null
     *                 null 时仅更新 account field（UserConversation.readSeqId 老语义）
     *                 非 null 时同时更新 account + dev:{deviceId}（device_read_points 新语义）
     */
    fun bufferReadPoint(userId: String, convId: String, seqId: Long, deviceId: String? = null) {
        val bufferKey = "$BUFFER_PREFIX$userId:$convId"
        val memberValue = "$userId:$convId"
        // 始终更新 account field（保持老逻辑兼容）
        redisTemplate.execute(
            bufferScript,
            listOf(bufferKey, BUFFER_INDEX_KEY),
            FIELD_ACCOUNT, seqId.toString(), memberValue
        )
        // 有 deviceId 则额外更新 per-device field
        if (!deviceId.isNullOrEmpty()) {
            redisTemplate.execute(
                bufferScript,
                listOf(bufferKey, BUFFER_INDEX_KEY),
                "$FIELD_DEVICE_PREFIX$deviceId", seqId.toString(), memberValue
            )
        }
    }

    /**
     * Flush buffered read points to MongoDB every 5 seconds.
     * 使用 MongoTemplate bulkOps 批量更新，取代逐一 find + save 的 O(N) 操作。
     */
    @Scheduled(fixedDelay = 5000)
    fun flushReadPoints() {
        val handle = distributedLockService.tryLock("readpoint:flush", Duration.ofSeconds(4))
            ?: return

        try {
            doFlush()
        } finally {
            distributedLockService.unlock(handle)
        }
    }

    private fun doFlush() {
        val entries = redisTemplate.opsForSet().members(BUFFER_INDEX_KEY)
        if (entries.isNullOrEmpty()) return

        val now = System.currentTimeMillis()
        val processedKeys = mutableListOf<String>()
        val affectedUserIds = mutableSetOf<String>()
        val ucBulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "user_conversations")
        val drpBulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "device_read_points")

        var hasUcOps = false
        var hasDrpOps = false

        for (entry in entries) {
            try {
                val parts = entry.split(":", limit = 2)
                if (parts.size != 2) continue
                val userId = parts[0]
                val convId = parts[1]

                val bufferKey = "$BUFFER_PREFIX$entry"
                // 原子 HGETALL + DEL，结果为 [field1, value1, field2, value2, ...]
                val rawFields = redisTemplate.execute(drainScript, listOf(bufferKey))
                    ?: continue
                if (rawFields.isEmpty()) continue

                // 解析 hash fields
                val fieldMap = mutableMapOf<String, Long>()
                var i = 0
                while (i + 1 < rawFields.size) {
                    val fieldName = rawFields[i]
                    val seqId = rawFields[i + 1].toLongOrNull()
                    if (seqId != null) fieldMap[fieldName] = seqId
                    i += 2
                }
                if (fieldMap.isEmpty()) continue

                // account field → user_conversations（只增不减）
                fieldMap[FIELD_ACCOUNT]?.let { accountSeq ->
                    val query = Query(
                        Criteria.where("userId").`is`(userId)
                            .and("conversationId").`is`(convId)
                            .and("readSeqId").lt(accountSeq)
                    )
                    val update = Update()
                        .set("readSeqId", accountSeq)
                        .set("lastReadTime", now)
                    ucBulk.updateOne(query, update)
                    hasUcOps = true
                    affectedUserIds.add(userId)
                }

                // dev:* fields → device_read_points（upsert，只增不减）
                for ((fieldName, devSeq) in fieldMap) {
                    if (!fieldName.startsWith(FIELD_DEVICE_PREFIX)) continue
                    val deviceId = fieldName.substring(FIELD_DEVICE_PREFIX.length)
                    if (deviceId.isEmpty()) continue
                    val query = Query(
                        Criteria.where("userId").`is`(userId)
                            .and("deviceId").`is`(deviceId)
                            .and("conversationId").`is`(convId)
                    )
                    val update = Update()
                        .max("readSeqId", devSeq)
                        .set("updatedAt", now)
                        .setOnInsert("userId", userId)
                        .setOnInsert("deviceId", deviceId)
                        .setOnInsert("conversationId", convId)
                    drpBulk.upsert(query, update)
                    hasDrpOps = true
                }

                processedKeys.add(entry)
            } catch (e: Exception) {
                log.error("Failed to prepare read point flush for entry=$entry", e)
            }
        }

        // 两个 bulkOps 并发提交：墙钟时间为 max(t_uc, t_drp) 而非 t_uc + t_drp
        if (hasUcOps || hasDrpOps) {
            val ucThread = if (hasUcOps) Thread.ofVirtual().unstarted {
                try {
                    val result = ucBulk.execute()
                    log.debug("UC bulk flushed: {} modified", result.modifiedCount)
                } catch (e: Exception) {
                    log.error("UC bulk flush failed", e)
                }
            } else null
            val drpThread = if (hasDrpOps) Thread.ofVirtual().unstarted {
                try {
                    val result = drpBulk.execute()
                    log.debug("DRP bulk flushed: {} upserted/{} modified", result.upserts.size, result.modifiedCount)
                } catch (e: Exception) {
                    log.error("DRP bulk flush failed", e)
                }
            } else null
            ucThread?.start()
            drpThread?.start()
            ucThread?.join()
            drpThread?.join()
        }

        if (processedKeys.isNotEmpty()) {
            redisTemplate.opsForSet().remove(BUFFER_INDEX_KEY, *processedKeys.toTypedArray())
        }

        if (affectedUserIds.isNotEmpty()) {
            userConversationCacheService.invalidateAll(affectedUserIds)
        }
    }
}
