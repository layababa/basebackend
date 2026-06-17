package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.AdminAuditLogQuery
import com.layababateam.xinxiwang_backend.service.AdminDashboardPort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/dashboard")
class AdminDashboardController(
    private val adminDashboardPort: AdminDashboardPort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping("/stats")
    fun getOverviewStats(): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardPort.overviewStats()))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/stats/users")
    fun getUserGrowthTrend(@RequestParam(defaultValue = "7") days: Int): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardPort.userGrowthTrend(days)))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/stats/messages")
    fun getMessageVolumeTrend(@RequestParam(defaultValue = "7") days: Int): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardPort.messageVolumeTrend(days)))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/stats/online")
    fun getOnlineUserCount(): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardPort.onlineUserCount()))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/stats/groups")
    fun getGroupGrowthTrend(@RequestParam(defaultValue = "7") days: Int): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardPort.groupGrowthTrend(days)))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/stats/active-users")
    fun getActiveUsersTrend(@RequestParam(defaultValue = "7") days: Int): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardPort.activeUsersTrend(days)))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/stats/top-senders")
    fun getTopSenders(@RequestParam(defaultValue = "10") limit: Int): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardPort.topSenders(limit)))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/stats/top-groups")
    fun getTopGroups(@RequestParam(defaultValue = "10") limit: Int): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardPort.topGroups(limit)))
    }

    @RequireAdmin("SUPER_ADMIN")
    @GetMapping("/audit-logs")
    fun getAuditLogs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) adminUsername: String?,
        @RequestParam(required = false) targetType: String?,
        @RequestParam(required = false) targetId: String?,
        @RequestParam(required = false) ip: String?,
        @RequestParam(required = false) startAt: Long?,
        @RequestParam(required = false) endAt: Long?,
    ): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.ok(
            ApiResponse.ok(
                adminDashboardPort.auditLogs(
                    AdminAuditLogQuery(
                        page = page,
                        size = size,
                        eventType = eventType,
                        adminUsername = adminUsername,
                        targetType = targetType,
                        targetId = targetId,
                        ip = ip,
                        startAt = startAt,
                        endAt = endAt,
                    )
                )
            )
        )
    }
}
