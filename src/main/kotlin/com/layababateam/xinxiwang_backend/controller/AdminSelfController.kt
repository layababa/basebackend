package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.AdminChangePasswordRequest
import com.layababateam.xinxiwang_backend.dto.AdminConfirmSelf2faSetupRequest
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.AdminSelfPort
import com.layababateam.xinxiwang_backend.service.AdminSelfRequestContext
import com.layababateam.xinxiwang_backend.service.AdminSelfResult
import com.layababateam.xinxiwang_backend.service.StringValueRules
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/self")
class AdminSelfController(
    private val adminSelfPort: AdminSelfPort,
) {
    @GetMapping("/profile")
    @RequireAdmin("MODERATOR")
    fun getProfile(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        return response(adminSelfPort.profile(adminId(request)))
    }

    @PutMapping("/password")
    @RequireAdmin("MODERATOR")
    fun changePassword(
        request: HttpServletRequest,
        @Valid @RequestBody body: AdminChangePasswordRequest,
    ): ResponseEntity<ApiResponse<*>> {
        if (body.oldPassword.isBlank() || body.newPassword.isBlank()) {
            return badRequest(ErrorCode.INVALID_PARAM, "旧密码和新密码不能为空")
        }
        if (body.newPassword.length < 6) {
            return badRequest(ErrorCode.INVALID_PARAM, "新密码长度不能少于6位")
        }
        return response(adminSelfPort.changePassword(adminId(request), body))
    }

    @PostMapping("/2fa/setup")
    @RequireAdmin("MODERATOR")
    fun setup2fa(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        return response(adminSelfPort.begin2faSetup(adminId(request)))
    }

    @PostMapping("/2fa/confirm")
    @RequireAdmin("MODERATOR")
    fun confirm2fa(
        request: HttpServletRequest,
        @Valid @RequestBody body: AdminConfirmSelf2faSetupRequest,
    ): ResponseEntity<ApiResponse<*>> {
        return response(
            adminSelfPort.confirm2faSetup(
                adminId = adminId(request),
                adminUsername = adminUsername(request),
                request = body,
                context = context(request),
            )
        )
    }

    private fun response(result: AdminSelfResult): ResponseEntity<ApiResponse<*>> {
        if (result.success) {
            val payload = result.message?.let { ApiResponse.ok(result.data, it) }
                ?: ApiResponse.ok(result.data)
            return ResponseEntity.status(result.httpStatus).body(payload)
        }
        val error = ApiResponse.error<Nothing>(
            result.errorCode ?: ErrorCode.UNKNOWN_ERROR,
            result.errorMessage ?: "请求失败",
        )
        return ResponseEntity.status(result.httpStatus).body(error)
    }

    private fun badRequest(errorCode: ErrorCode, message: String): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.badRequest().body(ApiResponse.error<Nothing>(errorCode, message))

    private fun adminId(request: HttpServletRequest): String =
        request.getAttribute("adminId") as String

    private fun adminUsername(request: HttpServletRequest): String =
        request.getAttribute("adminUsername") as? String ?: ""

    private fun context(request: HttpServletRequest): AdminSelfRequestContext =
        AdminSelfRequestContext(
            remoteAddr = request.remoteAddr,
            forwardedFor = request.getHeader("X-Forwarded-For"),
            realIp = request.getHeader("X-Real-IP"),
            forwarded = request.getHeader("Forwarded"),
            userAgent = request.getHeader("User-Agent"),
            deviceId = StringValueRules.nonBlank(request.getHeader("X-Admin-Device-Id"), max = 128),
        )
}
