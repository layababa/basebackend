package com.layababateam.xinxiwang_backend.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

/**
 * Cluster-safe Redis token key deletion.
 *
 * Token keys are not hash-tagged, so deleting a collection can trigger CROSSSLOT
 * errors on Redis cluster mode. Delete each key independently instead.
 */
@Service
class RedisTokenCleanupService(
    private val redisTemplate: StringRedisTemplate,
) {
    private val log = LoggerFactory.getLogger(RedisTokenCleanupService::class.java)

    fun deleteTokenKeys(tokenKeys: Collection<String>, context: String): Long {
        if (tokenKeys.isEmpty()) return 0L
        var removed = 0L
        var failed = 0
        tokenKeys.forEach { key ->
            try {
                if (redisTemplate.delete(key)) removed++
            } catch (e: Exception) {
                failed++
                log.debug("[RedisDelete] Failed to delete key in {}: {}", context, e.message)
            }
        }
        if (failed > 0) {
            log.warn(
                "[RedisDelete] Partial failure in {}: requested={}, removed={}, failed={}",
                context,
                tokenKeys.size,
                removed,
                failed,
            )
        } else {
            log.info("[RedisDelete] {} requested={} removed={}", context, tokenKeys.size, removed)
        }
        return removed
    }

    fun deleteByTokens(tokens: Collection<String>, context: String): Long =
        deleteTokenKeys(tokens.map { "rentmsg:tokens:$it" }, context)
}
