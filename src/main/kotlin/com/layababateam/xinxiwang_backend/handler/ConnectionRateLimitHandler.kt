package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.ConnectionRateLimitBypassPort
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.time.Duration

@Sharable
@Component
class ConnectionRateLimitHandler(
    @Value("\${xinxiwang.ratelimit.connections-per-second:200}") private val maxPerSecond: Int,
    private val redisTemplate: StringRedisTemplate,
    private val bypassPortProvider: ObjectProvider<ConnectionRateLimitBypassPort>,
) : ChannelInboundHandlerAdapter() {
    private val log = LoggerFactory.getLogger(ConnectionRateLimitHandler::class.java)

    override fun channelActive(ctx: ChannelHandlerContext) {
        if (bypassPortProvider.ifAvailable()?.shouldBypassConnectionRateLimit() == true) {
            super.channelActive(ctx)
            return
        }

        val ip = (ctx.channel().remoteAddress() as? InetSocketAddress)?.address?.hostAddress ?: "unknown"
        try {
            val count = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                listOf("$KEY_PREFIX$ip"),
                WINDOW.toMillis().toString(),
            ) ?: 1L

            if (count > maxPerSecond) {
                log.warn("Connection rate limit exceeded for IP {}, rejecting", ip)
                ctx.close()
                return
            }
        } catch (e: Exception) {
            log.warn("Redis rate-limit check failed for IP {}, allowing: {}", ip, e.message)
        }

        super.channelActive(ctx)
    }

    private fun <T : Any> ObjectProvider<T>.ifAvailable(): T? = try {
        ifAvailable
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val KEY_PREFIX = "xinxiwang:ratelimit:conn:"
        val WINDOW: Duration = Duration.ofSeconds(1)

        /**
         * 原子执行 INCR 和过期时间设置，避免服务异常时留下永久限流 key。
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
