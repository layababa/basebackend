package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity

/** 积分查询能力端口。SDK 负责路由，接入方负责账户、流水和分页查询实现。 */
interface PointsPort {
    fun adminTransactions(
        page: Int,
        size: Int,
        userId: String?,
        reason: String?,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>>

    fun myPoints(request: HttpServletRequest, page: Int, size: Int): ResponseEntity<ApiResponse<Map<String, Any?>>>
}
