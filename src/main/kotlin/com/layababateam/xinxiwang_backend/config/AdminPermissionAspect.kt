package com.layababateam.xinxiwang_backend.config

import com.layababateam.xinxiwang_backend.exception.ForbiddenException
import com.layababateam.xinxiwang_backend.exception.UnauthorizedException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Aspect
@Component
class AdminPermissionAspect {

    @Around("@annotation(requireAdmin)")
    fun checkPermission(joinPoint: ProceedingJoinPoint, requireAdmin: RequireAdmin): Any? {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        val adminRole = request.getAttribute(ADMIN_ROLE_ATTR) as? String
            ?: throw UnauthorizedException()
        if (!checkPermission(adminRole, requireAdmin.role)) {
            throw ForbiddenException()
        }
        return joinPoint.proceed()
    }

    private fun checkPermission(role: String, requiredRole: String): Boolean {
        val roleLevel = ROLE_HIERARCHY[role] ?: 0
        val requiredLevel = ROLE_HIERARCHY[requiredRole] ?: 0
        return roleLevel >= requiredLevel
    }

    companion object {
        const val ADMIN_ROLE_ATTR = "adminRole"

        private val ROLE_HIERARCHY = mapOf(
            "SUPER_ADMIN" to 3,
            "ADMIN" to 2,
            "MODERATOR" to 1
        )
    }
}
