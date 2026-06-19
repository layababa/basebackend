package com.layababateam.xinxiwang_backend.service

import tools.jackson.databind.json.JsonMapper

/**
 * 腾讯 TRTC UserSig v2 生成器。
 *
 * SDK 统一维护签名算法，接入方只负责提供 sdkAppId、secretKey 和过期时间策略。
 */
class TrtcUserSigGenerator(
    private val objectMapper: JsonMapper,
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

        val jsonBytes = objectMapper.writeValueAsBytes(sigDoc)
        return EncodingRules.trtcBase64(CompressionRules.deflate(jsonBytes))
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
        return EncodingRules.base64(HmacRules.hmacSha256(secretKey, content))
    }
}
