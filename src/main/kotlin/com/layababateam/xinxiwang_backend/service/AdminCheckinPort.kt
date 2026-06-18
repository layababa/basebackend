package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity

/** 后台群签到活动管理能力端口。SDK 负责路由和权限注解，接入方负责活动规则和群组数据实现。 */
interface AdminCheckinPort {
    fun list(
        page: Int,
        size: Int,
        keyword: String?,
        status: Int?,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>>

    fun detail(id: String): ResponseEntity<ApiResponse<Map<String, Any?>>>

    fun create(request: HttpServletRequest, body: Map<String, Any?>): ResponseEntity<ApiResponse<Map<String, Any?>>>

    fun update(id: String, body: Map<String, Any?>): ResponseEntity<ApiResponse<Map<String, Any?>>>

    fun toggle(id: String, body: Map<String, Any?>): ResponseEntity<ApiResponse<Map<String, Any?>>>
}
