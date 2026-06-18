package com.layababateam.xinxiwang_backend.middleware

import com.layababateam.xinxiwang_backend.service.AdminRequestAuthPort
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AdminAuthInterceptor(
    private val adminRequestAuthPort: AdminRequestAuthPort,
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(AdminAuthInterceptor::class.java)

    companion object {
        const val ADMIN_ID_ATTR = "adminId"
        const val ADMIN_ROLE_ATTR = "adminRole"
        const val ADMIN_USERNAME_ATTR = "adminUsername"

        private val ROLE_HIERARCHY = mapOf(
            "SUPER_ADMIN" to 3,
            "ADMIN" to 2,
            "MODERATOR" to 1,
        )

        fun checkPermission(role: String, requiredRole: String): Boolean {
            val roleLevel = ROLE_HIERARCHY[role] ?: 0
            val requiredLevel = ROLE_HIERARCHY[requiredRole] ?: 0
            return roleLevel >= requiredLevel
        }
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val path = request.servletPath?.takeIf { it.isNotBlank() }
            ?: request.requestURI.removePrefix(request.contextPath ?: "")
        if (path.startsWith("/api/admin/auth/") || path == "/api/admin/login") return true

        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn(
                "Admin auth failed: ip={}, path={}, reason={}",
                request.remoteAddr,
                path,
                "Missing or invalid Authorization header",
            )
            return sendUnauthorized(response)
        }

        val context = try {
            adminRequestAuthPort.authenticateAdminRequest(authHeader.substring(7))
        } catch (e: Exception) {
            log.warn(
                "Admin auth failed: ip={}, path={}, reason={}",
                request.remoteAddr,
                path,
                "Token validation exception: ${e.message}",
            )
            return sendUnauthorized(response)
        }

        if (context == null) {
            log.warn("Admin auth failed: ip={}, path={}, reason={}", request.remoteAddr, path, "Invalid admin token")
            return sendUnauthorized(response)
        }

        request.setAttribute(ADMIN_ID_ATTR, context.adminId)
        request.setAttribute(ADMIN_ROLE_ATTR, context.role)
        request.setAttribute(ADMIN_USERNAME_ATTR, context.username)

        if (context.mustChangePassword && !isPasswordChangeAllowed(path)) {
            log.warn(
                "Admin auth failed: ip={}, path={}, reason={}, adminId={}",
                request.remoteAddr,
                path,
                "Must change password first",
                context.adminId,
            )
            return sendForbidden(response, "请先修改初始密码")
        }

        val superAdminPaths = listOf("/api/admin/system")
        if (superAdminPaths.any { path.startsWith(it) } && context.role != "SUPER_ADMIN") {
            log.warn(
                "Admin auth failed: ip={}, path={}, reason={}, adminId={}, role={}",
                request.remoteAddr,
                path,
                "Insufficient permissions",
                context.adminId,
                context.role,
            )
            return sendForbidden(response)
        }

        return true
    }

    private fun isPasswordChangeAllowed(path: String): Boolean {
        return path == "/api/admin/self/profile" || path == "/api/admin/self/password"
    }

    private fun sendUnauthorized(response: HttpServletResponse): Boolean {
        response.status = 401
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write("""{"success":false,"code":"20001","message":"未授权，请重新登录"}""")
        return false
    }

    private fun sendForbidden(response: HttpServletResponse, message: String = "权限不足"): Boolean {
        response.status = 403
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write("""{"success":false,"code":"20004","message":"$message"}""")
        return false
    }
}
