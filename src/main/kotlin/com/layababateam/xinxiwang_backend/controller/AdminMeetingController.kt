package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.MeetingDto
import com.layababateam.xinxiwang_backend.dto.MeetingJoinResponse
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.dto.ParticipantDto
import com.layababateam.xinxiwang_backend.service.AdminMeetingPort
import com.layababateam.xinxiwang_backend.service.PaginationRules
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/meetings")
class AdminMeetingController(
    private val adminMeetingPort: AdminMeetingPort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping
    fun listMeetings(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: Int?,
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<ApiResponse<PagedData<MeetingDto>>> {
        val clampedSize = PaginationRules.pageSize(size, MAX_PAGE_SIZE)
        val clampedPage = PaginationRules.zeroBasedPage(page)
        val (items, total) = adminMeetingPort.getAllMeetings(clampedPage, clampedSize, status, keyword)
        return ResponseEntity.ok(
            ApiResponse.ok(
                PagedData(
                    items = items,
                    total = total,
                    page = clampedPage,
                    size = clampedSize,
                ),
            ),
        )
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/{id}")
    fun getMeetingDetail(@PathVariable id: String): ResponseEntity<ApiResponse<MeetingDto>> =
        try {
            ResponseEntity.ok(ApiResponse.ok(adminMeetingPort.getMeeting(id)))
        } catch (_: IllegalArgumentException) {
            ResponseEntity.status(404)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, "会议不存在"))
        }

    @RequireAdmin("ADMIN")
    @GetMapping("/{id}/participants")
    fun getParticipants(@PathVariable id: String): ResponseEntity<ApiResponse<List<ParticipantDto>>> =
        try {
            ResponseEntity.ok(ApiResponse.ok(adminMeetingPort.getParticipants(id)))
        } catch (_: IllegalArgumentException) {
            ResponseEntity.status(404)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, "会议不存在"))
        }

    @RequireAdmin("ADMIN")
    @GetMapping("/{id}/history-participants")
    fun getHistoryParticipants(@PathVariable id: String): ResponseEntity<ApiResponse<List<ParticipantDto>>> =
        try {
            ResponseEntity.ok(ApiResponse.ok(adminMeetingPort.getHistoricalParticipants(id)))
        } catch (_: IllegalArgumentException) {
            ResponseEntity.status(404)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, "会议不存在"))
        }

    @RequireAdmin("ADMIN")
    @PostMapping("/{id}/end")
    fun forceEndMeeting(@PathVariable id: String): ResponseEntity<ApiResponse<Nothing>> =
        try {
            adminMeetingPort.forceEndMeeting(id)
            ResponseEntity.ok(ApiResponse.ok(message = "会议已强制结束"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(404)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message ?: "会议不存在或已结束"))
        }

    @RequireAdmin("ADMIN")
    @PostMapping("/{id}/join")
    fun adminJoinMeeting(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<MeetingJoinResponse>> {
        val adminId = request.getAttribute("adminId") as String
        return try {
            ResponseEntity.ok(ApiResponse.ok(adminMeetingPort.adminJoinMeeting(adminId, id)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(404)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message ?: "会议不存在或已结束"))
        }
    }

    private companion object {
        const val MAX_PAGE_SIZE = 50
    }
}
