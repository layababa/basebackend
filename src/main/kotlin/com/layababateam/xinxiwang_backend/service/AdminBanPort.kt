package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.AdminBanUserRequest
import com.layababateam.xinxiwang_backend.dto.ErrorCode

/**
 * 后台用户封禁管理端口。
 *
 * SDK 复用后台封禁路由，用户查询、通知、会话失效和审计由接入方实现。
 */
interface AdminBanPort {
    fun listBannedUsers(page: Int, size: Int, keyword: String?): Any

    fun banUser(
        adminId: String,
        adminUsername: String,
        userId: String,
        request: AdminBanUserRequest,
    ): AdminBanResult

    fun unbanUser(adminId: String, adminUsername: String, userId: String): AdminBanResult
}

data class AdminBanResult(
    val data: Any? = null,
    val message: String? = null,
    val errorCode: ErrorCode? = null,
    val errorMessage: String? = null,
    val httpStatus: Int = 200,
) {
    val success: Boolean
        get() = errorCode == null && errorMessage == null

    companion object {
        fun ok(data: Any? = null, message: String? = null): AdminBanResult =
            AdminBanResult(data = data, message = message)

        fun error(errorCode: ErrorCode, message: String, httpStatus: Int): AdminBanResult =
            AdminBanResult(errorCode = errorCode, errorMessage = message, httpStatus = httpStatus)
    }
}
