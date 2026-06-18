package com.layababateam.xinxiwang_backend.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

@Service
class GroupV3SyncRateLimiter(
    private val redisTemplate: StringRedisTemplate,
) {
    private val deviceSemaphores = ConcurrentHashMap<String, Semaphore>()

    fun <T> withDeviceLimit(userId: String, deviceId: String?, block: () -> T): T? {
        val key = "$userId:${deviceId ?: "unknown"}"
        val semaphore = deviceSemaphores.computeIfAbsent(key) { Semaphore(1) }
        if (!semaphore.tryAcquire()) return null
        return try {
            block()
        } finally {
            semaphore.release()
        }
    }

    fun allow(userId: String, groupId: String): Boolean =
        increment("xinxiwang:v3sync:user:$userId", USER_LIMIT, Duration.ofSeconds(1)) &&
            increment("xinxiwang:v3sync:group:$groupId", GROUP_LIMIT, Duration.ofSeconds(1))

    private fun increment(key: String, limit: Int, window: Duration): Boolean {
        val current = redisTemplate.opsForValue().increment(key) ?: 1L
        if (current == 1L) redisTemplate.expire(key, window)
        return current <= limit
    }

    companion object {
        private const val USER_LIMIT = 5
        private const val GROUP_LIMIT = 200
    }
}
