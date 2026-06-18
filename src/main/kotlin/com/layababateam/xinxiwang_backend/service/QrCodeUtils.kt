package com.layababateam.xinxiwang_backend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class GroupQrPayload(
    val groupId: String,
    val userId: String,
    val timestamp: Long
)

data class MeetingInvitePayload(
    val meetingId: String,
    val inviterId: String,
    val moderatorInvite: Boolean
)

@Component
class QrCodeUtils(
    @Value("\${xinxiwang.qr.aes-key}") private val aesKey: String
) {
    private val algorithm = "AES/CBC/PKCS5Padding"
    private val keySpec by lazy { SecretKeySpec(aesKey.toByteArray(Charsets.UTF_8).copyOf(16), "AES") }
    private val ivSpec by lazy { IvParameterSpec(aesKey.toByteArray(Charsets.UTF_8).copyOf(16)) }

    /**
     * 加密: groupId|userId|timestamp → Base64 URL-safe string
     */
    fun encryptGroupQr(groupId: String, userId: String, timestamp: Long): String {
        val plainText = "$groupId|$userId|$timestamp"
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
    }

    /**
     * 解密: Base64 URL-safe string → GroupQrPayload
     */
    fun decryptGroupQr(encrypted: String): GroupQrPayload? {
        return try {
            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decoded = Base64.getUrlDecoder().decode(encrypted)
            val plainText = String(cipher.doFinal(decoded), Charsets.UTF_8)
            val parts = plainText.split("|")
            if (parts.size != 3) return null
            GroupQrPayload(
                groupId = parts[0],
                userId = parts[1],
                timestamp = parts[2].toLongOrNull() ?: return null
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 验证二维码是否过期 (7天)
     */
    /**
     * Meeting share credential that lets moderator-created shares bypass meeting password checks.
     */
    fun encryptMeetingInvite(meetingId: String, inviterId: String, moderatorInvite: Boolean): String {
        val plainText = "meetingInvite|$meetingId|$inviterId|${if (moderatorInvite) 1 else 0}"
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
    }

    fun decryptMeetingInvite(encrypted: String): MeetingInvitePayload? {
        return try {
            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decoded = Base64.getUrlDecoder().decode(encrypted)
            val plainText = String(cipher.doFinal(decoded), Charsets.UTF_8)
            val parts = plainText.split("|")
            if (parts.size != 4 || parts[0] != "meetingInvite") return null
            MeetingInvitePayload(
                meetingId = parts[1],
                inviterId = parts[2],
                moderatorInvite = parts[3] == "1"
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isExpired(timestamp: Long): Boolean {
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - timestamp > sevenDaysMs
    }
}
