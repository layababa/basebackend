package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.CreateAdminRequest
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.UpdateAdminPasswordRequest
import com.layababateam.xinxiwang_backend.dto.UpdateAdminRoleRequest
import com.layababateam.xinxiwang_backend.dto.UpdateAdminStatusRequest
import com.layababateam.xinxiwang_backend.service.AdminActorContext
import com.layababateam.xinxiwang_backend.service.AdminManagePort
import com.layababateam.xinxiwang_backend.service.AdminManageResult
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
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/admins")
class AdminManageController(
    private val adminManagePort: AdminManagePort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping
    fun listAdmins(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        return response(adminManagePort.listAdmins(actor(request)))
    }

    @RequireAdmin("ADMIN")
    @PostMapping
    fun createAdmin(
        request: HttpServletRequest,
        @Valid @RequestBody body: CreateAdminRequest,
    ): ResponseEntity<ApiResponse<*>> {
        if (body.username.isBlank() || body.password.isBlank()) {
            return badRequest("用户名和密码不能为空")
        }
        return response(adminManagePort.createAdmin(actor(request), body))
    }

    @RequireAdmin("ADMIN")
    @PutMapping("/{id}/role")
    fun updateRole(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: UpdateAdminRoleRequest,
    ): ResponseEntity<ApiResponse<*>> {
        return response(adminManagePort.updateRole(actor(request), id, body))
    }

    @RequireAdmin("ADMIN")
    @PutMapping("/{id}/password")
    fun resetPassword(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: UpdateAdminPasswordRequest,
    ): ResponseEntity<ApiResponse<*>> {
        if (body.password.isBlank()) {
            return badRequest("密码不能为空")
        }
        return response(adminManagePort.resetPassword(actor(request), id, body))
    }

    @RequireAdmin("ADMIN")
    @PutMapping("/{id}/status")
    fun updateStatus(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: UpdateAdminStatusRequest,
    ): ResponseEntity<ApiResponse<*>> {
        return response(adminManagePort.updateStatus(actor(request), id, body))
    }

    @RequireAdmin("ADMIN")
    @DeleteMapping("/{id}")
    fun deleteAdmin(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> {
        return response(adminManagePort.deleteAdmin(actor(request), id))
    }

    @RequireAdmin("ADMIN")
    @PostMapping("/{id}/2fa/reset")
    fun reset2fa(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> {
        return response(adminManagePort.reset2fa(actor(request), id))
    }

    @RequireAdmin("ADMIN")
    @DeleteMapping("/{id}/login-fail-count")
    fun resetLoginFailCount(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> {
        return response(adminManagePort.resetLoginFailCount(actor(request), id))
    }

    private fun response(result: AdminManageResult): ResponseEntity<ApiResponse<*>> {
        if (result.success) {
            val payload = result.message?.let { ApiResponse.ok(result.data, it) }
                ?: ApiResponse.ok(result.data)
            return ResponseEntity.status(result.httpStatus).body(payload)
        }
        val error = ApiResponse.error<Any>(
            result.errorCode ?: ErrorCode.UNKNOWN_ERROR,
            result.errorMessage ?: "请求失败",
        )
        return ResponseEntity.status(result.httpStatus).body(error)
    }

    private fun badRequest(message: String): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, message))

    private fun actor(request: HttpServletRequest): AdminActorContext =
        AdminActorContext(
            adminId = request.getAttribute("adminId") as? String ?: "",
            adminUsername = request.getAttribute("adminUsername") as? String ?: "",
            role = request.getAttribute("adminRole") as? String ?: ROLE_MODERATOR,
        )

    companion object {
        private const val ROLE_MODERATOR = "MODERATOR"
    }
}
