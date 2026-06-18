package com.layababateam.xinxiwang_backend.config

import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class SeqIdInitializer(
    private val mongoTemplate: MongoTemplate,
    private val redisTemplate: StringRedisTemplate,
    private val stagingSafetyPolicy: StagingSafetyPolicy,
) : SeqIdInitializationPort {
    private val log = LoggerFactory.getLogger(SeqIdInitializer::class.java)

    companion object {
        private const val SEQ_KEY_PREFIX = "xinxiwang:seq:"
    }

    override fun syncSeqIds() {
        if (stagingSafetyPolicy.writeProtectionEnabled) {
            log.warn("[SeqIdInit] Skipped while staging write protection is enabled")
            return
        }
        try {
            val maxSeqIds = queryMaxSeqIds()
            if (maxSeqIds.isEmpty()) {
                log.info("[SeqIdInit] No messages found, skipping seqId sync")
                return
            }

            var updated = 0
            var skipped = 0

            for ((conversationId, dbMaxSeqId) in maxSeqIds) {
                val key = "$SEQ_KEY_PREFIX$conversationId"
                val currentRedisValue = redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: 0L

                if (dbMaxSeqId > currentRedisValue) {
                    redisTemplate.opsForValue().set(key, dbMaxSeqId.toString())
                    updated++
                } else {
                    skipped++
                }
            }

            log.info(
                "[SeqIdInit] SeqId sync complete: {} updated, {} skipped (Redis already up-to-date), {} conversations total",
                updated,
                skipped,
                maxSeqIds.size,
            )
        } catch (e: Exception) {
            log.error("[SeqIdInit] Failed to sync seqIds from MongoDB to Redis: {}", e.message, e)
        }
    }

    private fun queryMaxSeqIds(): Map<String, Long> {
        val pipeline = listOf(
            Document(
                "\$group",
                Document("_id", "\$conversationId")
                    .append("maxSeqId", Document("\$max", "\$seqId")),
            ),
        )

        val results = mongoTemplate.getCollection("messages")
            .aggregate(pipeline)
            .toList()

        return results.associate { doc ->
            val conversationId = doc.getString("_id")
            val maxSeqId = (doc.get("maxSeqId") as? Number)?.toLong() ?: 0L
            conversationId to maxSeqId
        }
    }
}
