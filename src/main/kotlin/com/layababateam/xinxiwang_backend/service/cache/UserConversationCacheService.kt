package com.layababateam.xinxiwang_backend.service.cache

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class UserConversationCacheService(
    private val redisTemplate: StringRedisTemplate,
) {
    companion object {
        private const val KEY_PREFIX = "xinxiwang:uc_list:"
    }

    fun invalidate(userId: String) {
        deleteQuietly("$KEY_PREFIX$userId")
    }

    /**
     * Delete keys one by one so Redis Cluster never receives a cross-slot multi-key DEL.
     */
    fun invalidateAll(userIds: Collection<String>) {
        userIds.forEach { userId -> deleteQuietly("$KEY_PREFIX$userId") }
    }

    private fun deleteQuietly(key: String) {
        try {
            redisTemplate.delete(key)
        } catch (_: Exception) {
            // Cache invalidation is best effort; callers must not fail writes because Redis skipped a delete.
        }
    }
}
