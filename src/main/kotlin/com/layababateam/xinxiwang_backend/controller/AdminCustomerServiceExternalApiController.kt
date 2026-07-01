package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.CustomerServiceExternalApiCredentialRequest
import com.layababateam.xinxiwang_backend.service.CustomerServiceExternalApiCredentialService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/customer-service-external-api")
class AdminCustomerServiceExternalApiController(
    private val service: CustomerServiceExternalApiCredentialService,
) {
    @RequireAdmin("ADMIN")
    @GetMapping("/credentials")
    fun listCredentials(): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.listCredentials()))

    @RequireAdmin("ADMIN")
    @PostMapping("/credentials")
    fun createCredential(
        request: HttpServletRequest,
        @Valid @RequestBody body: CustomerServiceExternalApiCredentialRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.createCredential(body, adminId(request)), "external api credential created"))

    @RequireAdmin("ADMIN")
    @PutMapping("/credentials/{id}")
    fun updateCredential(
        @PathVariable id: String,
        @Valid @RequestBody body: CustomerServiceExternalApiCredentialRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.updateCredential(id, body), "external api credential updated"))

    @RequireAdmin("ADMIN")
    @PostMapping("/credentials/{id}/rotate-secret")
    fun rotateSecret(@PathVariable id: String): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.rotateSecret(id), "external api secret rotated"))

    private fun adminId(request: HttpServletRequest): String? =
        request.getAttribute("adminId") as? String
}
