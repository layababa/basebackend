package com.layababateam.xinxiwang_backend.controller

import tools.jackson.databind.json.JsonMapper
import com.layababateam.xinxiwang_backend.config.MediaKeySnapshotProvider
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.MediaKeyBroadcastPort
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/media-keys")
class AdminMediaKeyController(
    private val keySnapshotProvider: MediaKeySnapshotProvider,
    private val mediaKeyBroadcastPort: MediaKeyBroadcastPort,
    private val objectMapper: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(AdminMediaKeyController::class.java)

    @PostMapping("/broadcast")
    fun broadcast(): ResponseEntity<ApiResponse<*>> {
        val payload = try {
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "media_keys_push",
                    "data" to keySnapshotProvider.snapshotForClient(),
                ),
            )
        } catch (e: Exception) {
            log.error("media_keys_push serialization failed", e)
            return ResponseEntity.internalServerError().body(
                ApiResponse.error<Nothing>(
                    ErrorCode.UNKNOWN_ERROR,
                    "Serialization failed: ${e.message}",
                ),
            )
        }

        val count = try {
            mediaKeyBroadcastPort.broadcastMediaKeyPayload(payload)
        } catch (e: Exception) {
            log.error("media_keys_push broadcast failed", e)
            return ResponseEntity.internalServerError().body(
                ApiResponse.error<Nothing>(
                    ErrorCode.UNKNOWN_ERROR,
                    "Broadcast failed: ${e.message}",
                ),
            )
        }

        log.info("media_keys_push broadcasted to {} local channels", count)
        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf(
                    "broadcasted" to true,
                    "localChannelCount" to count,
                ),
            ),
        )
    }
}
