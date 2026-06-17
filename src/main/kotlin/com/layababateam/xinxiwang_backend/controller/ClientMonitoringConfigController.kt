package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.GroupMessageSignalClientConfigDto
import com.layababateam.xinxiwang_backend.dto.MonitoringConfigDto
import com.layababateam.xinxiwang_backend.service.ClientConfigPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/client/config")
class ClientMonitoringConfigController(
    private val clientConfigPort: ClientConfigPort,
) {
    @GetMapping("/monitoring")
    fun getMonitoringConfig(): ResponseEntity<ApiResponse<MonitoringConfigDto>> {
        return ResponseEntity.ok(ApiResponse.ok(clientConfigPort.getMonitoringConfig()))
    }

    @GetMapping("/group-message-signal")
    fun getGroupMessageSignalConfig(
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<GroupMessageSignalClientConfigDto>> {
        return ResponseEntity.ok(ApiResponse.ok(clientConfigPort.getGroupMessageSignalConfig(request)))
    }
}
