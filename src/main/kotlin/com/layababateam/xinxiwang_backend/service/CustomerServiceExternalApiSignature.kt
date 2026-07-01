package com.layababateam.xinxiwang_backend.service

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.stereotype.Component

@Component
class CustomerServiceExternalApiSignature {
    fun sign(
        secret: String,
        method: String,
        path: String,
        canonicalQuery: String,
        body: String,
        timestamp: String,
        nonce: String,
    ): String {
        val signingText = listOf(
            method.uppercase(),
            path,
            canonicalQuery,
            sha256Hex(body),
            timestamp,
            nonce,
        ).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(signingText.toByteArray(StandardCharsets.UTF_8)).toHex()
    }

    fun matches(
        expected: String,
        secret: String,
        method: String,
        path: String,
        canonicalQuery: String,
        body: String,
        timestamp: String,
        nonce: String,
    ): Boolean {
        val actual = sign(secret, method, path, canonicalQuery, body, timestamp, nonce)
        return MessageDigest.isEqual(
            actual.toByteArray(StandardCharsets.UTF_8),
            expected.lowercase().toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
