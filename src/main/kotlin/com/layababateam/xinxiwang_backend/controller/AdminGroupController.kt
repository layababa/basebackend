package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.service.AdminGroupPort
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/groups")
class AdminGroupController(
    private val adminGroupPort: AdminGroupPort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping
    fun listGroups(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<ApiResponse<Any>> {
        // 兼容 admin 签到编辑页"适用群"选择器以 limit 传分页大小。
        return adminGroupPort.listGroups(page, limit ?: size, keyword)
    }

    @RequireAdmin("ADMIN")
    @GetMapping("/{id}")
    fun getGroupDetail(@PathVariable id: String): ResponseEntity<ApiResponse<Any>> =
        adminGroupPort.getGroupDetail(id)

    @RequireAdmin("ADMIN")
    @GetMapping("/{id}/members")
    fun getGroupMembers(
        @PathVariable id: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<ApiResponse<Any>> =
        adminGroupPort.getGroupMembers(id, page, size, keyword)

    @RequireAdmin("ADMIN")
    @PutMapping("/{id}")
    fun updateGroup(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<ApiResponse<Nothing>> =
        adminGroupPort.updateGroup(request, id, body)

    @RequireAdmin("ADMIN")
    @DeleteMapping("/{id}")
    fun disbandGroup(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<Nothing>> =
        adminGroupPort.disbandGroup(request, id)

    @RequireAdmin("ADMIN")
    @DeleteMapping("/{id}/members/{userId}")
    fun kickMember(
        request: HttpServletRequest,
        @PathVariable id: String,
        @PathVariable userId: String,
    ): ResponseEntity<ApiResponse<Nothing>> =
        adminGroupPort.kickMember(request, id, userId)

    @RequireAdmin("ADMIN")
    @PutMapping("/{id}/transfer")
    fun transferOwner(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<ApiResponse<Nothing>> =
        adminGroupPort.transferOwner(request, id, body)

    @RequireAdmin("ADMIN")
    @PutMapping("/{id}/virtual-members")
    fun setVirtualMembers(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: AdminSetVirtualCountRequest,
    ): ResponseEntity<ApiResponse<Any>> =
        try {
            val adminId = request.getAttribute("adminId") as? String ?: "admin"
            val members = adminGroupPort.setVirtualMemberCount(adminId, id, body.count)
            val virtualCount = members.count(::isVirtualMember)
            ResponseEntity.ok(
                ApiResponse.ok(
                    mapOf(
                        "virtualMemberCount" to virtualCount,
                        "realMemberCount" to members.size - virtualCount,
                        "memberCount" to members.size,
                        "members" to members,
                    ),
                    "虚拟成员数量已更新",
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                ApiResponse.error(ErrorCode.INVALID_PARAM, e.message ?: "虚拟成员数量更新失败"),
            )
        } catch (e: UnsupportedOperationException) {
            ResponseEntity.status(503).body(
                ApiResponse.error(ErrorCode.SERVICE_UNAVAILABLE, e.message),
            )
        }

    private fun isVirtualMember(member: Map<String, Any?>): Boolean =
        when (val value = member["isVirtual"] ?: member["is_virtual"]) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }
}
