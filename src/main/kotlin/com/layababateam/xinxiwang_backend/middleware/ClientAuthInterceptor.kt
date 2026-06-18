package com.layababateam.xinxiwang_backend.middleware

import com.layababateam.xinxiwang_backend.service.AuthTokenResolver
import com.layababateam.xinxiwang_backend.service.ClientAuthRefreshPolicy
import com.layababateam.xinxiwang_backend.service.StringValueRules
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class ClientAuthInterceptor(
    private val authTokenResolver: AuthTokenResolver,
    private val refreshPolicy: ClientAuthRefreshPolicy,
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(ClientAuthInterceptor::class.java)

    companion object {
        const val USER_ID_ATTR = "userId"
        const val AUTH_TOKEN_ATTR = "authToken"
        const val BACKEND_COMPAT_VERSION_ATTR = "backendCompatVersion"
        const val CLIENT_PLATFORM_ATTR = "clientPlatform"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val path = request.requestURI
        val authHeader = request.getHeader("Authorization")

        val userId = try {
            authTokenResolver.resolveUserId(authHeader, refreshTtl = refreshPolicy.refreshTokenTtlOnClientAuth)
        } catch (e: Exception) {
            log.info(
                "Client auth failed: ip={}, path={}, reason={}",
                request.remoteAddr,
                path,
                "Token resolution exception: ${e.message}",
            )
            return sendUnauthorized(response)
        }

        if (userId == null) {
            log.info("Client auth failed: ip={}, path={}, reason={}", request.remoteAddr, path, "Invalid or missing token")
            return sendUnauthorized(response)
        }

        request.setAttribute(USER_ID_ATTR, userId)
        val token = if (authHeader!!.startsWith("Bearer ")) authHeader.substring(7) else authHeader
        request.setAttribute(AUTH_TOKEN_ATTR, token)
        StringValueRules.nonBlank(request.getHeader("X-Backend-Compat-Version"))?.let {
            request.setAttribute(BACKEND_COMPAT_VERSION_ATTR, it)
        }
        StringValueRules.nonBlank(request.getHeader("X-Client-Platform"))?.let {
            request.setAttribute(CLIENT_PLATFORM_ATTR, it)
        }
        return true
    }

    private fun sendUnauthorized(response: HttpServletResponse): Boolean {
        response.status = 401
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write("""{"success":false,"code":"20001","message":"未授权，请重新登录"}""")
        return false
    }
}
