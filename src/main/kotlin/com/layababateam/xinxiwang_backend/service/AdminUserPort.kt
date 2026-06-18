package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.AdminResetPasswordRequest
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity

/** 后台用户管理能力端口。SDK 负责路由和权限注解，接入方负责查询、审计和会话处理。 */
interface AdminUserPort {
    fun listUsers(
        page: Int,
        size: Int,
        keyword: String?,
        status: String?,
        accountStatus: Int?,
        aiTag: String?,
        isBlacklisted: Boolean?,
        includeDeleted: Boolean,
    ): ResponseEntity<ApiResponse<*>>

    fun getUserDetail(id: String, includeDeleted: Boolean): ResponseEntity<ApiResponse<*>>

    fun updateUser(request: HttpServletRequest, id: String, body: Map<String, Any>): ResponseEntity<ApiResponse<*>>

    fun resetLoginPassword(
        request: HttpServletRequest,
        id: String,
        body: AdminResetPasswordRequest,
    ): ResponseEntity<ApiResponse<*>>

    fun resetPaymentPassword(
        request: HttpServletRequest,
        id: String,
        body: AdminResetPasswordRequest,
    ): ResponseEntity<ApiResponse<*>>

    fun resetLoginFailCount(request: HttpServletRequest, id: String): ResponseEntity<ApiResponse<*>>

    fun viewPassword(request: HttpServletRequest, id: String): ResponseEntity<ApiResponse<*>>

    fun getUserSessions(id: String): ResponseEntity<ApiResponse<*>>

    fun forceLogout(request: HttpServletRequest, id: String): ResponseEntity<ApiResponse<*>>

    fun changeUsername(
        request: HttpServletRequest,
        id: String,
        body: Map<String, String>,
    ): ResponseEntity<ApiResponse<*>>

    fun updateAccountStatus(
        request: HttpServletRequest,
        id: String,
        body: Map<String, Int>,
    ): ResponseEntity<ApiResponse<*>>

    fun updateAiTag(request: HttpServletRequest, id: String, body: Map<String, String>): ResponseEntity<ApiResponse<*>>

    fun updateBlacklist(
        request: HttpServletRequest,
        id: String,
        body: Map<String, Boolean>,
    ): ResponseEntity<ApiResponse<*>>

    fun getDeviceStats(): ResponseEntity<ApiResponse<*>>

    fun toggleOperator(request: HttpServletRequest, id: String, body: Map<String, Any>): ResponseEntity<ApiResponse<*>>
}
