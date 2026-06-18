package com.layababateam.xinxiwang_backend.config

import com.layababateam.xinxiwang_backend.service.RequestMetadataService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

@Component
class RateLimitFilter(
    private val redisTemplate: StringRedisTemplate,
    private val stagingSafetyPolicy: StagingSafetyPolicy,
    private val requestMetadataService: RequestMetadataService,
) : OncePerRequestFilter() {

    companion object {
        private val BYPASS_PATHS = setOf(
            "/api/auth/login",
        )
        private val RATE_LIMITS = mapOf(
            "/api/user/search" to RateConfig(limit = 20, window = Duration.ofMinutes(1)),
            "/api/upload" to RateConfig(limit = 30, window = Duration.ofMinutes(1)),
            "/api/v1/stickers/upload" to RateConfig(limit = 10, window = Duration.ofMinutes(1)),
        )
        private val DEFAULT_RATE = RateConfig(limit = 100, window = Duration.ofMinutes(1))
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (stagingSafetyPolicy.writeProtectionEnabled) {
            filterChain.doFilter(request, response)
            return
        }

        val uri = request.requestURI
        val path = request.servletPath?.takeIf { it.isNotBlank() }
            ?: uri.removePrefix(request.contextPath ?: "")
        if (BYPASS_PATHS.any { path.startsWith(it) }) {
            filterChain.doFilter(request, response)
            return
        }
        val config = RATE_LIMITS.entries.find { uri.startsWith(it.key) }?.value ?: DEFAULT_RATE

        val clientKey = extractClientKey(request)
        val redisKey = "ratelimit:$clientKey:$uri"

        val current = redisTemplate.opsForValue().increment(redisKey) ?: 1L
        if (current == 1L) {
            redisTemplate.expire(redisKey, config.window)
        }

        response.setHeader("X-RateLimit-Limit", config.limit.toString())
        response.setHeader("X-RateLimit-Remaining", maxOf(0, config.limit - current.toInt()).toString())

        if (current > config.limit) {
            response.status = 429
            response.contentType = "application/json"
            response.writer.write("""{"success":false,"message":"请求过于频繁，请稍后再试"}""")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun extractClientKey(request: HttpServletRequest): String {
        val token = request.getHeader("Authorization")
        if (token != null && token.startsWith("Bearer ")) {
            return "user:${token.hashCode()}"
        }
        val metadata = requestMetadataService.from(request)
        return "ip:${metadata.clientIp ?: request.remoteAddr}"
    }

    data class RateConfig(val limit: Int, val window: Duration)
}
