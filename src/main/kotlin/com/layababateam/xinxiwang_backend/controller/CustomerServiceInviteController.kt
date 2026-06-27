package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.CustomerServiceQrApplyRequest
import com.layababateam.xinxiwang_backend.service.CustomerServiceQrAssignmentService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/invite/customer-service")
class CustomerServiceInviteController(
    private val service: CustomerServiceQrAssignmentService,
) {
    @PostMapping("/apply")
    fun apply(
        request: HttpServletRequest,
        @Valid @RequestBody body: CustomerServiceQrApplyRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(service.apply(body.code, userId), "customer service assigned"))
    }
}
