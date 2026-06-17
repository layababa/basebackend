package com.layababateam.xinxiwang_backend.handler

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class MessageRateLimitHandler(
    private val redisTemplate: StringRedisTemplate,
) {
    private val log = LoggerFactory.getLogger(MessageRateLimitHandler::class.java)

    fun checkRateLimit(userId: String, isGroup: Boolean): Boolean {
        val key = "xinxiwang:ratelimit:msg:$userId"
        val limit = if (isGroup) GROUP_LIMIT else PRIVATE_LIMIT
        val count = redisTemplate.execute(
            RATE_LIMIT_SCRIPT,
            listOf(key),
            WINDOW.toMillis().toString(),
        ) ?: 1L

        if (count > limit) {
            log.debug("Rate limit exceeded for user {}: {}/{} per second", userId, count, limit)
            return false
        }
        return true
    }

    private companion object {
        const val PRIVATE_LIMIT = 5
        const val GROUP_LIMIT = 3
        val WINDOW: Duration = Duration.ofSeconds(1)

        /**
         * 原子执行 INCR 和过期时间设置，避免服务在两条 Redis 命令之间异常时留下永久 key。
         */
        val RATE_LIMIT_SCRIPT: RedisScript<Long> = RedisScript.of(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """.trimIndent(),
            Long::class.java,
        )
    }
}
