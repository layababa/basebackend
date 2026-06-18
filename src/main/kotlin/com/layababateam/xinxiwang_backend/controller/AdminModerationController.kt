package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.AdminUpdateReportRequest
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.service.AdminModerationPort
import com.layababateam.xinxiwang_backend.service.PaginationRules
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/moderation")
class AdminModerationController(
    private val adminModerationPort: AdminModerationPort,
) {

    @RequireAdmin("MODERATOR")
    @GetMapping("/reports")
    fun listReports(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PagedData<*>>> {
        val safePage = PaginationRules.zeroBasedPage(page)
        val safeSize = PaginationRules.pageSize(size, 100)
        val result = adminModerationPort.listReports(status, PageRequest.of(safePage, safeSize))
        return ResponseEntity.ok(
            ApiResponse.ok(PagedData(items = result.content, total = result.totalElements, page = safePage, size = safeSize)),
        )
    }

    @RequireAdmin("MODERATOR")
    @GetMapping("/reports/{id}")
    fun getReportDetail(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        val detail = adminModerationPort.getReportDetail(id)
            ?: return ResponseEntity.status(404).body(
                ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "举报记录不存在"),
            )
        return ResponseEntity.ok(ApiResponse.ok(detail))
    }

    @RequireAdmin("MODERATOR")
    @GetMapping("/reports/{id}/chat-history")
    fun getReportChatHistory(
        @PathVariable id: String,
        @RequestParam(required = false) before: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<*>> {
        val history = adminModerationPort.getReportChatHistory(id, before, size)
            ?: return ResponseEntity.status(404).body(
                ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "举报记录不存在"),
            )
        return ResponseEntity.ok(ApiResponse.ok(history))
    }

    @RequireAdmin("MODERATOR")
    @GetMapping("/users/{userId}/report-history")
    fun getUserReportHistory(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "reporter") role: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PagedData<*>>> {
        val safePage = PaginationRules.zeroBasedPage(page)
        val safeSize = PaginationRules.pageSize(size, 50)
        val result = adminModerationPort.getUserReportHistory(
            userId,
            role,
            PageRequest.of(safePage, safeSize),
        )
        return ResponseEntity.ok(
            ApiResponse.ok(PagedData(items = result.content, total = result.totalElements, page = safePage, size = safeSize)),
        )
    }

    @RequireAdmin("MODERATOR")
    @PutMapping("/reports/{id}")
    fun updateReport(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: AdminUpdateReportRequest,
    ): ResponseEntity<ApiResponse<*>> {
        if (body.status !in listOf("PENDING", "RESOLVED", "REJECTED")) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "状态必须为 PENDING、RESOLVED 或 REJECTED"),
            )
        }

        val updated = adminModerationPort.updateReport(
            id,
            body,
            request.getAttribute("adminId") as String,
            request.getAttribute("adminUsername") as? String ?: "",
        ) ?: return ResponseEntity.status(404).body(
            ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "举报记录不存在"),
        )

        return ResponseEntity.ok(ApiResponse.ok(updated, "举报处理成功"))
    }

    @RequireAdmin("MODERATOR")
    @GetMapping("/users/{userId}/ban-history")
    fun getUserBanHistory(@PathVariable userId: String): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(adminModerationPort.getUserBanHistory(userId)))
    }
}
