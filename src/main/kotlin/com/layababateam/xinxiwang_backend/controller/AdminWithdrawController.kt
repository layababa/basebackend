package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.service.AdminWithdrawService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class RejectWithdrawRequest(
    val reason: String
)

@RestController
@RequestMapping("/api/admin/withdrawals")
class AdminWithdrawController(
    private val adminWithdrawService: AdminWithdrawService
) {

    @RequireAdmin
    @GetMapping
    fun listWithdrawals(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: Int?,
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) keyword: String?
    ): ResponseEntity<ApiResponse<PagedData<*>>> {
        return try {
            val records = adminWithdrawService.listRecords(page, size, status, userId, keyword)
            ResponseEntity.ok(ApiResponse.ok(
                PagedData(
                    items = records.content,
                    total = records.totalElements,
                    page = page,
                    size = size
                )
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                ApiResponse.error(ErrorCode.INVALID_PARAM, e.message ?: "查询失败")
            )
        }
    }

    @RequireAdmin
    @GetMapping("/{id}")
    fun getWithdrawDetail(
        @PathVariable id: String
    ): ResponseEntity<ApiResponse<*>> {
        return try {
            val record = adminWithdrawService.getRecord(id)
            ResponseEntity.ok(ApiResponse.ok(record))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(404).body(
                ApiResponse.error<Any>(ErrorCode.NOT_FOUND, e.message ?: "提现记录不存在")
            )
        }
    }

    @RequireAdmin
    @PutMapping("/{id}/approve")
    fun approveWithdraw(
        request: HttpServletRequest,
        @PathVariable id: String
    ): ResponseEntity<ApiResponse<*>> {
        val adminId = request.getAttribute("adminId") as String
        val adminUsername = request.getAttribute("adminUsername") as? String ?: ""

        return try {
            adminWithdrawService.approve(id, adminId, adminUsername)
            ResponseEntity.ok(ApiResponse.ok<Unit>(message = "提现审批通过"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(404).body(
                ApiResponse.error<Any>(ErrorCode.NOT_FOUND, e.message ?: "记录不存在")
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message ?: "操作失败")
            )
        }
    }

    @RequireAdmin
    @PutMapping("/{id}/reject")
    fun rejectWithdraw(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: RejectWithdrawRequest
    ): ResponseEntity<ApiResponse<*>> {
        val adminId = request.getAttribute("adminId") as String
        val adminUsername = request.getAttribute("adminUsername") as? String ?: ""

        if (body.reason.isBlank()) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "驳回原因不能为空")
            )
        }

        return try {
            adminWithdrawService.reject(id, adminId, adminUsername, body.reason)
            ResponseEntity.ok(ApiResponse.ok<Unit>(message = "提现审批已驳回"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(404).body(
                ApiResponse.error<Any>(ErrorCode.NOT_FOUND, e.message ?: "记录不存在")
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message ?: "操作失败")
            )
        }
    }
}
