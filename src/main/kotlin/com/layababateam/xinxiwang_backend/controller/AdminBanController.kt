package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.AdminBanUserRequest
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.AdminBanPort
import com.layababateam.xinxiwang_backend.service.AdminBanResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/users")
class AdminBanController(
    private val adminBanPort: AdminBanPort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping("/banned")
    fun listBannedUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminBanPort.listBannedUsers(page, size, keyword)))
    }

    @RequireAdmin("ADMIN")
    @PostMapping("/{id}/ban")
    fun banUser(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: AdminBanUserRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val adminId = request.getAttribute("adminId") as? String
            ?: return unauthorized()
        val adminUsername = request.getAttribute("adminUsername") as? String ?: ""

        if (body.reason.isBlank()) {
            return badRequest("封禁原因不能为空")
        }
        if (body.type !in listOf("PERMANENT", "TEMPORARY", "WARNING")) {
            return badRequest("封禁类型必须为 PERMANENT、TEMPORARY 或 WARNING")
        }
        if (body.type == "TEMPORARY" && ((body.duration ?: 0) <= 0)) {
            return badRequest("临时封禁必须指定有效的封禁时长（小时）")
        }
        return response(adminBanPort.banUser(adminId, adminUsername, id, body))
    }

    @RequireAdmin("ADMIN")
    @PostMapping("/{id}/unban")
    fun unbanUser(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<Any>> {
        val adminId = request.getAttribute("adminId") as? String
            ?: return unauthorized()
        val adminUsername = request.getAttribute("adminUsername") as? String ?: ""
        return response(adminBanPort.unbanUser(adminId, adminUsername, id))
    }

    private fun unauthorized(): ResponseEntity<ApiResponse<Any>> =
        ResponseEntity.status(401).body(ApiResponse.error(ErrorCode.UNAUTHORIZED))

    private fun badRequest(message: String): ResponseEntity<ApiResponse<Any>> =
        ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_PARAM, message))

    private fun response(result: AdminBanResult): ResponseEntity<ApiResponse<Any>> {
        val body = if (result.success) {
            ApiResponse.ok(result.data, result.message ?: "OK")
        } else {
            ApiResponse.error(result.errorCode ?: ErrorCode.UNKNOWN_ERROR, result.errorMessage)
        }
        return ResponseEntity.status(result.httpStatus).body(body)
    }
}
