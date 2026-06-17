package com.layababateam.xinxiwang_backend.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class CacheControlFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        filterChain.doFilter(request, response)

        if (request.method != HTTP_GET) return

        response.setHeader(CACHE_CONTROL_HEADER, cacheControlValue(request.requestURI))
    }

    private fun cacheControlValue(uri: String): String =
        when {
            uri.contains("/api/user/") -> "private, max-age=60"
            uri.contains("/api/v1/stickers") -> "private, max-age=300"
            uri.contains("/api/upload") -> "no-store"
            else -> "no-cache"
        }

    private companion object {
        const val HTTP_GET = "GET"
        const val CACHE_CONTROL_HEADER = "Cache-Control"
    }
}
