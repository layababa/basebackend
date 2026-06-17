package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.AdminChangePasswordRequest
import com.layababateam.xinxiwang_backend.dto.AdminConfirmSelf2faSetupRequest
import com.layababateam.xinxiwang_backend.dto.ErrorCode

/**
 * 后台管理员个人中心端口。
 *
 * SDK 复用个人资料、改密和自助 2FA HTTP 契约，管理员认证、Redis 限流和审计由接入方实现。
 */
interface AdminSelfPort {
    fun profile(adminId: String): AdminSelfResult

    fun changePassword(adminId: String, request: AdminChangePasswordRequest): AdminSelfResult

    fun begin2faSetup(adminId: String): AdminSelfResult

    fun confirm2faSetup(
        adminId: String,
        adminUsername: String,
        request: AdminConfirmSelf2faSetupRequest,
        context: AdminSelfRequestContext,
    ): AdminSelfResult
}

data class AdminSelfRequestContext(
    val remoteAddr: String?,
    val forwardedFor: String?,
    val realIp: String?,
    val forwarded: String?,
    val userAgent: String?,
    val deviceId: String?,
)

data class AdminSelfResult(
    val data: Any? = null,
    val message: String? = null,
    val errorCode: ErrorCode? = null,
    val errorMessage: String? = null,
    val httpStatus: Int = 200,
) {
    val success: Boolean
        get() = errorCode == null

    companion object {
        fun ok(data: Any? = null, message: String? = null): AdminSelfResult =
            AdminSelfResult(data = data, message = message)

        fun error(errorCode: ErrorCode, message: String, httpStatus: Int = 400): AdminSelfResult =
            AdminSelfResult(errorCode = errorCode, errorMessage = message, httpStatus = httpStatus)
    }
}
