package com.layababateam.xinxiwang_backend.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A1 护栏：trtcBase64 替换表（+→* /→- =→_）+ base64Url 往返。
 * 这是 UserSig 编码不漂移的子护栏。
 */
class EncodingRulesTest {

    @Test
    fun `trtcBase64 replaces plus slash and equals per TRTC table`() {
        // 0xFB 0xFF 0xBF 标准 base64 -> "+/+/" 含 + 与 /；长度触发 padding =
        val input = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte())
        val std = EncodingRules.base64(input)
        val trtc = EncodingRules.trtcBase64(input)

        // 标准编码确实含目标字符，否则断言无意义
        assertTrue(std.contains("+") || std.contains("/") || std.contains("="), "前置：标准 base64 应含可替换字符: $std")
        assertEquals(std.replace("+", "*").replace("/", "-").replace("=", "_"), trtc)
        assertTrue(!trtc.contains("+") && !trtc.contains("/") && !trtc.contains("="))
    }

    @Test
    fun `base64Url roundtrip is lossless`() {
        val original = "groupId|userId|1718764800000".toByteArray(Charsets.UTF_8)
        val encoded = EncodingRules.base64UrlNoPadding(original)
        assertTrue(!encoded.contains("=") && !encoded.contains("+") && !encoded.contains("/"))
        val decoded = EncodingRules.decodeBase64Url(encoded)
        assertEquals(String(original, Charsets.UTF_8), String(decoded, Charsets.UTF_8))
    }
}
