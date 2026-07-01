package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.CustomerServiceQrAssignmentService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/customer-service-qrs/public")
class CustomerServiceQrPublicController(
    private val service: CustomerServiceQrAssignmentService,
) {
    @PostMapping("/{code}/reservation")
    fun createReservation(
        request: HttpServletRequest,
        @PathVariable code: String,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.createLandingReservation(code, platform, request)))
}
