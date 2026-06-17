package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.AdminRedPacketReconcilePort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 红包对账后台接口。
 *
 * 默认 dryRun=true，只看不改；实际对账基线与落库修复由接入方端口实现。
 */
@RestController
@RequestMapping("/api/admin/redpackets")
class AdminRedPacketController(
    private val reconcilePort: AdminRedPacketReconcilePort,
) {
    @RequireAdmin
    @PostMapping("/{id}/reconcile")
    fun reconcileOne(
        @PathVariable id: String,
        @RequestParam(defaultValue = "true") dryRun: Boolean,
    ): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(reconcilePort.reconcile(id, dryRun)))
    }

    @RequireAdmin
    @PostMapping("/reconcile/scan")
    fun scan(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "true") dryRun: Boolean,
    ): ResponseEntity<ApiResponse<*>> {
        val reports = reconcilePort.scan(limit, dryRun)
        val summary = mapOf(
            "total" to reports.size,
            "applied" to reports.count { it.applied },
            "skippedAligned" to reports.count { it.skipped && it.skipReason == "已对齐，无需修改" },
            "skippedRedisGone" to reports.count {
                it.skipped && it.skipReason?.startsWith("Redis key 已过期") == true
            },
            "needsRepair" to reports.count { !it.applied && !it.skipped },
            "reports" to reports,
        )
        return ResponseEntity.ok(ApiResponse.ok(summary))
    }
}
