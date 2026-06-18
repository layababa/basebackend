package com.layababateam.xinxiwang_backend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
    private val keySpec by lazy {
        SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_ALG)
    }

    fun sign(mediaId: String): String {
        val mac = Mac.getInstance(HMAC_ALG)
        mac.init(keySpec)
        val hmac = mac.doFinal(mediaId.toByteArray(StandardCharsets.UTF_8))
        // base64url no-pad, truncated to TOKEN_LEN chars (~144 bits of entropy)
        val full = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac)
        return full.substring(0, minOf(TOKEN_LEN, full.length))
    }

    fun verify(mediaId: String, token: String): Boolean {
        val expected = sign(mediaId).toByteArray(StandardCharsets.US_ASCII)
        val actual = token.toByteArray(StandardCharsets.US_ASCII)
        if (expected.size != actual.size) return false
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].toInt() xor actual[i].toInt())
        }
        return diff == 0
    }

    companion object {
        private const val HMAC_ALG = "HmacSHA256"
        private const val TOKEN_LEN = 24
    }
}
