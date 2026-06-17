package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.CreateAdminRequest
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.UpdateAdminPasswordRequest
import com.layababateam.xinxiwang_backend.dto.UpdateAdminRoleRequest
import com.layababateam.xinxiwang_backend.dto.UpdateAdminStatusRequest

/**
 * 后台管理员管理端口。
 *
 * SDK 复用管理员管理 HTTP 契约，角色层级、仓库读写、审计和登录失败计数清理由接入方实现。
 */
interface AdminManagePort {
    fun listAdmins(actor: AdminActorContext): AdminManageResult

    fun createAdmin(actor: AdminActorContext, request: CreateAdminRequest): AdminManageResult

    fun updateRole(actor: AdminActorContext, id: String, request: UpdateAdminRoleRequest): AdminManageResult

    fun resetPassword(actor: AdminActorContext, id: String, request: UpdateAdminPasswordRequest): AdminManageResult

    fun updateStatus(actor: AdminActorContext, id: String, request: UpdateAdminStatusRequest): AdminManageResult

    fun deleteAdmin(actor: AdminActorContext, id: String): AdminManageResult

    fun reset2fa(actor: AdminActorContext, id: String): AdminManageResult

    fun resetLoginFailCount(actor: AdminActorContext, id: String): AdminManageResult
}

data class AdminActorContext(
    val adminId: String,
    val adminUsername: String,
    val role: String,
)

data class AdminManageResult(
    val data: Any? = null,
    val message: String? = null,
    val errorCode: ErrorCode? = null,
    val errorMessage: String? = null,
    val httpStatus: Int = 200,
) {
    val success: Boolean
        get() = errorCode == null

    companion object {
        fun ok(data: Any? = null, message: String? = null): AdminManageResult =
            AdminManageResult(data = data, message = message)

        fun error(errorCode: ErrorCode, message: String, httpStatus: Int = 400): AdminManageResult =
            AdminManageResult(errorCode = errorCode, errorMessage = message, httpStatus = httpStatus)
    }
}
