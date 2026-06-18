package com.layababateam.xinxiwang_backend.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

data class LockHandle(val key: String, val value: String)

@Service
class DistributedLockService(
    private val redisTemplate: StringRedisTemplate,
) : DistributedLockPort {
    private companion object {
        const val LOCK_PREFIX = "xinxiwang:lock:"
    }

    /** 原子 compare-and-delete，避免锁过期重入后误删其他请求持有的锁。 */
    private val unlockScript = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            if redis.call("GET", KEYS[1]) == ARGV[1] then
                return redis.call("DEL", KEYS[1])
            else
                return 0
            end
            """.trimIndent(),
        )
        resultType = Long::class.java
    }

    fun tryLock(key: String, timeout: Duration = Duration.ofSeconds(10)): LockHandle? {
        val lockKey = "$LOCK_PREFIX$key"
        val lockValue = UUID.randomUUID().toString()
        val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, timeout)
        return if (acquired == true) LockHandle(lockKey, lockValue) else null
    }

    fun unlock(handle: LockHandle) {
        redisTemplate.execute(unlockScript, listOf(handle.key), handle.value)
    }

    fun <T> withLock(key: String, timeout: Duration = Duration.ofSeconds(10), action: () -> T): T {
        val handle = tryLock(key, timeout)
            ?: throw IllegalStateException("操作频繁，请稍后重试")
        return try {
            action()
        } finally {
            unlock(handle)
        }
    }

    override fun <T> withLock(key: String, action: () -> T): T {
        return withLock(key, Duration.ofSeconds(10), action)
    }
}
