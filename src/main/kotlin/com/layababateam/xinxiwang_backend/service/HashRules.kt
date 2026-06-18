package com.layababateam.xinxiwang_backend.service

import java.security.MessageDigest

/**
 * 稳定短哈希纯规则。
 *
 * 调用方负责输入拼接和脱敏策略；SDK 统一维护 SHA-256 与十六进制截断格式。
 */
object HashRules {
    fun sha256HexPrefix(value: String, bytes: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(bytes.coerceAtLeast(0)).joinToString("") { "%02x".format(it) }
    }
}
