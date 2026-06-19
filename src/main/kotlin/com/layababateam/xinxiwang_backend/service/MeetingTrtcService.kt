package com.layababateam.xinxiwang_backend.service

import tools.jackson.databind.json.JsonMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class MeetingTrtcService(
    private val objectMapper: JsonMapper,
    @Value("\${xinxiwang.meeting.trtc.sdk-app-id:20030819}")
    private val meetingSdkAppId: Long,
    @Value("\${xinxiwang.meeting.trtc.secret-key:}")
    private val meetingSecretKey: String
) {
    private val log = LoggerFactory.getLogger(MeetingTrtcService::class.java)

    companion object {
        const val MEETING_SDK_APP_ID = 20030819L
        private const val DEFAULT_EXPIRE = 86400L
    }

    fun genUserSig(userId: String, expire: Long = DEFAULT_EXPIRE): String {
        require(meetingSecretKey.isNotBlank()) {
            "xinxiwang.meeting.trtc.secret-key 未配置，无法生成有效 UserSig（拒绝用空密钥签名）"
        }
        log.info(
            "[MEETING-TRTC] Generating UserSig for userId={}, sdkAppId={}, expire={}s",
            userId, meetingSdkAppId, expire
        )

        val result = TrtcUserSigGenerator(objectMapper).generate(
            userId = userId,
            sdkAppId = meetingSdkAppId,
            secretKey = meetingSecretKey,
            expire = expire,
        )

        log.info(
            "[MEETING-TRTC] UserSig generated for userId={}, length={} chars",
            userId, result.length
        )
        return result
    }
}
