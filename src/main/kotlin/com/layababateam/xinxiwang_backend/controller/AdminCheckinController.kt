package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.AdminCheckinPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/checkin")
class AdminCheckinController(
    private val adminCheckinPort: AdminCheckinPort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping("/activities")
    fun list(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: Int?,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> =
        adminCheckinPort.list(page, size, keyword, status)

    @RequireAdmin("ADMIN")
    @GetMapping("/activities/{id}")
    fun detail(@PathVariable id: String): ResponseEntity<ApiResponse<Map<String, Any?>>> =
        adminCheckinPort.detail(id)

    @RequireAdmin("ADMIN")
    @PostMapping("/activities")
    fun create(
        request: HttpServletRequest,
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> =
        adminCheckinPort.create(request, body)

    @RequireAdmin("ADMIN")
    @PutMapping("/activities/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> =
        adminCheckinPort.update(id, body)

    @RequireAdmin("ADMIN")
    @PostMapping("/activities/{id}/toggle")
    fun toggle(
        @PathVariable id: String,
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> =
        adminCheckinPort.toggle(id, body)
}
