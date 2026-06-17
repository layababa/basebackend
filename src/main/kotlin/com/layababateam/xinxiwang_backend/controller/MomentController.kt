package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.AddCommentRequest
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.MomentDto
import com.layababateam.xinxiwang_backend.dto.PublishMomentRequest
import com.layababateam.xinxiwang_backend.dto.RelationSettingDto
import com.layababateam.xinxiwang_backend.dto.TimelineResponse
import com.layababateam.xinxiwang_backend.dto.UpdateGlobalPrivacyRequest
import com.layababateam.xinxiwang_backend.dto.UpdateRelationSettingRequest
import com.layababateam.xinxiwang_backend.service.MomentPort
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/moments")
class MomentController(
    private val momentPort: MomentPort,
) {
    @PostMapping("/publish")
    fun publishMoment(
        request: HttpServletRequest,
        @Valid @RequestBody body: PublishMomentRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val response = momentPort.publishMoment(userId(request), body)
        return response.toWriteResponse()
    }

    @DeleteMapping("/{momentId}")
    fun deleteMoment(
        request: HttpServletRequest,
        @PathVariable momentId: String,
    ): ResponseEntity<ApiResponse<*>> {
        val response = momentPort.deleteMoment(userId(request), momentId)
        return response.toWriteResponse()
    }

    @PostMapping("/{momentId}/like")
    fun likeMoment(
        request: HttpServletRequest,
        @PathVariable momentId: String,
    ): ResponseEntity<ApiResponse<*>> {
        val response = momentPort.likeMoment(userId(request), momentId)
        return response.toWriteResponse()
    }

    @PostMapping("/{momentId}/unlike")
    fun unlikeMoment(
        request: HttpServletRequest,
        @PathVariable momentId: String,
    ): ResponseEntity<ApiResponse<*>> {
        val response = momentPort.unlikeMoment(userId(request), momentId)
        return response.toWriteResponse()
    }

    @PostMapping("/comment")
    fun addComment(
        request: HttpServletRequest,
        @Valid @RequestBody body: AddCommentRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val response = momentPort.addComment(userId(request), body)
        return response.toWriteResponse()
    }

    @DeleteMapping("/comment/{commentId}")
    fun deleteComment(
        request: HttpServletRequest,
        @PathVariable commentId: String,
    ): ResponseEntity<ApiResponse<*>> {
        val response = momentPort.deleteComment(userId(request), commentId)
        return response.toWriteResponse()
    }

    @GetMapping("/unread-count")
    fun getUnreadCount(request: HttpServletRequest): ResponseEntity<ApiResponse<Map<String, Int>>> =
        ResponseEntity.ok(ApiResponse.ok(mapOf("count" to momentPort.getUnreadCount(userId(request)))))

    @PostMapping("/unread-count/clear")
    fun clearUnreadCount(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        momentPort.clearUnreadCount(userId(request))
        return ResponseEntity.ok(ApiResponse.ok<Any>(message = "已清除"))
    }

    @GetMapping("/latest-timestamp")
    fun getLatestTimestamp(request: HttpServletRequest): ResponseEntity<ApiResponse<Map<String, Long>>> =
        ResponseEntity.ok(
            ApiResponse.ok(mapOf("latestTimestamp" to momentPort.getLatestMomentTimestamp(userId(request)))),
        )

    @GetMapping("/timeline")
    fun getTimeline(
        request: HttpServletRequest,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<ApiResponse<TimelineResponse>> =
        ResponseEntity.ok(ApiResponse.ok(momentPort.getTimeline(userId(request), page, size)))

    @GetMapping("/user/{targetUserId}")
    fun getUserMoments(
        request: HttpServletRequest,
        @PathVariable targetUserId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<ApiResponse<List<MomentDto>>> =
        ResponseEntity.ok(ApiResponse.ok(momentPort.getUserMoments(userId(request), targetUserId, page, size)))

    @PostMapping("/privacy/global")
    fun updateGlobalPrivacy(
        request: HttpServletRequest,
        @Valid @RequestBody body: UpdateGlobalPrivacyRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val response = momentPort.updateGlobalPrivacy(userId(request), body)
        return response.toWriteResponse()
    }

    @GetMapping("/privacy/relation/{targetUserId}")
    fun getRelationSetting(
        request: HttpServletRequest,
        @PathVariable targetUserId: String,
    ): ResponseEntity<ApiResponse<RelationSettingDto>> =
        ResponseEntity.ok(ApiResponse.ok(momentPort.getRelationSetting(userId(request), targetUserId)))

    @PostMapping("/privacy/relation")
    fun updateRelationSetting(
        request: HttpServletRequest,
        @Valid @RequestBody body: UpdateRelationSettingRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val response = momentPort.updateRelationSetting(userId(request), body)
        return response.toWriteResponse()
    }

    private fun userId(request: HttpServletRequest): String =
        request.getAttribute("userId") as String

    private fun ApiResponse<*>.toWriteResponse(): ResponseEntity<ApiResponse<*>> =
        if (success) ResponseEntity.ok(this) else ResponseEntity.badRequest().body(this)
}
