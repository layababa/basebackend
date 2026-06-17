package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.AdminClientMutationResult
import com.layababateam.xinxiwang_backend.service.AdminClientPort
import com.layababateam.xinxiwang_backend.service.AppVersionUpdateRequest
import com.layababateam.xinxiwang_backend.service.ForceUpdateRuleRequest
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
@RequestMapping("/api/admin/clients")
class AdminClientController(
    private val adminClientPort: AdminClientPort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping("/online")
    fun getOnlineStats(): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminClientPort.onlineStats()))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/online-trend")
    fun getOnlineTrend(): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminClientPort.onlineTrend()))
    }

    @RequireAdmin("SUPER_ADMIN")
    @GetMapping("/force-update-rules")
    fun getRules(): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminClientPort.forceUpdateRules()))
    }

    @RequireAdmin("SUPER_ADMIN")
    @PostMapping("/force-update-rules")
    fun upsertRule(@RequestBody request: ForceUpdateRuleRequest): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminClientPort.upsertForceUpdateRule(request)))
    }

    @RequireAdmin("SUPER_ADMIN")
    @DeleteMapping("/force-update-rules/{id}")
    fun deleteRule(@PathVariable id: String): ResponseEntity<ApiResponse<Any>> {
        return mutation(adminClientPort.deleteForceUpdateRule(id))
    }

    @RequireAdmin("SUPER_ADMIN")
    @PostMapping("/force-update-rules/{id}/kick")
    fun kickOutdatedClients(@PathVariable id: String): ResponseEntity<ApiResponse<Any>> {
        return mutation(adminClientPort.kickOutdatedClients(id))
    }

    @RequireAdmin("SUPER_ADMIN")
    @GetMapping("/app-versions")
    fun getAppVersions(): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminClientPort.appVersions()))
    }

    @RequireAdmin("SUPER_ADMIN")
    @PutMapping("/app-versions/{platform}")
    fun updateAppVersion(
        @PathVariable platform: String,
        @RequestBody request: AppVersionUpdateRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        return mutation(adminClientPort.updateAppVersion(platform, request))
    }

    @RequireAdmin("SUPER_ADMIN")
    @DeleteMapping("/app-versions/{platform}")
    fun deleteAppVersion(@PathVariable platform: String): ResponseEntity<ApiResponse<Any>> {
        return mutation(adminClientPort.deleteAppVersion(platform))
    }

    private fun mutation(result: AdminClientMutationResult): ResponseEntity<ApiResponse<Any>> {
        return if (result.success) {
            ResponseEntity.ok(ApiResponse.ok(result.data))
        } else {
            ResponseEntity.badRequest().body(ApiResponse.error(result.errorMessage ?: "请求失败"))
        }
    }
}
