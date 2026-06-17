package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.AdminSecurityMutationResult
import com.layababateam.xinxiwang_backend.service.AdminSecurityPort
import com.layababateam.xinxiwang_backend.service.LoginSecurityAlertQuery
import com.layababateam.xinxiwang_backend.service.LoginSecurityBlockQuery
import com.layababateam.xinxiwang_backend.service.LoginSecurityBlockRequest
import com.layababateam.xinxiwang_backend.service.LoginSecurityConfigUpdateRequest
import com.layababateam.xinxiwang_backend.service.LoginSecurityEventQuery
import jakarta.servlet.http.HttpServletRequest
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
@RequestMapping("/api/admin/security")
class AdminSecurityController(
    private val adminSecurityPort: AdminSecurityPort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping("/summary")
    fun summary(): ResponseEntity<ApiResponse<Any>> =
        ResponseEntity.ok(ApiResponse.ok(adminSecurityPort.summary()))

    @RequireAdmin("ADMIN")
    @GetMapping("/events")
    fun events(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) username: String?,
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) ip: String?,
        @RequestParam(required = false) deviceId: String?,
        @RequestParam(required = false) riskLevel: String?,
        @RequestParam(required = false) startAt: Long?,
        @RequestParam(required = false) endAt: Long?,
    ): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(
            ApiResponse.ok(
                adminSecurityPort.events(
                    LoginSecurityEventQuery(
                        page = page,
                        size = size,
                        eventType = eventType,
                        username = username,
                        userId = userId,
                        ip = ip,
                        deviceId = deviceId,
                        riskLevel = riskLevel,
                        startAt = startAt,
                        endAt = endAt,
                    )
                )
            )
        )
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/alerts")
    fun alerts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) alertType: String?,
    ): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(
            ApiResponse.ok(
                adminSecurityPort.alerts(
                    LoginSecurityAlertQuery(page = page, size = size, status = status, alertType = alertType)
                )
            )
        )
    }

    @RequireAdmin("ADMIN")
    @PostMapping("/alerts/{id}/ack")
    fun ackAlert(request: HttpServletRequest, @PathVariable id: String): ResponseEntity<ApiResponse<Any>> {
        return mutation(adminSecurityPort.ackAlert(id, adminId(request)))
    }

    @RequireAdmin("ADMIN")
    @PostMapping("/alerts/{id}/resolve")
    fun resolveAlert(request: HttpServletRequest, @PathVariable id: String): ResponseEntity<ApiResponse<Any>> {
        return mutation(adminSecurityPort.resolveAlert(id, adminId(request)))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/blocks")
    fun blocks(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) value: String?,
    ): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(
            ApiResponse.ok(
                adminSecurityPort.blocks(
                    LoginSecurityBlockQuery(page = page, size = size, active = active, type = type, value = value)
                )
            )
        )
    }

    @RequireAdmin("SUPER_ADMIN")
    @PostMapping("/blocks")
    fun createBlock(
        request: HttpServletRequest,
        @RequestBody body: LoginSecurityBlockRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        if (body.reason.isBlank()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_PARAM, "封禁原因不能为空"))
        }
        return mutation(adminSecurityPort.createBlock(body, adminId(request)))
    }

    @RequireAdmin("SUPER_ADMIN")
    @DeleteMapping("/blocks/{id}")
    fun revokeBlock(request: HttpServletRequest, @PathVariable id: String): ResponseEntity<ApiResponse<Any>> {
        return mutation(adminSecurityPort.revokeBlock(id, adminId(request)))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/config")
    fun getConfig(): ResponseEntity<ApiResponse<Any>> =
        ResponseEntity.ok(ApiResponse.ok(adminSecurityPort.config()))

    @RequireAdmin("SUPER_ADMIN")
    @PutMapping("/config")
    fun updateConfig(@RequestBody body: LoginSecurityConfigUpdateRequest): ResponseEntity<ApiResponse<Any>> {
        return mutation(adminSecurityPort.updateConfig(body))
    }

    private fun mutation(result: AdminSecurityMutationResult): ResponseEntity<ApiResponse<Any>> {
        if (result.success) {
            val response = result.message?.let { ApiResponse.ok(result.data, it) }
                ?: ApiResponse.ok(result.data)
            return ResponseEntity.ok(response)
        }
        val error = ApiResponse.error<Any>(
            if (result.notFound) ErrorCode.NOT_FOUND else ErrorCode.INVALID_PARAM,
            result.errorMessage ?: "请求失败",
        )
        return if (result.notFound) {
            ResponseEntity.status(404).body(error)
        } else {
            ResponseEntity.badRequest().body(error)
        }
    }

    private fun adminId(request: HttpServletRequest): String? =
        request.getAttribute("adminId") as? String
}
