package com.layababateam.xinxiwang_backend.config

import io.sentry.protocol.User
import io.sentry.spring.jakarta.SentryUserProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Configuration
class SentryConfig {

    @Bean
    fun sentryUserProvider(): SentryUserProvider {
        return SentryUserProvider {
            try {
                val attrs = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
                val request = attrs?.request ?: return@SentryUserProvider null
                val userId = request.getAttribute("userId") as? String
                User().apply {
                    id = userId
                    ipAddress = request.getHeader("X-Forwarded-For") ?: request.remoteAddr
                    data = mapOf(
                        "userAgent" to (request.getHeader("User-Agent") ?: "unknown"),
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
