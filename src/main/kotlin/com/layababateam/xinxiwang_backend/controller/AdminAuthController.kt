package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.AdminConfirm2faSetupRequest
import com.layababateam.xinxiwang_backend.dto.AdminLoginRequest
import com.layababateam.xinxiwang_backend.dto.AdminRefreshTokenRequest
import com.layababateam.xinxiwang_backend.dto.AdminVerify2faRequest
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.AdminAuthPort
import com.layababateam.xinxiwang_backend.service.AdminAuthResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/auth")
class AdminAuthController(
    private val adminAuthPort: AdminAuthPort,
) {
    companion object {
        private const val MAX_TEMP_TOKEN_LENGTH = 2048
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: AdminLoginRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        if (request.username.isBlank() || request.password.isBlank()) {
            return badRequest("用户名和密码不能为空")
        }
        return response(adminAuthPort.login(request, httpRequest))
    }

    @PostMapping("/verify-2fa")
    fun verify2fa(
        @Valid @RequestBody request: AdminVerify2faRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val invalid = validateTempTokenAndCode(request.tempToken, request.code)
        if (invalid != null) return invalid
        return response(adminAuthPort.verify2fa(request, httpRequest))
    }

    @PostMapping("/2fa/setup/confirm")
    fun confirm2faSetup(
        @Valid @RequestBody request: AdminConfirm2faSetupRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val invalid = validateTempTokenAndCode(request.tempToken, request.code)
        if (invalid != null) return invalid
        return response(adminAuthPort.confirm2faSetup(request, httpRequest))
    }

    @PostMapping("/refresh")
    fun refreshToken(
        @Valid @RequestBody request: AdminRefreshTokenRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        if (request.refreshToken.isBlank()) {
            return badRequest("刷新令牌不能为空")
        }
        return response(adminAuthPort.refreshToken(request))
    }

    private fun validateTempTokenAndCode(tempToken: String, code: String): ResponseEntity<ApiResponse<Any>>? {
        if (tempToken.isBlank() || code.isBlank()) {
            return badRequest("临时令牌和验证码不能为空")
        }
        if (tempToken.length > MAX_TEMP_TOKEN_LENGTH) {
            return badRequest("临时令牌无效")
        }
        return null
    }

    private fun badRequest(message: String): ResponseEntity<ApiResponse<Any>> =
        ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_PARAM, message))

    private fun response(result: AdminAuthResult): ResponseEntity<ApiResponse<Any>> {
        val body = if (result.success) {
            ApiResponse.ok(result.data, result.message ?: "OK")
        } else {
            ApiResponse.error(result.errorCode ?: ErrorCode.UNKNOWN_ERROR, result.errorMessage)
        }
        return ResponseEntity.status(result.httpStatus).body(body)
    }
}
