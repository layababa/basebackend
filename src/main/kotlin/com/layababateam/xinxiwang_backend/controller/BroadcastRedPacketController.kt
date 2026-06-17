package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.BroadcastRedPacketPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 宣讲红包独立路由：契约 §2.5.3 / §2.5.4。
 * 与 IM 钱包红包解耦，故 base path `/api/broadcast/red-packets` 而非顶级。
 */
@RestController
@Profile("legacy-broadcast")
@RequestMapping("/api/broadcast/red-packets")
class BroadcastRedPacketController(
    private val redPacketPort: BroadcastRedPacketPort,
) {
    @PostMapping("/{id}/grab")
    fun grab(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(redPacketPort.grab(userId, id)))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(redPacketPort.get(id, userId)))
    }
}
