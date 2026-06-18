package com.layababateam.xinxiwang_backend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets

/**
 * HMAC-SHA256 signed proxy URL tokens.
 *
 * The proxy endpoint is reachable from the public internet, so each request
 * carries a token that proves the URL was minted by the server.  Tokens are
 * deterministic per [mediaId] (no expiry baked in) so the proxy can be served
 * by any backend node and so generated URLs can be cached.
 *
 * Verification is constant-time to avoid timing side channels.
 */
@Service
class MediaProxyTokenService(
    @Value("\${xinxiwang.media.proxy.token-secret}") private val secret: String,
) {
    private val secretBytes by lazy { secret.toByteArray(StandardCharsets.UTF_8) }

    fun sign(mediaId: String): String {
        val hmac = HmacRules.hmacSha256(secretBytes, mediaId.toByteArray(StandardCharsets.UTF_8))
        // base64url no-pad, truncated to TOKEN_LEN chars (~144 bits of entropy)
        val full = EncodingRules.base64UrlNoPadding(hmac)
        return full.substring(0, minOf(TOKEN_LEN, full.length))
    }

    fun verify(mediaId: String, token: String): Boolean {
        return SecurityCompareRules.constantTimeAsciiEquals(sign(mediaId), token)
    }

    companion object {
        private const val TOKEN_LEN = 24
    }
}
