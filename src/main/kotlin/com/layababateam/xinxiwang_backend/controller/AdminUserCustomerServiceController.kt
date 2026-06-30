package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.AdminUserCustomerServiceService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/users")
class AdminUserCustomerServiceController(
    private val service: AdminUserCustomerServiceService,
) {
    @RequireAdmin("ADMIN")
    @PostMapping("/{id}/customer-service")
    fun toggleCustomerService(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<ApiResponse<*>> =
        service.toggleCustomerService(
            adminId = request.getAttribute("adminId") as String,
            adminUsername = request.getAttribute("adminUsername") as? String ?: "",
            userId = id,
            body = body,
            ipAddress = request.remoteAddr,
        )
}
