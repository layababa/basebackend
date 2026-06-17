package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.AdminConfirm2faSetupRequest
import com.layababateam.xinxiwang_backend.dto.AdminLoginRequest
import com.layababateam.xinxiwang_backend.dto.AdminRefreshTokenRequest
import com.layababateam.xinxiwang_backend.dto.AdminVerify2faRequest
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import jakarta.servlet.http.HttpServletRequest

/**
 * 后台管理员认证端口。
 *
 * 认证、2FA 限流、审计、令牌签发和请求元数据解析由接入方实现；
 * SDK 仅复用 `/api/admin/auth` HTTP 契约。
 */
interface AdminAuthPort {
    fun login(request: AdminLoginRequest, httpRequest: HttpServletRequest): AdminAuthResult

    fun verify2fa(request: AdminVerify2faRequest, httpRequest: HttpServletRequest): AdminAuthResult

    fun confirm2faSetup(request: AdminConfirm2faSetupRequest, httpRequest: HttpServletRequest): AdminAuthResult

    fun refreshToken(request: AdminRefreshTokenRequest): AdminAuthResult
}

data class AdminAuthResult(
    val data: Any? = null,
    val message: String? = null,
    val errorCode: ErrorCode? = null,
    val errorMessage: String? = null,
    val httpStatus: Int = 200,
) {
    val success: Boolean
        get() = errorCode == null && errorMessage == null

    companion object {
        fun ok(data: Any? = null, message: String? = null): AdminAuthResult =
            AdminAuthResult(data = data, message = message)

        fun error(errorCode: ErrorCode, message: String, httpStatus: Int): AdminAuthResult =
            AdminAuthResult(errorCode = errorCode, errorMessage = message, httpStatus = httpStatus)
    }
}
