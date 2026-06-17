package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.MediaKeySnapshotProvider
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP fallback for fetching the current media key set.
 *
 * The primary delivery mechanism is the `media_keys_push` WebSocket message
 * sent on successful auth (see [com.layababateam.xinxiwang_backend.netty.NettyWebSocketHandler]).
 * This endpoint exists so that clients which lose the WS frame or connect via
 * a transport that skipped the push can still bootstrap.
 */
@RestController
@RequestMapping("/api/media/keys")
class MediaKeyController(
    private val keySnapshotProvider: MediaKeySnapshotProvider,
) {
    @GetMapping("/current")
    fun current(): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(keySnapshotProvider.snapshotForClient()))
    }
}
