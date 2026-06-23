package com.layababateam.xinxiwang_backend.config

import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceEntryRepository
import com.layababateam.xinxiwang_backend.service.WebCustomerServiceRules
import com.layababateam.xinxiwang_backend.service.WebCustomerServiceService
import com.layababateam.xinxiwang_backend.service.WebCustomerServiceTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import kotlin.jvm.optionals.getOrNull

@Component
class WebCustomerServiceCorsFilter(
    private val entryRepository: WebCustomerServiceEntryRepository,
    private val tokenService: WebCustomerServiceTokenService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = request.servletPath?.takeIf { it.isNotBlank() }
            ?: request.requestURI.removePrefix(request.contextPath ?: "")
        if (!path.startsWith("/api/web-customer-service/public/")) {
            filterChain.doFilter(request, response)
            return
        }

        val origin = request.getHeader("Origin")
        if (!origin.isNullOrBlank() && (request.method.equals("OPTIONS", ignoreCase = true) || isAllowedActualOrigin(path, origin, request))) {
            response.setHeader("Access-Control-Allow-Origin", origin)
            response.setHeader("Vary", "Origin")
        }
        response.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-WCS-Visitor-Token")
        response.setHeader("Access-Control-Max-Age", "600")

        if (request.method.equals("OPTIONS", ignoreCase = true)) {
            response.status = HttpServletResponse.SC_OK
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun isAllowedActualOrigin(path: String, origin: String, request: HttpServletRequest): Boolean {
        val entryId = entryIdFromEntryPath(path) ?: entryIdFromVisitorToken(request) ?: return false
        val entry = entryRepository.findById(entryId).getOrNull() ?: return false
        return entry.enabled && WebCustomerServiceRules.isOriginAllowed(origin, entry.allowedDomains)
    }

    private fun entryIdFromEntryPath(path: String): String? {
        val marker = "/api/web-customer-service/public/entries/"
        if (!path.startsWith(marker)) return null
        return path.removePrefix(marker).substringBefore('/').takeIf { it.isNotBlank() }
    }

    private fun entryIdFromVisitorToken(request: HttpServletRequest): String? =
        runCatching {
            tokenService.verify(request.getHeader(WebCustomerServiceService.VISITOR_TOKEN_HEADER)).entryId
        }.getOrNull()
}
