package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ApplyViaQrRequest
import com.layababateam.xinxiwang_backend.dto.GenerateGroupQrRequest
import com.layababateam.xinxiwang_backend.dto.GenerateGroupQrResponse
import com.layababateam.xinxiwang_backend.dto.GroupInviteInfo
import com.layababateam.xinxiwang_backend.dto.InviteResult
import com.layababateam.xinxiwang_backend.dto.UserInviteInfo
import com.layababateam.xinxiwang_backend.service.InvitePort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/invite")
class InviteController(
    private val invitePort: InvitePort,
) {
    @GetMapping("/user/{userId}")
    fun getUserInviteInfo(@PathVariable userId: String): ResponseEntity<ApiResponse<UserInviteInfo>> {
        return toResponse(invitePort.getUserInviteInfo(userId))
    }

    @GetMapping("/group/{encryptedGroupId}")
    fun getGroupInviteInfo(@PathVariable encryptedGroupId: String): ResponseEntity<ApiResponse<GroupInviteInfo>> {
        return toResponse(invitePort.getGroupInviteInfo(encryptedGroupId))
    }

    @PostMapping("/generate-group-qr")
    fun generateGroupQr(
        request: HttpServletRequest,
        @RequestBody body: GenerateGroupQrRequest,
    ): ResponseEntity<ApiResponse<GenerateGroupQrResponse>> {
        val userId = request.getAttribute("userId") as String
        return toResponse(invitePort.generateGroupQr(userId, body.conversationId))
    }

    @PostMapping("/apply-via-qr")
    fun applyViaQr(
        request: HttpServletRequest,
        @RequestBody body: ApplyViaQrRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val userId = request.getAttribute("userId") as String
        return toResponse(invitePort.applyViaQr(userId, body.encryptedGroupId))
    }

    private fun <T> toResponse(result: InviteResult<T>): ResponseEntity<ApiResponse<T>> {
        val body = ApiResponse(success = result.success, message = result.message, data = result.data)
        return if (result.success) {
            ResponseEntity.ok(body)
        } else {
            ResponseEntity.badRequest().body(body)
        }
    }
}
