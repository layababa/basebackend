package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.AdminFeedbackReplyRequest
import com.layababateam.xinxiwang_backend.dto.AdminUpdateFeedbackRequest
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.model.Feedback
import com.layababateam.xinxiwang_backend.service.AdminFeedbackPort
import com.layababateam.xinxiwang_backend.service.AuditLogPort
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
@RequestMapping("/api/admin/feedback")
class AdminFeedbackController(
    private val adminFeedbackPort: AdminFeedbackPort,
    private val auditLogPort: AuditLogPort,
) {
    @RequireAdmin("MODERATOR")
    @GetMapping
    fun listFeedback(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PagedData<Feedback>>> {
        return ResponseEntity.ok(ApiResponse.ok(adminFeedbackPort.listFeedback(status, page, size)))
    }

    @RequireAdmin("MODERATOR")
    @GetMapping("/{id}")
    fun getFeedbackDetail(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        val feedback = adminFeedbackPort.findFeedback(id)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "反馈记录不存在"))
        return ResponseEntity.ok(ApiResponse.ok(feedback))
    }

    @RequireAdmin("MODERATOR")
    @PutMapping("/{id}")
    fun updateFeedback(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: AdminUpdateFeedbackRequest,
    ): ResponseEntity<ApiResponse<*>> {
        if (body.status !in VALID_STATUSES) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "状态必须为 PENDING、RESOLVED 或 REJECTED"),
            )
        }

        val updated = adminFeedbackPort.updateFeedback(id, body)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "反馈记录不存在"))

        auditLogPort.recordAudit(
            adminId = adminId(request),
            adminUsername = adminUsername(request),
            action = "UPDATE_FEEDBACK",
            targetType = "FEEDBACK",
            targetId = id,
            details = "更新反馈状态: ${body.status}" + if (body.rewardPoints > 0) "，奖励积分: ${body.rewardPoints}" else "",
            ipAddress = null,
        )

        return ResponseEntity.ok(ApiResponse.ok(updated, "反馈处理成功"))
    }

    @RequireAdmin("MODERATOR")
    @PostMapping("/{id}/reply")
    fun replyToFeedback(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: AdminFeedbackReplyRequest,
    ): ResponseEntity<ApiResponse<*>> {
        if (body.content.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "回复内容不能为空"))
        }
        if (body.content.length > 500) {
            return ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "回复内容不能超过500字"))
        }

        val adminId = adminId(request)
        val adminUsername = adminUsername(request)
        val updated = adminFeedbackPort.replyToFeedback(id, adminId, adminUsername, body)
            ?: return ResponseEntity.status(404).body(ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "反馈记录不存在"))

        auditLogPort.recordAudit(
            adminId = adminId,
            adminUsername = adminUsername,
            action = "REPLY_FEEDBACK",
            targetType = "FEEDBACK",
            targetId = id,
            details = "回复反馈",
            ipAddress = null,
        )

        return ResponseEntity.ok(ApiResponse.ok(updated, "回复成功"))
    }

    private fun adminId(request: HttpServletRequest): String = request.getAttribute("adminId") as String

    private fun adminUsername(request: HttpServletRequest): String =
        request.getAttribute("adminUsername") as? String ?: ""

    companion object {
        private val VALID_STATUSES = setOf("PENDING", "RESOLVED", "REJECTED")
    }
}
