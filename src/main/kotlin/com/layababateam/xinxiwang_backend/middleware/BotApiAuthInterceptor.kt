package com.layababateam.xinxiwang_backend.middleware

import com.layababateam.xinxiwang_backend.service.BotApiAuthAttributes
import com.layababateam.xinxiwang_backend.service.BotApiCredentialResolver
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class BotApiAuthInterceptor(
    private val credentialResolver: BotApiCredentialResolver,
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(BotApiAuthInterceptor::class.java)

    companion object {
        private const val AUTH_PREFIX = "Bot "
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith(AUTH_PREFIX)) {
            return sendUnauthorized(response, "Missing or invalid Authorization header. Use 'Bot <api_key>'")
        }

        val apiKey = authHeader.substring(AUTH_PREFIX.length).trim()
        if (apiKey.isBlank()) {
            return sendUnauthorized(response, "API key is empty")
        }

        val userId = credentialResolver.resolveBotUserId(apiKey)
        if (userId == null) {
            // Token/API key misses are expected client failures; keep them local at info level.
            log.info("Bot auth failed: invalid API key, ip={}", request.remoteAddr)
            return sendUnauthorized(response, "Invalid API key")
        }

        request.setAttribute(BotApiAuthAttributes.BOT_USER_ID_ATTR, userId)
        return true
    }

    private fun sendUnauthorized(response: HttpServletResponse, message: String): Boolean {
        response.status = 401
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write("""{"success":false,"message":"$message"}""")
        return false
    }
}
