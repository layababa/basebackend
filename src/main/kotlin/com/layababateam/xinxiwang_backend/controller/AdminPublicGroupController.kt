package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.service.AdminPublicGroupPort
import com.layababateam.xinxiwang_backend.service.PaginationRules
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/public-groups")
class AdminPublicGroupController(
    private val adminPublicGroupPort: AdminPublicGroupPort,
) {

    @RequireAdmin
    @GetMapping("/applies")
    fun listApplies(
        @RequestParam(required = false) status: Int?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PagedData<*>>> {
        val safePage = PaginationRules.zeroBasedPage(page)
        val safeSize = PaginationRules.pageSize(size, 100)
        val pageable = PageRequest.of(safePage, safeSize)
        val result = adminPublicGroupPort.listPublicGroupApplies(status, pageable)

        return ResponseEntity.ok(
            ApiResponse.ok(
                PagedData(
                    items = result.content,
                    total = result.totalElements,
                    page = safePage,
                    size = safeSize,
                ),
            ),
        )
    }

    @RequireAdmin
    @PutMapping("/applies/{id}/accept")
    fun acceptApply(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> {
        val admin = request.adminContext()
        return handlePublicGroupAction {
            ApiResponse.ok(
                adminPublicGroupPort.acceptPublicGroupApply(id, admin.id, admin.username),
                "申请已通过",
            )
        }
    }

    @RequireAdmin
    @PutMapping("/applies/{id}/reject")
    fun rejectApply(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<*>> {
        val admin = request.adminContext()
        return handlePublicGroupAction {
            ApiResponse.ok(
                adminPublicGroupPort.rejectPublicGroupApply(id, admin.id, admin.username),
                "申请已拒绝",
            )
        }
    }

    @RequireAdmin
    @PutMapping("/{groupId}/top")
    fun topGroup(
        request: HttpServletRequest,
        @PathVariable groupId: String,
    ): ResponseEntity<ApiResponse<*>> {
        val admin = request.adminContext()
        return handlePublicGroupAction {
            adminPublicGroupPort.topPublicGroup(groupId, admin.id, admin.username)
            ApiResponse.ok<Unit>(message = "置顶成功")
        }
    }

    @RequireAdmin
    @PutMapping("/{groupId}/cancel-top")
    fun cancelTopGroup(
        request: HttpServletRequest,
        @PathVariable groupId: String,
    ): ResponseEntity<ApiResponse<*>> {
        val admin = request.adminContext()
        return handlePublicGroupAction {
            adminPublicGroupPort.cancelTopPublicGroup(groupId, admin.id, admin.username)
            ApiResponse.ok<Unit>(message = "取消置顶成功")
        }
    }

    @RequireAdmin
    @PutMapping("/{groupId}/close")
    fun closePublicGroup(
        request: HttpServletRequest,
        @PathVariable groupId: String,
    ): ResponseEntity<ApiResponse<*>> {
        val admin = request.adminContext()
        return handlePublicGroupAction {
            adminPublicGroupPort.closePublicGroupOperation(groupId, admin.id, admin.username)
            ApiResponse.ok<Unit>(message = "已取消公开")
        }
    }

    private fun HttpServletRequest.adminContext(): AdminContext {
        return AdminContext(
            id = getAttribute("adminId") as String,
            username = getAttribute("adminUsername") as? String ?: "",
        )
    }

    private fun handlePublicGroupAction(action: () -> ApiResponse<*>): ResponseEntity<ApiResponse<*>> {
        return try {
            ResponseEntity.ok(action())
        } catch (error: Exception) {
            val (status, errorCode) = when (error) {
                is IllegalArgumentException -> 404 to ErrorCode.NOT_FOUND
                is IllegalStateException -> 400 to ErrorCode.INVALID_PARAM
                else -> 500 to ErrorCode.UNKNOWN_ERROR
            }
            ResponseEntity.status(status).body(
                ApiResponse.error<Any>(errorCode, error.message ?: "操作失败"),
            )
        }
    }

    private data class AdminContext(
        val id: String,
        val username: String,
    )
}
