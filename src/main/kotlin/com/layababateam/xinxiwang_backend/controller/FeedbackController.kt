package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.FeedbackDto
import com.layababateam.xinxiwang_backend.dto.SubmitFeedbackRequest
import com.layababateam.xinxiwang_backend.dto.UpdateFeedbackRequest
import com.layababateam.xinxiwang_backend.service.AuthTokenResolver
import com.layababateam.xinxiwang_backend.service.FeedbackService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class FeedbackController(
    private val feedbackService: FeedbackService,
    private val authTokenResolver: AuthTokenResolver
) {

    // 用户提交反馈 — 走拦截器
    @PostMapping("/feedback")
    fun submitFeedback(
        request: HttpServletRequest,
        @Valid @RequestBody body: SubmitFeedbackRequest
    ): ResponseEntity<ApiResponse<FeedbackDto>> {
        val userId = request.getAttribute("userId") as String

        return try {
            val feedback = feedbackService.submitFeedback(userId, body)
            ResponseEntity.ok(ApiResponse(true, "反馈已提交", feedback))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "提交失败"))
        }
    }

    // 管理员查询反馈列表 — 走 AdminAuthInterceptor
    @GetMapping("/admin/feedbacks")
    fun getFeedbacks(
        @RequestHeader("Authorization") token: String?,
        @RequestParam(defaultValue = "PENDING") status: String
    ): ResponseEntity<ApiResponse<List<FeedbackDto>>> {
        authTokenResolver.resolveUserId(token)
            ?: return ResponseEntity.status(401).body(ApiResponse(false, "未授权，请重新登录"))

        val feedbacks = feedbackService.getFeedbacksByStatus(status)
        return ResponseEntity.ok(ApiResponse(true, "OK", feedbacks))
    }

    // 管理员处理反馈（触发推送通知） — 走 AdminAuthInterceptor
    @PutMapping("/admin/feedbacks/{id}")
    fun updateFeedback(
        @RequestHeader("Authorization") token: String?,
        @PathVariable id: String,
        @Valid @RequestBody body: UpdateFeedbackRequest
    ): ResponseEntity<ApiResponse<FeedbackDto>> {
        authTokenResolver.resolveUserId(token)
            ?: return ResponseEntity.status(401).body(ApiResponse(false, "未授权，请重新登录"))

        if (body.status !in listOf("RESOLVED", "REJECTED")) {
            return ResponseEntity.badRequest().body(ApiResponse(false, "status 必须为 RESOLVED 或 REJECTED"))
        }

        return try {
            val feedback = feedbackService.updateFeedbackStatus(id, body)
            ResponseEntity.ok(ApiResponse(true, "处理成功", feedback))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "处理失败"))
        }
    }
}
