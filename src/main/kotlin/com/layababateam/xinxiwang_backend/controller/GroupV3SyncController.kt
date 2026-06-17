package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.GroupV3SyncRequest
import com.layababateam.xinxiwang_backend.dto.GroupV3SyncResponse
import com.layababateam.xinxiwang_backend.service.GroupV3SyncPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v3/sync")
class GroupV3SyncController(
    private val groupV3SyncPort: GroupV3SyncPort,
) {
    @PostMapping("/group")
    fun syncGroup(
        request: HttpServletRequest,
        @RequestBody body: GroupV3SyncRequest,
    ): ResponseEntity<ApiResponse<GroupV3SyncResponse>> {
        if (body.groupId.isBlank()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_PARAM, "groupId 不能为空"))
        }

        val result = groupV3SyncPort.syncGroup(userId(request), body)
        if (result.data != null) {
            return ResponseEntity.ok(ApiResponse.ok(result.data))
        }

        val status = HttpStatus.resolve(result.status) ?: HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(status).body(
            ApiResponse.error(
                result.errorCode ?: errorCodeFor(status),
                result.message,
            ),
        )
    }

    private fun userId(request: HttpServletRequest): String =
        request.getAttribute("userId") as String

    private fun errorCodeFor(status: HttpStatus): ErrorCode = when (status) {
        HttpStatus.UNAUTHORIZED -> ErrorCode.UNAUTHORIZED
        HttpStatus.FORBIDDEN -> ErrorCode.FORBIDDEN
        HttpStatus.NOT_FOUND -> ErrorCode.NOT_FOUND
        HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.TOO_MANY_REQUESTS -> ErrorCode.SERVICE_UNAVAILABLE
        else -> if (status.is4xxClientError) ErrorCode.INVALID_PARAM else ErrorCode.UNKNOWN_ERROR
    }
}
