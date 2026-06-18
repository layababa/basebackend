package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Base64
import java.util.zip.Deflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 腾讯 TRTC UserSig v2 生成器。
 *
 * SDK 统一维护签名算法，接入方只负责提供 sdkAppId、secretKey 和过期时间策略。
 */
class TrtcUserSigGenerator(
    private val objectMapper: ObjectMapper,
) {
    fun generate(userId: String, sdkAppId: Long, secretKey: String, expire: Long): String {
        val currTime = System.currentTimeMillis() / 1000
        val sigDoc = linkedMapOf<String, Any>(
            "TLS.ver" to "2.0",
            "TLS.identifier" to userId,
            "TLS.sdkappid" to sdkAppId,
            "TLS.expire" to expire,
            "TLS.time" to currTime,
            "TLS.sig" to hmacSha256(userId, sdkAppId, secretKey, currTime, expire),
        )

        val compressor = Deflater()
        return try {
            val jsonBytes = objectMapper.writeValueAsBytes(sigDoc)
            compressor.setInput(jsonBytes)
            compressor.finish()
            val buf = ByteArray(4096)
            val len = compressor.deflate(buf)
            Base64.getEncoder()
                .encodeToString(Arrays.copyOfRange(buf, 0, len))
                .replace("+", "*")
                .replace("/", "-")
                .replace("=", "_")
        } finally {
            compressor.end()
        }
    }

    private fun hmacSha256(
        identifier: String,
        sdkAppId: Long,
        secretKey: String,
        currTime: Long,
        expire: Long,
    ): String {
        val content = "TLS.identifier:$identifier\n" +
            "TLS.sdkappid:$sdkAppId\n" +
            "TLS.time:$currTime\n" +
            "TLS.expire:$expire\n"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(content.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }
}
