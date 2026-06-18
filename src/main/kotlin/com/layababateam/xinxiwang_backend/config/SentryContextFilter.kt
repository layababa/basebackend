package com.layababateam.xinxiwang_backend.config

import com.layababateam.xinxiwang_backend.middleware.AdminAuthInterceptor
import com.layababateam.xinxiwang_backend.middleware.ClientAuthInterceptor
import com.layababateam.xinxiwang_backend.service.RequestMetadataRules
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.protocol.User
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class SentryContextFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startTime = System.currentTimeMillis()

        Sentry.configureScope { scope ->
            scope.setTag("request.method", request.method)
            scope.setTag("request.uri", request.requestURI)
            scope.setTag("request.ip", resolveClientIp(request))

            val traceId = MDC.get("traceId")
            if (traceId != null) {
                scope.setTag("traceId", traceId)
                scope.setExtra("traceId", traceId)
            }

            request.getHeader("User-Agent")?.let { scope.setExtra("userAgent", it) }
            request.queryString?.takeIf { it.isNotBlank() }?.let { scope.setExtra("queryString", it) }
            request.getHeader("X-App-Version")?.let { scope.setTag("app.version", it) }
            request.getHeader("X-Device-Id")?.let { scope.setTag("device.id", it) }
        }

        Sentry.addBreadcrumb(Breadcrumb().apply {
            message = "${request.method} ${request.requestURI}"
            category = "http"
            level = SentryLevel.INFO
        })

        try {
            filterChain.doFilter(request, response)
        } finally {
            val userId = request.getAttribute(ClientAuthInterceptor.USER_ID_ATTR) as? String
            val adminId = request.getAttribute(AdminAuthInterceptor.ADMIN_ID_ATTR) as? String
            val adminUsername = request.getAttribute(AdminAuthInterceptor.ADMIN_USERNAME_ATTR) as? String

            Sentry.configureScope { scope ->
                if (userId != null) {
                    scope.user = User().apply { id = userId }
                    scope.setTag("userId", userId)
                } else if (adminId != null) {
                    scope.user = User().apply {
                        id = adminId
                        username = adminUsername
                    }
                    scope.setTag("adminId", adminId)
                    val adminRole = request.getAttribute(AdminAuthInterceptor.ADMIN_ROLE_ATTR) as? String
                    if (adminRole != null) {
                        scope.setTag("adminRole", adminRole)
                    }
                }

                scope.setTag("response.status", response.status.toString())

                val duration = System.currentTimeMillis() - startTime
                scope.setExtra("request.duration_ms", duration.toString())
            }

            val duration = System.currentTimeMillis() - startTime
            Sentry.addBreadcrumb(Breadcrumb().apply {
                message = "${request.method} ${request.requestURI} -> ${response.status} (${duration}ms)"
                category = "http"
                level = if (response.status >= 500) SentryLevel.ERROR else SentryLevel.INFO
            })

            Sentry.configureScope { it.clear() }
        }
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        return RequestMetadataRules.clientIp(
            forwardedFor = request.getHeader("X-Forwarded-For"),
            realIp = request.getHeader("X-Real-IP"),
            remoteAddr = request.remoteAddr,
        ).orEmpty()
    }
}
