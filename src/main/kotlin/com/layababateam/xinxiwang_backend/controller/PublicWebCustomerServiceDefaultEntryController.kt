package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.exception.NotFoundException
import com.layababateam.xinxiwang_backend.service.WebCustomerServiceService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/web-customer-service/public")
class PublicWebCustomerServiceDefaultEntryController(
    private val service: WebCustomerServiceService,
) {
    @GetMapping("/default-entry/bootstrap")
    fun defaultBootstrap(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        return try {
            ResponseEntity.ok(ApiResponse.ok(service.defaultBootstrap(request)))
        } catch (_: NotFoundException) {
            ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "客服入口未配置"))
        }
    }
}
