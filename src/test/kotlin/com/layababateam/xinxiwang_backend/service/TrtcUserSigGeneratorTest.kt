package com.layababateam.xinxiwang_backend.service

import tools.jackson.databind.json.JsonMapper
import java.util.Base64
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 🔴 T3.1 / T4.6 核心护栏：锁定「Jackson 2→3 迁移后 UserSig 生成字节不漂移」。
 *
 * TrtcUserSigGenerator.generate() 内部用 System.currentTimeMillis()，整段输出非确定。
 * 因此本测试拆成三层确定性护栏：
 *  1. golden：固定 sigDoc(LinkedHashMap) 经 Jackson 3 JsonMapper.writeValueAsBytes 的字节
 *     必须等于已固化字符串（property 顺序 / 空格 / 转义不漂移）——这是签名输入字节的根。
 *  2. golden：固定 secret/userId/sdkAppId/time/expire 下 HMAC 拼接串 + base64 的确定性输出。
 *  3. 端到端往返：generate() 输出经 trtcBase64 逆替换 → inflate → JSON 解析，
 *     断言字段顺序/值，且内嵌 TLS.sig 等于按抓取到的 TLS.time 独立重算的 HMAC。
 */
class TrtcUserSigGeneratorTest {

    private val mapper: JsonMapper = JsonMapper.builder().build()

    // 固定输入（golden 基准）
    private val secretKey = "test-secret-key-1234567890abcdef"
    private val userId = "user_42"
    private val sdkAppId = 20030819L
    private val expire = 604800L
    private val fixedTime = 1718764800L

    @Test
    fun `golden - Jackson3 serializes fixed sigDoc to expected bytes (UserSig input byte stability)`() {
        val sig = hmacBase64(userId, sdkAppId, secretKey, fixedTime, expire)
        val sigDoc = linkedMapOf<String, Any>(
            "TLS.ver" to "2.0",
            "TLS.identifier" to userId,
            "TLS.sdkappid" to sdkAppId,
            "TLS.expire" to expire,
            "TLS.time" to fixedTime,
            "TLS.sig" to sig,
        )
        val json = String(mapper.writeValueAsBytes(sigDoc), Charsets.UTF_8)

        // Golden：先跑取得当前 Jackson 3 输出固化为断言。property 顺序须与 LinkedHashMap 一致、无多余空格。
        val expected =
            """{"TLS.ver":"2.0","TLS.identifier":"user_42","TLS.sdkappid":20030819,""" +
            """"TLS.expire":604800,"TLS.time":1718764800,"TLS.sig":"$sig"}"""
        assertEquals(expected, json, "Jackson 3 序列化字节漂移 → UserSig 会漂移")
    }

    @Test
    fun `golden - HMAC content format and base64 are deterministic`() {
        // HMAC 拼接顺序：identifier -> sdkappid -> time -> expire，逐行 \n
        val sig = hmacBase64(userId, sdkAppId, secretKey, fixedTime, expire)
        // 固化 golden（首跑取值）。一旦 HMAC 拼接串顺序/编码变化即破裂。
        assertEquals(GOLDEN_SIG, sig, "HMAC 拼接串顺序或 base64 编码漂移")
    }

    @Test
    fun `end-to-end - generate output decodes back to expected sigDoc with valid sig`() {
        val generator = TrtcUserSigGenerator(mapper)
        val output = generator.generate(userId, sdkAppId, secretKey, expire)

        // trtcBase64 逆替换：* -> + / - -> / / _ -> =
        val stdBase64 = output.replace("*", "+").replace("-", "/").replace("_", "=")
        val deflated = Base64.getDecoder().decode(stdBase64)
        val json = String(inflate(deflated), Charsets.UTF_8)

        // 字段顺序锁定（除 time/sig 外全部确定）
        assertTrue(json.startsWith("""{"TLS.ver":"2.0","TLS.identifier":"user_42","TLS.sdkappid":20030819,"TLS.expire":604800,"TLS.time":"""), "JSON 字段顺序/值漂移: $json")

        @Suppress("UNCHECKED_CAST")
        val doc = mapper.readValue(json, Map::class.java) as Map<String, Any>
        assertEquals("2.0", doc["TLS.ver"])
        assertEquals(userId, doc["TLS.identifier"])
        assertEquals(sdkAppId.toInt(), (doc["TLS.sdkappid"] as Number).toInt())
        assertEquals(expire.toInt(), (doc["TLS.expire"] as Number).toInt())

        val embeddedTime = (doc["TLS.time"] as Number).toLong()
        val embeddedSig = doc["TLS.sig"] as String
        // 用抓取到的 time 独立重算 HMAC，必须与内嵌 sig 完全一致（签名算法等价）
        assertEquals(hmacBase64(userId, sdkAppId, secretKey, embeddedTime, expire), embeddedSig)
    }

    @Test
    fun `trtcBase64 substitution table plus slash equals`() {
        val input = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte())
        val std = EncodingRules.base64(input)
        val trtc = EncodingRules.trtcBase64(input)
        assertEquals(std.replace("+", "*").replace("/", "-").replace("=", "_"), trtc)
    }

    private fun hmacBase64(identifier: String, appId: Long, secret: String, time: Long, exp: Long): String {
        val content = "TLS.identifier:$identifier\n" +
            "TLS.sdkappid:$appId\n" +
            "TLS.time:$time\n" +
            "TLS.expire:$exp\n"
        return EncodingRules.base64(HmacRules.hmacSha256(secret, content))
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val buffer = ByteArray(4096)
        val out = java.io.ByteArrayOutputStream()
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0) break
                out.write(buffer, 0, n)
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    companion object {
        // Golden HMAC（独立用 python hmac-sha256 + base64 复算固化，见 plan 回报）。
        // content = "TLS.identifier:user_42\nTLS.sdkappid:20030819\nTLS.time:1718764800\nTLS.expire:604800\n"
        const val GOLDEN_SIG = "8y4PAvIKGCmKvubBW+DGnAa/LQ9VpBWp4fEjtiQ/VHQ="
    }
}
