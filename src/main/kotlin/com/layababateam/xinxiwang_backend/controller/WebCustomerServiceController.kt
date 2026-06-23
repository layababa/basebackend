package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceCreateSessionRequest
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceTextMessageRequest
import com.layababateam.xinxiwang_backend.service.WebCustomerServiceService
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
@RequestMapping("/api/web-customer-service")
class WebCustomerServiceController(
    private val service: WebCustomerServiceService,
) {
    @GetMapping("/widget.js", produces = ["application/javascript"])
    fun widget(@RequestParam entryId: String): ResponseEntity<String> =
        ResponseEntity.ok()
            .contentType(MediaType.valueOf("application/javascript;charset=UTF-8"))
            .body(service.widgetScript(entryId))

    @GetMapping("/public/entries/{entryId}/bootstrap")
    fun bootstrap(
        @PathVariable entryId: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.bootstrap(entryId, request)))

    @PostMapping("/public/entries/{entryId}/sessions")
    fun createSession(
        @PathVariable entryId: String,
        @Valid @RequestBody body: WebCustomerServiceCreateSessionRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.createSession(entryId, body, request)))

    @GetMapping("/public/sessions/{sessionId}/messages")
    fun publicMessages(
        @PathVariable sessionId: String,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") size: Int,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.publicMessages(sessionId, after, size, request)))

    @PostMapping("/public/sessions/{sessionId}/messages")
    fun visitorSendText(
        @PathVariable sessionId: String,
        @Valid @RequestBody body: WebCustomerServiceTextMessageRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.visitorSendText(sessionId, body.content, request)))

    @PostMapping("/public/sessions/{sessionId}/images", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun visitorSendImage(
        @PathVariable sessionId: String,
        @RequestParam("file") file: MultipartFile,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.visitorSendImage(sessionId, file, request)))
}
