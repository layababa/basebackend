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
        writeJsonError(response, 401, """{"success":false,"code":"20001","message":"未授权，请重新登录"}""")
        return false
    }

    private fun sendForbidden(response: HttpServletResponse, message: String = "权限不足"): Boolean {
        writeJsonError(response, 403, """{"success":false,"code":"20004","message":"$message"}""")
        return false
    }

    // 用 outputStream 直接写字节 + 显式 Content-Length，而不是 response.writer.write(...)。
    // 之前的实现偶发出现 403/401 响应 status 和 header 都对，但 body 是空的（content-length: 0）：
    // PrintWriter 默认不会立即 flush，一旦请求链路里任何一方先摸过 getOutputStream()（例如响应包装/压缩相关
    // 组件），同一 response 上再调 getWriter() 会因 getOutputStream()/getWriter() 二选一的 Servlet 规范冲突
    // 而抛异常，异常发生时 status/header 已经提交但 body 从未写出。直接写 outputStream 并立即 flush，
    // 避免这一整类 writer 相关的时序问题。
    private fun writeJsonError(response: HttpServletResponse, status: Int, body: String) {
        response.status = status
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        val bytes = body.toByteArray(Charsets.UTF_8)
        response.setContentLength(bytes.size)
        response.outputStream.write(bytes)
        response.outputStream.flush()
    }
}
