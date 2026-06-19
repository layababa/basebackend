package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R1 回归护栏：常量时间比较的功能正确性（等值 / 不等值 / 不等长）。
 */
class SecurityCompareRulesTest {

    @Test
    fun `constantTimeEquals returns true for identical byte arrays`() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 3, 4, 5)
        assertTrue(SecurityCompareRules.constantTimeEquals(a, b))
    }

    @Test
    fun `constantTimeEquals returns false for same length different content`() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 3, 4, 9)
        assertFalse(SecurityCompareRules.constantTimeEquals(a, b))
    }

    @Test
    fun `constantTimeEquals returns false for different lengths`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 3, 4)
        assertFalse(SecurityCompareRules.constantTimeEquals(a, b))
    }

    @Test
    fun `constantTimeEquals handles empty arrays as equal`() {
        assertTrue(SecurityCompareRules.constantTimeEquals(ByteArray(0), ByteArray(0)))
    }

    @Test
    fun `constantTimeAsciiEquals matches identical ascii strings`() {
        assertTrue(SecurityCompareRules.constantTimeAsciiEquals("abc123-token", "abc123-token"))
    }

    @Test
    fun `constantTimeAsciiEquals rejects different strings`() {
        assertFalse(SecurityCompareRules.constantTimeAsciiEquals("abc123-token", "abc123-toker"))
    }

    @Test
    fun `constantTimeAsciiEquals rejects different length strings`() {
        assertFalse(SecurityCompareRules.constantTimeAsciiEquals("short", "longer-token"))
    }
}
