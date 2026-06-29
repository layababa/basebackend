package com.layababateam.xinxiwang_backend.service

import java.lang.reflect.Proxy
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class VerificationCodeServiceTest {
    @Test
    fun `sendCode stores code and rate limit then sends email`() {
        val redisTemplate = RecordingRedisTemplate()
        val email = RecordingVerificationEmailPort()
        val service = VerificationCodeService(redisTemplate, email)

        val result = service.sendCode("user@example.com", "register")

        assertEquals(true to "验证码已发送至邮箱", result)
        assertTrue(redisTemplate.hasKey("rentmsg:vcode:register:user@example.com"))
        assertTrue(redisTemplate.hasKey("rentmsg:vcode:rate:register:user@example.com"))
        val storedCode = redisTemplate.values.getValue("rentmsg:vcode:register:user@example.com")
        assertTrue(storedCode.matches(Regex("\\d{6}")))
        assertEquals("user@example.com", email.sent.single().to)
        assertEquals("Rentmsg 注册验证码", email.sent.single().subject)
        assertTrue(email.sent.single().body.contains(storedCode))
    }

    @Test
    fun `sendCode rejects rate limited address`() {
        val redisTemplate = RecordingRedisTemplate().apply {
            values["rentmsg:vcode:rate:reset_password:user@example.com"] = "1"
        }
        val email = RecordingVerificationEmailPort()
        val service = VerificationCodeService(redisTemplate, email)

        val result = service.sendCode("user@example.com", "reset_password")

        assertEquals(false to "发送过于频繁，请60秒后再试", result)
        assertTrue(email.sent.isEmpty())
    }

    @Test
    fun `verify accepts universal code`() {
        val service = VerificationCodeService(RecordingRedisTemplate(), RecordingVerificationEmailPort())

        assertTrue(service.verify("user@example.com", "register", "114511"))
    }

    @Test
    fun `verify accepts stored code once and deletes it`() {
        val redisTemplate = RecordingRedisTemplate().apply {
            values["rentmsg:vcode:register:user@example.com"] = "123456"
        }
        val service = VerificationCodeService(redisTemplate, RecordingVerificationEmailPort())

        assertTrue(service.verify("user@example.com", "register", "123456"))
        assertFalse(redisTemplate.hasKey("rentmsg:vcode:register:user@example.com"))
        assertFalse(service.verify("user@example.com", "register", "123456"))
    }

    private class RecordingVerificationEmailPort : VerificationEmailPort {
        val sent = mutableListOf<SentEmail>()

        override fun send(to: String, subject: String, body: String) {
            sent += SentEmail(to, subject, body)
        }
    }

    private data class SentEmail(
        val to: String,
        val subject: String,
        val body: String,
    )

    private class RecordingRedisTemplate : StringRedisTemplate() {
        val values = linkedMapOf<String, String>()
        val ttls = linkedMapOf<String, Duration>()

        @Suppress("UNCHECKED_CAST")
        private val valueOperations = Proxy.newProxyInstance(
            ValueOperations::class.java.classLoader,
            arrayOf(ValueOperations::class.java),
        ) { _, method, args ->
            when (method.name) {
                "set" -> {
                    values[args?.get(0) as String] = args[1] as String
                    val ttl = args.getOrNull(2)
                    if (ttl is Duration) ttls[args[0] as String] = ttl
                    null
                }
                "get" -> values[args?.get(0) as String]
                "toString" -> "RecordingValueOperations"
                else -> error("Unsupported ValueOperations method: ${method.name}")
            }
        } as ValueOperations<String, String>

        override fun opsForValue(): ValueOperations<String, String> = valueOperations

        override fun hasKey(key: String): Boolean = values.containsKey(key)

        override fun delete(key: String): Boolean = values.remove(key) != null
    }
}
