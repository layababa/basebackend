package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.AdminResetPasswordRequest
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.AdminUserPort
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/users")
class AdminUserController(
    private val adminUserPort: AdminUserPort,
) {
    @RequireAdmin
    @GetMapping
    fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) accountStatus: Int?,
        @RequestParam(required = false) aiTag: String?,
        @RequestParam(required = false) isBlacklisted: Boolean?,
        @RequestParam(defaultValue = "false") includeDeleted: Boolean,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.listUsers(page, size, keyword, status, accountStatus, aiTag, isBlacklisted, includeDeleted)

    @RequireAdmin
    @GetMapping("/{id}")
    fun getUserDetail(
        @PathVariable id: String,
        @RequestParam(defaultValue = "false") includeDeleted: Boolean,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.getUserDetail(id, includeDeleted)

    @RequireAdmin
    @PutMapping("/{id}")
    fun updateUser(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.updateUser(request, id, body)

    @RequireAdmin
    @PutMapping("/{id}/login-password")
    fun resetLoginPassword(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: AdminResetPasswordRequest,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.resetLoginPassword(request, id, body)

    @RequireAdmin
    @PutMapping("/{id}/payment-password")
    fun resetPaymentPassword(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: AdminResetPasswordRequest,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.resetPaymentPassword(request, id, body)

    @RequireAdmin
    @DeleteMapping("/{id}/login-fail-count")
    fun resetLoginFailCount(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.resetLoginFailCount(request, id)

    @RequireAdmin("SUPER_ADMIN")
    @GetMapping("/{id}/password")
    fun viewPassword(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.viewPassword(request, id)

    @RequireAdmin
    @GetMapping("/{id}/sessions")
    fun getUserSessions(@PathVariable id: String): ResponseEntity<ApiResponse<*>> =
        adminUserPort.getUserSessions(id)

    @RequireAdmin
    @DeleteMapping("/{id}/sessions")
    fun forceLogout(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.forceLogout(request, id)

    @RequireAdmin
    @PutMapping("/{id}/username")
    fun changeUsername(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.changeUsername(request, id, body)

    @RequireAdmin
    @PutMapping("/{id}/account-status")
    fun updateAccountStatus(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: Map<String, Int>,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.updateAccountStatus(request, id, body)

    @RequireAdmin
    @PutMapping("/{id}/ai-tag")
    fun updateAiTag(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.updateAiTag(request, id, body)

    @RequireAdmin
    @PutMapping("/{id}/blacklist")
    fun updateBlacklist(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: Map<String, Boolean>,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.updateBlacklist(request, id, body)

    @RequireAdmin
    @GetMapping("/device-stats")
    fun getDeviceStats(): ResponseEntity<ApiResponse<*>> =
        adminUserPort.getDeviceStats()

    @RequireAdmin
    @PostMapping("/{id}/operator")
    fun toggleOperator(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<ApiResponse<*>> =
        adminUserPort.toggleOperator(request, id, body)
}
