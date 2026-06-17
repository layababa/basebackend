package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.layababateam.xinxiwang_backend.model.DebugLogReport
import com.layababateam.xinxiwang_backend.service.AdminDebugLogContext
import com.layababateam.xinxiwang_backend.service.AdminDebugLogPort
import com.layababateam.xinxiwang_backend.service.AdminDebugLogRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/debug-log")
class AdminDebugLogController(
    private val adminDebugLogPort: AdminDebugLogPort,
) {
    @RequireAdmin
    @PostMapping("/request")
    fun request(
        @RequestBody body: AdminDebugLogRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<DebugLogReport>> {
        if (body.userId.isBlank() || body.targetDeviceId.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "userId / targetDeviceId 不能为空")
        }

        return ResponseEntity.ok(ApiResponse.ok(adminDebugLogPort.requestLog(body, context(request))))
    }

    @RequireAdmin
    @GetMapping("/list")
    fun list(
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PagedData<DebugLogReport>>> {
        val normalizedStatus = status?.trim()?.takeIf { it.isNotBlank() && it != "ALL" }
        if (normalizedStatus != null && normalizedStatus !in ALLOWED_STATUSES) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "非法 status: $normalizedStatus")
        }

        return ResponseEntity.ok(ApiResponse.ok(adminDebugLogPort.listLogs(userId, normalizedStatus, page, size)))
    }

    @RequireAdmin
    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<ApiResponse<DebugLogReport>> {
        val report = adminDebugLogPort.getLog(id)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "记录不存在")
        return ResponseEntity.ok(ApiResponse.ok(report))
    }

    @RequireAdmin
    @GetMapping("/{id}/download")
    fun download(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val download = adminDebugLogPort.createDownload(id, context(request))
        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf(
                    "url" to download.url,
                    "expiresInSeconds" to download.expiresInSeconds,
                )
            )
        )
    }

    @RequireAdmin
    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<DebugLogReport>> {
        return ResponseEntity.ok(ApiResponse.ok(adminDebugLogPort.cancelLog(id, context(request))))
    }

    private fun context(request: HttpServletRequest): AdminDebugLogContext {
        return AdminDebugLogContext(
            adminId = request.getAttribute(ADMIN_ID_ATTR) as? String ?: "unknown",
            ip = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                ?: request.remoteAddr,
            userAgent = request.getHeader("User-Agent"),
        )
    }

    companion object {
        const val ADMIN_ID_ATTR = "adminId"

        private val ALLOWED_STATUSES = setOf(
            "pending",
            "sent",
            "acked",
            "uploaded",
            "failed",
            "timeout",
            "cancelled",
        )
    }
}
