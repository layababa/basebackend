package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceEntryRequest
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceTextMessageRequest
import com.layababateam.xinxiwang_backend.exception.NotFoundException
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSessionStatus
import com.layababateam.xinxiwang_backend.service.WebCustomerServiceService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/admin/web-customer-service")
class AdminWebCustomerServiceController(
    private val service: WebCustomerServiceService,
) {
    @RequireAdmin("ADMIN")
    @GetMapping("/entries")
    fun listEntries(): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.listEntries()))

    @RequireAdmin("ADMIN")
    @PostMapping("/entries")
    fun createEntry(
        request: HttpServletRequest,
        @Valid @RequestBody body: WebCustomerServiceEntryRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.createEntry(body, adminId(request)), "客服入口已创建"))

    @RequireAdmin("ADMIN")
    @PutMapping("/entries/{id}")
    fun updateEntry(
        @PathVariable id: String,
        @Valid @RequestBody body: WebCustomerServiceEntryRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.updateEntry(id, body), "客服入口已更新"))

    @RequireAdmin("ADMIN")
    @DeleteMapping("/entries/{id}")
    fun deleteEntry(@PathVariable id: String): ResponseEntity<ApiResponse<*>> =
        try {
            service.deleteEntry(id)
            ResponseEntity.ok(ApiResponse.ok<Nothing>(message = "客服入口已删除"))
        } catch (e: NotFoundException) {
            ResponseEntity.status(404)
                .body(ApiResponse.error<Nothing>(ErrorCode.NOT_FOUND, e.message))
        }

    @RequireAdmin("ADMIN")
    @GetMapping("/entries/{id}/script")
    fun script(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.script(id, request)))

    @RequireAdmin("MODERATOR")
    @GetMapping("/sessions")
    fun listSessions(
        request: HttpServletRequest,
        @RequestParam(required = false) entryId: String?,
        @RequestParam(required = false) status: WebCustomerServiceSessionStatus?,
        @RequestParam(defaultValue = "all") assigned: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.listSessions(entryId, status, assigned, adminId(request), page, size)))

    @RequireAdmin("MODERATOR")
    @GetMapping("/sessions/{id}/messages")
    fun messages(
        @PathVariable id: String,
        @RequestParam(required = false) before: String?,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.adminMessages(id, before, size)))

    @RequireAdmin("MODERATOR")
    @PostMapping("/sessions/{id}/claim")
    fun claim(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.claim(id, adminId(request), adminUsername(request)), "已接待"))

    @RequireAdmin("MODERATOR")
    @PostMapping("/sessions/{id}/messages")
    fun sendText(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: WebCustomerServiceTextMessageRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.adminSendText(id, adminId(request), adminUsername(request), body.content)))

    @RequireAdmin("MODERATOR")
    @PostMapping("/sessions/{id}/images", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun sendImage(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.adminSendImage(id, adminId(request), adminUsername(request), file)))

    @RequireAdmin("MODERATOR")
    @PostMapping("/sessions/{id}/release")
    fun release(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.release(id, adminId(request), adminRole(request)), "已释放回队列"))

    @RequireAdmin("MODERATOR")
    @PostMapping("/sessions/{id}/close")
    fun close(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(service.close(id, adminId(request), adminRole(request)), "会话已关闭"))

    private fun adminId(request: HttpServletRequest): String =
        request.getAttribute("adminId") as String

    private fun adminUsername(request: HttpServletRequest): String =
        request.getAttribute("adminUsername") as? String ?: ""

    private fun adminRole(request: HttpServletRequest): String =
        request.getAttribute("adminRole") as? String ?: "MODERATOR"
}
