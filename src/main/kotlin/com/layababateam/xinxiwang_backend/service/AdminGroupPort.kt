package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity

/** 后台群组管理能力端口。SDK 负责路由和权限注解，接入方负责群组查询与管理实现。 */
interface AdminGroupPort {
    fun listGroups(page: Int, size: Int, keyword: String?): ResponseEntity<ApiResponse<Any>>

    fun getGroupDetail(id: String): ResponseEntity<ApiResponse<Any>>

    fun getGroupMembers(id: String, page: Int, size: Int, keyword: String?): ResponseEntity<ApiResponse<Any>>

    fun updateGroup(request: HttpServletRequest, id: String, body: Map<String, Any>): ResponseEntity<ApiResponse<Nothing>>

    fun disbandGroup(request: HttpServletRequest, id: String): ResponseEntity<ApiResponse<Nothing>>

    fun kickMember(request: HttpServletRequest, id: String, userId: String): ResponseEntity<ApiResponse<Nothing>>

    fun transferOwner(
        request: HttpServletRequest,
        id: String,
        body: Map<String, String>,
    ): ResponseEntity<ApiResponse<Nothing>>

    fun setVirtualMemberCount(adminId: String, groupId: String, count: Int): List<Map<String, Any?>> {
        throw UnsupportedOperationException("Group virtual members are not implemented by this AdminGroupPort")
    }
}
