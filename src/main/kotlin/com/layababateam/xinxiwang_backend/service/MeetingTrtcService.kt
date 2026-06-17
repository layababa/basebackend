package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Base64
import java.util.zip.Deflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
@ConditionalOnProperty(prefix = "xinxiwang.meeting.trtc", name = ["secret-key"])
class MeetingTrtcService(
    private val objectMapper: ObjectMapper,
    @Value("\${xinxiwang.meeting.trtc.sdk-app-id:20030819}")
    private val meetingSdkAppId: Long,
    @Value("\${xinxiwang.meeting.trtc.secret-key}")
    private val meetingSecretKey: String
) {
    private val log = LoggerFactory.getLogger(MeetingTrtcService::class.java)

    companion object {
        const val MEETING_SDK_APP_ID = 20030819L
        private const val DEFAULT_EXPIRE = 86400L
    }

    fun genUserSig(userId: String, expire: Long = DEFAULT_EXPIRE): String {
        log.info(
            "[MEETING-TRTC] Generating UserSig for userId={}, sdkAppId={}, expire={}s",
            userId, meetingSdkAppId, expire
        )
        val currTime = System.currentTimeMillis() / 1000
        val sig = hmacSha256(userId, currTime, expire)

        val sigDoc = linkedMapOf<String, Any>(
            "TLS.ver" to "2.0",
            "TLS.identifier" to userId,
            "TLS.sdkappid" to meetingSdkAppId,
            "TLS.expire" to expire,
            "TLS.time" to currTime,
            "TLS.sig" to sig
        )

        val jsonBytes = objectMapper.writeValueAsBytes(sigDoc)
        log.info(
            "[MEETING-TRTC] SigDoc JSON size={} bytes, time={}, expireAt={}",
            jsonBytes.size, currTime, currTime + expire
        )

        val compressor = Deflater()
        compressor.setInput(jsonBytes)
        compressor.finish()
        val buf = ByteArray(4096)
        val len = compressor.deflate(buf)
        compressor.end()

        val result = Base64.getEncoder()
            .encodeToString(Arrays.copyOfRange(buf, 0, len))
            .replace("+", "*")
            .replace("/", "-")
            .replace("=", "_")

        log.info(
            "[MEETING-TRTC] UserSig generated for userId={}, length={} chars",
            userId, result.length
        )
        return result
    }

    private fun hmacSha256(identifier: String, currTime: Long, expire: Long): String {
        val content = "TLS.identifier:$identifier\n" +
                "TLS.sdkappid:$meetingSdkAppId\n" +
                "TLS.time:$currTime\n" +
                "TLS.expire:$expire\n"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(meetingSecretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(content.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }
}
