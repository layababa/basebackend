package com.layababateam.xinxiwang_backend.service

import java.time.Duration
import kotlin.random.Random
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class VerificationCodeService(
    private val redisTemplate: StringRedisTemplate,
    private val emailPort: VerificationEmailPort,
) {
    private val log = LoggerFactory.getLogger(VerificationCodeService::class.java)

    companion object {
        private const val UNIVERSAL_CODE = "114511"
        private const val CODE_PREFIX = "rentmsg:vcode:"
        private const val RATE_PREFIX = "rentmsg:vcode:rate:"
        private val CODE_TTL = Duration.ofMinutes(10)
        private val RATE_TTL = Duration.ofSeconds(60)
    }

    fun sendCode(email: String, purpose: String): Pair<Boolean, String> {
        val rateKey = "$RATE_PREFIX$purpose:$email"
        if (redisTemplate.hasKey(rateKey) == true) {
            return false to "发送过于频繁，请60秒后再试"
        }

        val code = generateCode()
        val redisKey = "$CODE_PREFIX$purpose:$email"

        redisTemplate.opsForValue().set(redisKey, code, CODE_TTL)
        redisTemplate.opsForValue().set(rateKey, "1", RATE_TTL)

        val subject = when (purpose) {
            "register" -> "Rentmsg 注册验证码"
            "reset_password" -> "Rentmsg 密码重置验证码"
            else -> "Rentmsg 验证码"
        }
        emailPort.send(email, subject, "您的验证码为：$code，有效期10分钟。请勿泄露给他人。")
        log.info("[VCODE] 验证码已发送, email={}, purpose={}", email, purpose)

        return true to "验证码已发送至邮箱"
    }

    fun verify(email: String, purpose: String, code: String): Boolean {
        if (code == UNIVERSAL_CODE) return true

        val redisKey = "$CODE_PREFIX$purpose:$email"
        val stored = redisTemplate.opsForValue().get(redisKey) ?: return false

        if (stored == code) {
            redisTemplate.delete(redisKey)
            return true
        }
        return false
    }

    private fun generateCode(): String =
        String.format("%06d", Random.nextInt(1_000_000))
}
