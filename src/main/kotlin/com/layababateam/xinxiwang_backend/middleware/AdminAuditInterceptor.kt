package com.layababateam.xinxiwang_backend.middleware

import com.layababateam.xinxiwang_backend.service.AdminHttpAuditEvent
import com.layababateam.xinxiwang_backend.service.AuditLogPort
import com.layababateam.xinxiwang_backend.service.RequestMetadataService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AdminAuditInterceptor(
    private val auditLogPort: AuditLogPort,
    private val requestMetadataService: RequestMetadataService,
) : HandlerInterceptor {
    companion object {
        private const val START_TIME_ATTR = "adminAuditStartTime"
        private val WRITE_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        private val SENSITIVE_GET_PATTERNS = listOf(
            Regex("^/api/admin/users/?$"),
            Regex("^/api/admin/users/[^/]+$"),
            Regex("^/api/admin/users/[^/]+/(password|sessions)$"),
            Regex("^/api/admin/users/[^/]+/wallet(/transactions(/export)?)?$"),
            Regex("^/api/admin/admins/?$"),
            Regex("^/api/admin/dashboard/audit-logs$"),
            Regex("^/api/admin/security(/.*)?$"),
            Regex("^/api/admin/debug-log(/.*)?$"),
            Regex("^/api/admin/transactions(/.*)?$"),
            Regex("^/api/admin/withdrawals(/.*)?$"),
        )
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val path = request.requestURI.removePrefix(request.contextPath ?: "").substringBefore("?")
        if (path.startsWith("/api/admin/auth/")) return
        if (request.getAttribute(AuditLogPort.EXPLICIT_AUDIT_ATTR) == true) return

        val method = request.method.uppercase()
        val eventType = when {
            method in WRITE_METHODS -> "CHANGE"
            method == "GET" && SENSITIVE_GET_PATTERNS.any { it.matches(path) } -> "QUERY"
            else -> return
        }

        val adminId = request.getAttribute(AdminAuthInterceptor.ADMIN_ID_ATTR) as? String ?: return
        val adminUsername = request.getAttribute(AdminAuthInterceptor.ADMIN_USERNAME_ATTR) as? String ?: ""
        val startedAt = request.getAttribute(START_TIME_ATTR) as? Long ?: System.currentTimeMillis()
        val target = targetFrom(path)

        auditLogPort.recordHttpAudit(
            AdminHttpAuditEvent(
                adminId = adminId,
                adminUsername = adminUsername,
                eventType = eventType,
                action = "HTTP_${eventType}_${target.type}",
                targetType = target.type,
                targetId = target.id,
                details = "$method $path",
                method = method,
                path = path,
                status = response.status,
                durationMs = System.currentTimeMillis() - startedAt,
                metadata = requestMetadataService.from(request),
            ),
        )
    }

    private fun targetFrom(path: String): AuditTarget {
        val parts = path.removePrefix("/api/admin/").split("/").filter { it.isNotBlank() }
        val resource = parts.firstOrNull()?.uppercase()?.replace("-", "_") ?: "ADMIN_API"
        val targetId = parts.drop(1).firstOrNull { it.length >= 8 && !it.contains("-fail-count") }
        return AuditTarget(resource, targetId)
    }

    private data class AuditTarget(val type: String, val id: String?)
}
