package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ApnsTokenRequest
import com.layababateam.xinxiwang_backend.dto.PushDaActiveRequest
import com.layababateam.xinxiwang_backend.dto.PushDaBindingStatusDto
import com.layababateam.xinxiwang_backend.service.PushPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/push")
class PushController(
    private val pushPort: PushPort,
) {
    @PostMapping("/apns-token")
    fun registerApnsToken(
        request: HttpServletRequest,
        @RequestBody body: ApnsTokenRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val registered = pushPort.registerApnsToken(
            userId = request.userId(),
            authToken = request.authToken(),
            request = body,
        )
        if (!registered) {
            return ResponseEntity.badRequest().body(ApiResponse.error("会话不存在"))
        }
        return ResponseEntity.ok(ApiResponse.ok(message = "APNs token 注册成功"))
    }

    @PostMapping("/apns-token/clear")
    fun clearApnsToken(request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        pushPort.clearApnsToken(request.userId(), request.authToken())
        return ResponseEntity.ok(ApiResponse.ok(message = "APNs token 已清除"))
    }

    @PostMapping("/voip-token/clear")
    fun clearVoipToken(request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        pushPort.clearVoipToken(request.userId())
        return ResponseEntity.ok(ApiResponse.ok(message = "VoIP token 已清除"))
    }

    @PostMapping("/apns-token/clear-apns")
    fun clearApnsOnly(request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        pushPort.clearApnsOnly(request.userId(), request.authToken())
        return ResponseEntity.ok(ApiResponse.ok(message = "APNs token 已清除"))
    }

    @GetMapping("/pushda/binding-status")
    fun getPushDaBindingStatus(request: HttpServletRequest): ResponseEntity<ApiResponse<PushDaBindingStatusDto>> {
        return ResponseEntity.ok(ApiResponse.ok(data = pushPort.getPushDaBindingStatus(request.userId())))
    }

    @GetMapping("/pushda/store-links")
    fun getPushDaStoreLinks(): ResponseEntity<ApiResponse<Map<String, String>>> {
        return ResponseEntity.ok(ApiResponse.ok(data = pushPort.getPushDaStoreLinks()))
    }

    @PostMapping("/pushda/active")
    fun reportPushDaActive(@RequestBody body: PushDaActiveRequest): ResponseEntity<ApiResponse<Nothing>> {
        pushPort.reportPushDaActive(body.bindingUid)
        return ResponseEntity.ok(ApiResponse.ok(message = "已上报"))
    }

    private fun HttpServletRequest.userId(): String = getAttribute("userId") as String

    private fun HttpServletRequest.authToken(): String = getAttribute("authToken") as String
}
