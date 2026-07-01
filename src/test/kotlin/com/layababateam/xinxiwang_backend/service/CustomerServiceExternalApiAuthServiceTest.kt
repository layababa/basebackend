package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.CustomerServiceExternalApiCredential
import com.layababateam.xinxiwang_backend.repository.CustomerServiceExternalApiCredentialRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.StringRedisTemplate
import java.lang.reflect.Proxy
import java.util.Optional
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CustomerServiceExternalApiAuthServiceTest {
    @Test
    fun `authenticates hmac request and rejects nonce replay`() {
        val credential = CustomerServiceExternalApiCredential(
            id = "credential-1",
            name = "Partner",
            apiKey = "key-1",
            apiSecret = "secret-1",
            qrCodeId = "qr-1",
            createdBy = "admin-1",
        )
        val repository = externalAuthCredentialRepository(credential)
        val auth = CustomerServiceExternalApiAuthService(
            credentialRepository = repository,
            redisTemplateProvider = emptyRedisProvider(),
            signature = CustomerServiceExternalApiSignature(),
        )
        val body = """{"anonymousId":"visitor-1","content":"hello"}"""
        val signed = CustomerServiceExternalApiSignature().sign(
            secret = "secret-1",
            method = "POST",
            path = "/api/external/customer-service/sessions",
            canonicalQuery = "",
            body = body,
            timestamp = "1782800000000",
            nonce = "nonce-1",
        )
        val request = signedRequest(
            method = "POST",
            path = "/api/external/customer-service/sessions",
            headers = mapOf(
                "X-CS-API-Key" to "key-1",
                "X-CS-Timestamp" to "1782800000000",
                "X-CS-Nonce" to "nonce-1",
                "external-sign" to signed,
            ),
        )

        val authenticated = auth.authenticate(request, body, nowMillis = 1782800001000)

        assertEquals("credential-1", authenticated.id)
        assertFailsWith<ExternalCustomerServiceApiException> {
            auth.authenticate(request, body, nowMillis = 1782800001000)
        }
    }
}

private fun externalAuthCredentialRepository(
    credential: CustomerServiceExternalApiCredential,
): CustomerServiceExternalApiCredentialRepository =
    externalAuthProxy(CustomerServiceExternalApiCredentialRepository::class.java) { method, args ->
        when (method.name) {
            "findByApiKey" -> if (args?.firstOrNull() == credential.apiKey) credential else null
            "findById" -> Optional.ofNullable(if (args?.firstOrNull() == credential.id) credential else null)
            else -> externalAuthDefaultValue(method.returnType)
        }
    }

private fun signedRequest(
    method: String,
    path: String,
    headers: Map<String, String>,
): HttpServletRequest =
    externalAuthProxy(HttpServletRequest::class.java) { reflected, args ->
        when (reflected.name) {
            "getMethod" -> method
            "getRequestURI" -> path
            "getQueryString" -> null
            "getParameterMap" -> emptyMap<String, Array<String>>()
            "getHeader" -> headers[args?.firstOrNull() as String]
            else -> externalAuthDefaultValue(reflected.returnType)
        }
    }

@Suppress("UNCHECKED_CAST")
private fun emptyRedisProvider(): ObjectProvider<StringRedisTemplate> =
    externalAuthProxy(ObjectProvider::class.java) { method, _ ->
        when (method.name) {
            "getIfAvailable", "getIfUnique" -> null
            "iterator" -> emptyList<StringRedisTemplate>().iterator()
            "stream", "orderedStream" -> Stream.empty<StringRedisTemplate>()
            else -> externalAuthDefaultValue(method.returnType)
        }
    } as ObjectProvider<StringRedisTemplate>

private fun <T> externalAuthProxy(type: Class<T>, handler: (java.lang.reflect.Method, Array<Any?>?) -> Any?): T =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> handler(method, args) } as T

private fun externalAuthDefaultValue(type: Class<*>): Any? =
    when (type) {
        Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
        Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
        Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
        String::class.java -> ""
        Optional::class.java -> Optional.empty<Any>()
        List::class.java -> emptyList<Any>()
        Iterable::class.java -> emptyList<Any>()
        Stream::class.java -> Stream.empty<Any>()
        else -> null
    }
