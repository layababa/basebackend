package com.layababateam.xinxiwang_backend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class WebCustomerServiceTokenService(
    @Value("\${xinxiwang.web-customer-service.visitor-token-secret:}")
    private val configuredSecret: String = "",
    @Value("\${spring.profiles.active:}")
    private val activeProfiles: String = "",
) {
    private val secret: String

    init {
        val trimmed = configuredSecret.trim()
        val strictProfile = activeProfiles
            .split(',', ';')
            .map { it.trim().lowercase() }
            .any { it == "staging" || it == "prod" || it == "production" }
        if (trimmed.isBlank() && strictProfile) {
            throw IllegalStateException("xinxiwang.web-customer-service.visitor-token-secret is required in staging/prod")
        }
        secret = trimmed.ifBlank { LOCAL_DEV_SECRET }
    }

    fun sign(
        entryId: String,
        sessionId: String,
        visitorId: String,
        ttlMillis: Long = DEFAULT_TOKEN_TTL_MILLIS,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val exp = nowMillis + ttlMillis
        val payload = listOf(entryId, sessionId, visitorId, exp.toString()).joinToString("|", transform = ::encode)
        val signature = hmac(payload)
        return "$payload.$signature"
    }

    fun verify(token: String?, nowMillis: Long = System.currentTimeMillis()): WebCustomerServiceVisitorClaims {
        val raw = token?.trim().orEmpty()
        val separator = raw.lastIndexOf('.')
        require(separator > 0 && separator < raw.length - 1) { "访客令牌无效" }
        val payload = raw.substring(0, separator)
        val signature = raw.substring(separator + 1)
        require(MessageDigest.isEqual(signature.toByteArray(), hmac(payload).toByteArray())) { "访客令牌无效" }

        val parts = payload.split('|')
        require(parts.size == 4) { "访客令牌无效" }
        val exp = decode(parts[3]).toLongOrNull() ?: throw IllegalArgumentException("访客令牌无效")
        require(exp >= nowMillis) { "访客令牌已过期" }
        return WebCustomerServiceVisitorClaims(
            entryId = decode(parts[0]),
            sessionId = decode(parts[1]),
            visitorId = decode(parts[2]),
            exp = exp,
        )
    }

    private fun hmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return BASE64_URL.encodeToString(mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun encode(value: String): String =
        BASE64_URL.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    companion object {
        const val DEFAULT_TOKEN_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val LOCAL_DEV_SECRET = "local-web-customer-service-dev-secret-change-before-prod"
        private val BASE64_URL: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}

data class WebCustomerServiceVisitorClaims(
    val entryId: String,
    val sessionId: String,
    val visitorId: String,
    val exp: Long,
)

class WebCustomerServiceConflictException(message: String) : RuntimeException(message)
