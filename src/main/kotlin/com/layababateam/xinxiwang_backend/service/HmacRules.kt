package com.layababateam.xinxiwang_backend.service

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC 纯规则。
 *
 * 调用方负责选择编码格式和截断策略；SDK 统一维护签名算法调用。
 */
object HmacRules {
    fun hmacSha256(secret: String, content: String): ByteArray =
        hmacSha256(
            secret = secret.toByteArray(StandardCharsets.UTF_8),
            content = content.toByteArray(StandardCharsets.UTF_8),
        )

    fun hmacSha256(secret: ByteArray, content: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(secret, HMAC_SHA256))
        return mac.doFinal(content)
    }

    private const val HMAC_SHA256 = "HmacSHA256"
}
