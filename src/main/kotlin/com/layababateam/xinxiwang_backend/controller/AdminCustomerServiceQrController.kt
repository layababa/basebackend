package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.CustomerServiceAccountRequest
import com.layababateam.xinxiwang_backend.dto.CustomerServiceQrBindingsUpdateRequest
import com.layababateam.xinxiwang_backend.dto.CustomerServiceQrCodeRequest
import com.layababateam.xinxiwang_backend.service.CustomerServiceQrAssignmentService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
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
@RequestMapping("/api/admin")
class AdminCustomerServiceQrController(
    private val service: CustomerServiceQrAssignmentService,
) {
    @RequireAdmin("ADMIN")
    @GetMapping("/customer-services")
    fun listAccounts(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) enabled: Boolean?,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.listAccounts(keyword, enabled)))

    @RequireAdmin("ADMIN")
    @PostMapping("/customer-services")
    fun createAccount(@Valid @RequestBody body: CustomerServiceAccountRequest): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.createAccount(body), "customer service account created"))

    @RequireAdmin("ADMIN")
    @PutMapping("/customer-services/{id}")
    fun updateAccount(
        @PathVariable id: String,
        @Valid @RequestBody body: CustomerServiceAccountRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.updateAccount(id, body), "customer service account updated"))

    @RequireAdmin("ADMIN")
    @GetMapping("/customer-service-qrs")
    fun listQrCodes(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.listQrCodes(request)))

    @RequireAdmin("ADMIN")
    @PostMapping("/customer-service-qrs")
    fun createQrCode(
        request: HttpServletRequest,
        @Valid @RequestBody body: CustomerServiceQrCodeRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.createQrCode(body, request, adminId(request)), "customer service qr created"))

    @RequireAdmin("ADMIN")
    @GetMapping("/customer-service-qrs/{id}")
    fun qrDetail(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.qrDetail(id, request)))

    @RequireAdmin("ADMIN")
    @PutMapping("/customer-service-qrs/{id}")
    fun updateQrCode(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: CustomerServiceQrCodeRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.updateQrCode(id, body, request), "customer service qr updated"))

    @RequireAdmin("ADMIN")
    @PutMapping("/customer-service-qrs/{id}/bindings")
    fun replaceBindings(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: CustomerServiceQrBindingsUpdateRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.replaceBindings(id, body, request), "customer service qr bindings updated"))

    private fun adminId(request: HttpServletRequest): String? =
        request.getAttribute("adminId") as? String
}
