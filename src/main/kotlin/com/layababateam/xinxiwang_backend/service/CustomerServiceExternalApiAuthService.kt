package com.layababateam.xinxiwang_backend.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.layababateam.xinxiwang_backend.model.CustomerServiceExternalApiCredential
import com.layababateam.xinxiwang_backend.repository.CustomerServiceExternalApiCredentialRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

class ExternalCustomerServiceApiException(
    val externalCode: Int,
    override val message: String,
    val status: HttpStatus = HttpStatus.BAD_REQUEST,
) : RuntimeException(message)

@Service
class CustomerServiceExternalApiAuthService(
    private val credentialRepository: CustomerServiceExternalApiCredentialRepository,
    private val redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
    private val signature: CustomerServiceExternalApiSignature,
) {
    fun authenticate(
        request: HttpServletRequest,
        rawBody: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): CustomerServiceExternalApiCredential {
        val apiKey = requiredHeader(request, API_KEY_HEADER)
        val timestamp = requiredHeader(request, TIMESTAMP_HEADER)
        val nonce = requiredHeader(request, NONCE_HEADER)
        val providedSignature = requiredHeader(request, SIGNATURE_HEADER).lowercase()
        val credential = credentialRepository.findByApiKey(apiKey)
            ?: throw ExternalCustomerServiceApiException(103, "invalid api key", HttpStatus.UNAUTHORIZED)
        if (!credential.enabled) {
            throw ExternalCustomerServiceApiException(103, "api key disabled", HttpStatus.FORBIDDEN)
        }
        validateTimestamp(timestamp, nowMillis)
        if (!consumeNonce(apiKey, nonce)) {
            throw ExternalCustomerServiceApiException(102, "nonce replayed", HttpStatus.UNAUTHORIZED)
        }
        val matches = signature.matches(
            expected = providedSignature,
            secret = credential.apiSecret,
            method = request.method,
            path = request.requestURI,
            canonicalQuery = canonicalQuery(request),
            body = rawBody,
            timestamp = timestamp,
            nonce = nonce,
        )
        if (!matches) {
            throw ExternalCustomerServiceApiException(101, "invalid signature", HttpStatus.UNAUTHORIZED)
        }
        return credential
    }

    private fun requiredHeader(request: HttpServletRequest, name: String): String =
        request.getHeader(name)?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ExternalCustomerServiceApiException(100, "$name is required", HttpStatus.UNAUTHORIZED)

    private fun validateTimestamp(timestamp: String, nowMillis: Long) {
        val parsed = timestamp.toLongOrNull()
            ?: throw ExternalCustomerServiceApiException(102, "invalid timestamp", HttpStatus.UNAUTHORIZED)
        val timestampMillis = if (parsed < 10_000_000_000L) parsed * 1000L else parsed
        if (kotlin.math.abs(nowMillis - timestampMillis) > SIGNATURE_WINDOW.toMillis()) {
            throw ExternalCustomerServiceApiException(102, "timestamp expired", HttpStatus.UNAUTHORIZED)
        }
    }

    private fun consumeNonce(apiKey: String, nonce: String): Boolean {
        val key = "cs_external_api_nonce:$apiKey:$nonce"
        val redis = redisTemplateProvider.getIfAvailable()
        if (redis != null) {
            return redis.opsForValue().setIfAbsent(key, "1", SIGNATURE_WINDOW) == true
        }
        return LOCAL_NONCES.asMap().putIfAbsent(key, true) == null
    }

    private fun canonicalQuery(request: HttpServletRequest): String {
        val query = request.queryString
        if (!query.isNullOrBlank()) return query.split('&').filter { it.isNotBlank() }.sorted().joinToString("&")
        return request.parameterMap
            .toSortedMap()
            .flatMap { (key, values) ->
                values.sorted().map { value ->
                    "${queryEncode(key)}=${queryEncode(value)}"
                }
            }
            .joinToString("&")
    }

    private fun queryEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val API_KEY_HEADER = "X-CS-API-Key"
        const val TIMESTAMP_HEADER = "X-CS-Timestamp"
        const val NONCE_HEADER = "X-CS-Nonce"
        const val SIGNATURE_HEADER = "external-sign"
        val SIGNATURE_WINDOW: Duration = Duration.ofMinutes(5)
        val LOCAL_NONCES = Caffeine.newBuilder()
            .expireAfterWrite(SIGNATURE_WINDOW)
            .build<String, Boolean>()
    }
}
