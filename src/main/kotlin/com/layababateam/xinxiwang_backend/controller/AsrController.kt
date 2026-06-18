package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.AsrPort
import com.layababateam.xinxiwang_backend.service.mediaExtensionFromUrl
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/asr")
class AsrController(
    private val asrPort: AsrPort,
) {
    private val log = LoggerFactory.getLogger(AsrController::class.java)

    @PostMapping("/transcribe")
    fun transcribe(
        request: HttpServletRequest,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        val url = body["url"]
            ?: return ResponseEntity.badRequest()
                .body(ApiResponse.error<Nothing>(ErrorCode.INVALID_PARAM, "URL 不能为空"))

        log.info("[ASR] user={} url={}", userId, url)

        return try {
            val text = asrPort.transcribe(url, resolveAudioFormat(url))
            log.info("[ASR] success, text length={}", text.length)
            ResponseEntity.ok(ApiResponse.ok(mapOf("text" to text)))
        } catch (e: Exception) {
            log.error("[ASR] failed: {}", e.message, e)
            ResponseEntity.status(500)
                .body(ApiResponse.error<Nothing>(ErrorCode.UNKNOWN_ERROR, e.message ?: "语音识别失败"))
        }
    }

    private fun resolveAudioFormat(url: String): String {
        val extension = mediaExtensionFromUrl(url) ?: AsrPort.DEFAULT_FORMAT
        return if (extension in SUPPORTED_FORMATS) extension else AsrPort.DEFAULT_FORMAT
    }

    private companion object {
        val SUPPORTED_FORMATS = setOf("mp3", "m4a", "wav", "aac", "ogg")
    }
}
