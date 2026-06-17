package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.AvUsageOverviewDto
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.MeetingAvUsageStatsDto
import com.layababateam.xinxiwang_backend.service.AdminAvUsagePort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/av-usage")
class AdminAvUsageController(
    private val adminAvUsagePort: AdminAvUsagePort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping("/overview")
    fun overview(
        @RequestParam(defaultValue = "7") days: Int,
    ): ResponseEntity<ApiResponse<AvUsageOverviewDto>> {
        return ResponseEntity.ok(ApiResponse.ok(adminAvUsagePort.getOverview(days)))
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/meetings/{meetingId}")
    fun meetingStats(
        @PathVariable meetingId: String,
    ): ResponseEntity<ApiResponse<MeetingAvUsageStatsDto>> {
        return try {
            ResponseEntity.ok(ApiResponse.ok(adminAvUsagePort.getMeetingStats(meetingId)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(404)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message ?: "会议不存在"))
        }
    }
}
