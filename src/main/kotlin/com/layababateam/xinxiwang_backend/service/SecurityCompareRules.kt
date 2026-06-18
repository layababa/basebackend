package com.layababateam.xinxiwang_backend.service

import java.nio.charset.StandardCharsets

/**
 * 安全比较纯规则。
 *
 * 用于 token、签名等短字符串比较，避免通过提前返回泄露差异位置。
 */
object SecurityCompareRules {
    fun constantTimeEquals(expected: ByteArray, actual: ByteArray): Boolean {
        if (expected.size != actual.size) return false
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].toInt() xor actual[i].toInt())
        }
        return diff == 0
    }

    fun constantTimeAsciiEquals(expected: String, actual: String): Boolean =
        constantTimeEquals(
            expected.toByteArray(StandardCharsets.US_ASCII),
            actual.toByteArray(StandardCharsets.US_ASCII),
        )
}
