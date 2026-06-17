package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.GroupActivityPort
import com.layababateam.xinxiwang_backend.service.GroupActivityQueryResult
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 群接龙/群签到的只读 REST 接口，写操作走 WebSocket。 */
@RestController
@RequestMapping("/api/group-activity")
class GroupActivityController(
    private val groupActivityPort: GroupActivityPort,
) {
    @GetMapping("/relay/{id}")
    fun getRelay(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        return groupActivityPort.getRelay(userId(request), id).toResponse()
    }

    @GetMapping("/relay/list")
    fun listRelay(
        request: HttpServletRequest,
        @RequestParam conversationId: String,
    ): ResponseEntity<ApiResponse<List<Map<String, Any?>>>> {
        return groupActivityPort.listRelays(userId(request), conversationId).toResponse()
    }

    @GetMapping("/checkin/{id}")
    fun getCheckin(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        return groupActivityPort.getCheckin(userId(request), id).toResponse()
    }

    @GetMapping("/checkin/list")
    fun listCheckin(
        request: HttpServletRequest,
        @RequestParam conversationId: String,
    ): ResponseEntity<ApiResponse<List<Map<String, Any?>>>> {
        return groupActivityPort.listCheckins(userId(request), conversationId).toResponse()
    }

    private fun userId(request: HttpServletRequest): String =
        request.getAttribute("userId") as String

    private fun <T> GroupActivityQueryResult<T>.toResponse(): ResponseEntity<ApiResponse<T>> {
        val message = errorMessage
        if (message != null) {
            return ResponseEntity.ok(ApiResponse.error(message))
        }
        return ResponseEntity.ok(ApiResponse.ok(data))
    }
}
