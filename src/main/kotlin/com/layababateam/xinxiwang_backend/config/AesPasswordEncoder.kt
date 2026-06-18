package com.layababateam.xinxiwang_backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesPasswordEncoder(
    @Value("\${xianyun.password.aes-key:XianYun_AES_256bit_Key_2026!!}") private val aesKeyRaw: String,
) {
    companion object {
        private const val AES_PREFIX = "AES:"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    private val secretKey: SecretKeySpec by lazy {
        val keyBytes = aesKeyRaw.toByteArray(Charsets.UTF_8)
        SecretKeySpec(keyBytes.copyOf(32), "AES")
    }

    fun encrypt(plainPassword: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return AES_PREFIX + Base64.getEncoder()
            .encodeToString(iv + cipher.doFinal(plainPassword.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith(AES_PREFIX)) throw IllegalArgumentException("Not an AES-encrypted value")
        val combined = Base64.getDecoder().decode(stored.removePrefix(AES_PREFIX))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, combined.copyOfRange(0, GCM_IV_LENGTH)))
        return String(cipher.doFinal(combined.copyOfRange(GCM_IV_LENGTH, combined.size)), Charsets.UTF_8)
    }

    fun matches(rawPassword: String, stored: String): Boolean = when {
        stored.startsWith(AES_PREFIX) -> decrypt(stored) == rawPassword
        stored.startsWith("\$2") -> BCryptPasswordEncoder().matches(rawPassword, stored)
        else -> rawPassword == stored
    }

    fun isAesEncrypted(stored: String): Boolean = stored.startsWith(AES_PREFIX)
}
