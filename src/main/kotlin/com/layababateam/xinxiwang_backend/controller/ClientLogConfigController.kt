package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.UserLogConfigPushService
import com.layababateam.xinxiwang_backend.service.UserLogConfigService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ClientLogConfigUpdateRequest(
    val criticalLogEnabled: Boolean,
    val expectedRevision: Long? = null,
)

@RestController
@RequestMapping("/api/me/log-config")
class ClientLogConfigController(
    private val userLogConfigService: UserLogConfigService,
    private val userLogConfigPushService: UserLogConfigPushService,
) {
    @GetMapping
    fun get(request: HttpServletRequest): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val userId = request.getAttribute("userId") as String
        val config = userLogConfigService.getOrDefault(userId)
        return ResponseEntity.ok(ApiResponse.ok(userLogConfigPushService.toView(userId, config)))
    }

    @PutMapping
    fun update(
        request: HttpServletRequest,
        @RequestBody body: ClientLogConfigUpdateRequest,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val userId = request.getAttribute("userId") as String
        val result = userLogConfigService.update(
            userId = userId,
            criticalLogEnabled = body.criticalLogEnabled,
            expectedRevision = body.expectedRevision,
            updatedBy = userId,
        )
        val view = userLogConfigPushService.toView(userId, result.config)
        if (!result.updated) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiResponse(
                    success = false,
                    message = "日志配置已变更，请刷新后重试",
                    data = view,
                    code = ErrorCode.INVALID_PARAM.code,
                ),
            )
        }
        userLogConfigPushService.pushToEligibleDevices(userId, result.config)
        return ResponseEntity.ok(ApiResponse.ok(userLogConfigPushService.toView(userId, result.config)))
    }
}
