package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.BroadcastLuckyBagPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("legacy-broadcast")
@RequestMapping("/api/broadcast/lucky-bags")
class BroadcastLuckyBagController(
    private val luckyBagPort: BroadcastLuckyBagPort,
) {
    @PostMapping("/{id}/join")
    fun join(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(luckyBagPort.join(userId, id)))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(luckyBagPort.get(id, userId)))
    }
}
