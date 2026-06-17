package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ReportDto
import com.layababateam.xinxiwang_backend.dto.SubmitReportRequest
import com.layababateam.xinxiwang_backend.dto.UpdateReportRequest
import com.layababateam.xinxiwang_backend.service.AuthTokenResolver
import com.layababateam.xinxiwang_backend.service.ReportService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class ReportController(
    private val reportService: ReportService,
    private val authTokenResolver: AuthTokenResolver
) {

    // 用户提交举报 — 走拦截器
    @PostMapping("/report")
    fun submitReport(
        request: HttpServletRequest,
        @Valid @RequestBody body: SubmitReportRequest
    ): ResponseEntity<ApiResponse<ReportDto>> {
        val userId = request.getAttribute("userId") as String

        if (body.targetId.isBlank() || body.targetType.isBlank() || body.category.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse(false, "缺少必要参数"))
        }
        if (body.targetType !in listOf("USER", "MESSAGE")) {
            return ResponseEntity.badRequest().body(ApiResponse(false, "targetType 必须为 USER 或 MESSAGE"))
        }

        return try {
            val report = reportService.submitReport(userId, body)
            ResponseEntity.ok(ApiResponse(true, "举报已提交", report))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "提交失败"))
        }
    }

    // 管理员查询举报列表 — 走 AdminAuthInterceptor
    @GetMapping("/admin/reports")
    fun getReports(
        @RequestHeader("Authorization") token: String?,
        @RequestParam(defaultValue = "PENDING") status: String
    ): ResponseEntity<ApiResponse<List<ReportDto>>> {
        authTokenResolver.resolveUserId(token)
            ?: return ResponseEntity.status(401).body(ApiResponse(false, "未授权，请重新登录"))

        val reports = reportService.getReportsByStatus(status)
        return ResponseEntity.ok(ApiResponse(true, "OK", reports))
    }

    // 管理员处理举报 — 走 AdminAuthInterceptor
    @PutMapping("/admin/reports/{id}")
    fun updateReport(
        @RequestHeader("Authorization") token: String?,
        @PathVariable id: String,
        @Valid @RequestBody body: UpdateReportRequest
    ): ResponseEntity<ApiResponse<ReportDto>> {
        authTokenResolver.resolveUserId(token)
            ?: return ResponseEntity.status(401).body(ApiResponse(false, "未授权，请重新登录"))

        if (body.status !in listOf("RESOLVED", "REJECTED")) {
            return ResponseEntity.badRequest().body(ApiResponse(false, "status 必须为 RESOLVED 或 REJECTED"))
        }

        return try {
            val report = reportService.updateReportStatus(id, body)
            ResponseEntity.ok(ApiResponse(true, "处理成功", report))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "处理失败"))
        }
    }
}
