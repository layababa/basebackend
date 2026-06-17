package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.AdminCallPort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/call")
class AdminCallController(
    private val adminCallPort: AdminCallPort,
) {

    @RequireAdmin
    @GetMapping("/by-user/{userId}")
    fun byUser(@PathVariable userId: String): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(adminCallPort.getCallByUser(userId)))
    }

    @RequireAdmin
    @GetMapping("/{roomId}")
    fun byRoom(@PathVariable roomId: Int): ResponseEntity<ApiResponse<*>> {
        val result = adminCallPort.getCallByRoom(roomId)
            ?: return ResponseEntity.ok(ApiResponse.error<Any>("session not found"))
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @RequireAdmin
    @GetMapping("/pending/{userId}")
    fun pending(@PathVariable userId: String): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(adminCallPort.getPendingCallState(userId)))
    }

    @RequireAdmin
    @PostMapping("/{roomId}/force-end")
    fun forceEnd(
        @PathVariable roomId: Int,
        @RequestParam(defaultValue = "admin") reason: String,
    ): ResponseEntity<ApiResponse<*>> {
        val result = adminCallPort.forceEndCall(roomId, reason)
            ?: return ResponseEntity.ok(ApiResponse.error<Any>("session not found"))
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @RequireAdmin
    @GetMapping("/history/{userId}")
    fun history(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(adminCallPort.getCallHistory(userId, page, size)))
    }
}
