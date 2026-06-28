package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceTextMessageRequest
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSessionStatus
import com.layababateam.xinxiwang_backend.service.CustomerServiceWorkbenchService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/customer-service/workbench")
class CustomerServiceWorkbenchController(
    private val service: CustomerServiceWorkbenchService,
) {
    @GetMapping("/profile")
    fun profile(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.profile(userId(request))))

    @GetMapping("/sessions")
    fun listSessions(
        request: HttpServletRequest,
        @RequestParam(required = false) status: WebCustomerServiceSessionStatus?,
        @RequestParam(defaultValue = "all") assigned: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.listSessions(userId(request), status, assigned, page, size)))

    @GetMapping("/sessions/{id}/messages")
    fun messages(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestParam(required = false) before: String?,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.messages(userId(request), id, before, size)))

    @PostMapping("/sessions/{id}/claim")
    fun claim(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.claim(userId(request), id), "已接待"))

    @PostMapping("/sessions/{id}/messages")
    fun sendText(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: WebCustomerServiceTextMessageRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.sendText(userId(request), id, body.content)))

    @PostMapping("/sessions/{id}/images", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun sendImage(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.sendImage(userId(request), id, file)))

    @PostMapping("/sessions/{id}/release")
    fun release(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.release(userId(request), id), "已释放回队列"))

    @PostMapping("/sessions/{id}/close")
    fun close(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.close(userId(request), id), "会话已关闭"))

    private fun userId(request: HttpServletRequest): String =
        request.getAttribute("userId") as String
}
