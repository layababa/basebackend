package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.AdminBroadcastPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("legacy-broadcast")
@RequestMapping("/api/admin/broadcast")
class AdminBroadcastController(
    private val adminBroadcastPort: AdminBroadcastPort,
) {
    private fun adminId(request: HttpServletRequest): String = request.getAttribute("userId") as String

    @RequireAdmin
    @GetMapping("/broadcasts")
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) creatorId: String?,
        @RequestParam(required = false) speakerId: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
    ): ResponseEntity<ApiResponse<*>> {
        val pageData = adminBroadcastPort.list(status, creatorId, speakerId, keyword, page, pageSize)
        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf(
                    "total" to pageData.total,
                    "page" to page,
                    "pageSize" to pageSize,
                    "list" to pageData.content,
                )
            )
        )
    }

    @RequireAdmin
    @GetMapping("/broadcasts/{id}")
    fun detail(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to adminBroadcastPort.detail(id))))
    }

    @RequireAdmin
    @PostMapping("/broadcasts/{id}/end")
    fun forceEnd(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = adminBroadcastPort.forceEnd(adminId(request), id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @RequireAdmin
    @PostMapping("/broadcasts/{id}/cancel")
    fun forceCancel(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = adminBroadcastPort.forceCancel(adminId(request), id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @RequireAdmin
    @GetMapping("/broadcasts/{id}/viewers")
    fun viewers(
        @PathVariable id: String,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
    ): ResponseEntity<ApiResponse<*>> {
        val pageData = adminBroadcastPort.viewers(id, page, pageSize)
        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf(
                    "total" to pageData.total,
                    "page" to page,
                    "pageSize" to pageSize,
                    "list" to pageData.content,
                )
            )
        )
    }

    @RequireAdmin
    @GetMapping("/broadcasts/{id}/red-packets")
    fun redPackets(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(mapOf("list" to adminBroadcastPort.redPackets(id))))
    }

    @RequireAdmin
    @GetMapping("/red-packets/{redPacketId}/grabs")
    fun redPacketGrabs(@PathVariable redPacketId: String): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(mapOf("list" to adminBroadcastPort.redPacketGrabs(redPacketId))))
    }

    @RequireAdmin
    @GetMapping("/broadcasts/{id}/lucky-bags")
    fun luckyBags(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(mapOf("list" to adminBroadcastPort.luckyBags(id))))
    }

    @RequireAdmin
    @GetMapping("/broadcasts/{id}/stats")
    fun stats(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        val stats = adminBroadcastPort.stats(id)
        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf(
                    "title" to stats.title,
                    "status" to stats.status,
                    "peakViewerCount" to stats.peakViewerCount,
                    "totalParticipants" to stats.totalParticipants,
                    "onMicCount" to stats.onMicCount,
                    "likeCount" to stats.likeCount,
                    "redPacketCount" to stats.redPacketCount,
                    "redPacketTotalGrabbed" to stats.redPacketTotalGrabbed,
                    "redPacketDistributedPoints" to stats.redPacketDistributedPoints,
                    "redPacketRefundedPoints" to stats.redPacketRefundedPoints,
                    "startedAt" to stats.startedAt,
                    "endedAt" to stats.endedAt,
                    "durationMs" to stats.durationMs,
                )
            )
        )
    }
}
