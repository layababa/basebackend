package com.layababateam.xinxiwang_backend.service

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class SalesmartlyExternalApiSignature {
    fun payload(token: String, params: Map<String, Any?>): String {
        val parts = mutableListOf(token)
        params.keys
            .filterNot { it.equals(SIGNATURE_HEADER, ignoreCase = true) }
            .sorted()
            .forEach { key -> parts += "$key=${stringify(params[key])}" }
        return parts.joinToString("&")
    }

    fun sign(token: String, params: Map<String, Any?>): String =
        MessageDigest.getInstance("MD5")
            .digest(payload(token, params).toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun matches(provided: String?, token: String, params: Map<String, Any?>): Boolean {
        val actual = sign(token, params)
        return MessageDigest.isEqual(
            actual.toByteArray(StandardCharsets.UTF_8),
            provided.orEmpty().lowercase().toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun stringify(value: Any?): String =
        when (value) {
            null -> ""
            is Array<*> -> value.joinToString(",") { stringify(it) }
            is Iterable<*> -> value.joinToString(",") { stringify(it) }
            else -> value.toString()
        }

    companion object {
        const val SIGNATURE_HEADER = "external-sign"
    }
}
