package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.PointsPort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/points")
class AdminPointsController(
    private val pointsPort: PointsPort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping("/transactions")
    fun transactions(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) reason: String?,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> =
        pointsPort.adminTransactions(page, size, userId, reason)
}
