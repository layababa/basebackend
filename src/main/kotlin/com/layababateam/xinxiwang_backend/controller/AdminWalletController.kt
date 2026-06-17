package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.AdminAdjustBalanceRequest
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.repository.WalletTransactionRepository
import com.layababateam.xinxiwang_backend.service.AdminWalletService
import com.layababateam.xinxiwang_backend.service.ExcelExportService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date

@RestController
@RequestMapping("/api/admin/users")
class AdminWalletController(
    private val userRepository: UserRepository,
    private val walletTransactionRepository: WalletTransactionRepository,
    private val adminWalletService: AdminWalletService,
    private val excelExportService: ExcelExportService,
) {
    @RequireAdmin
    @GetMapping("/{id}/wallet")
    fun getUserWallet(@PathVariable id: String): ResponseEntity<ApiResponse<Any>> {
        val user = userRepository.findById(id).orElse(null)
            ?: return ResponseEntity.status(404).body(
                ApiResponse.error(ErrorCode.NOT_FOUND, "用户不存在")
            )

        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf(
                    "userId" to user.id,
                    "bscAddress" to user.bscAddress,
                    "walletBalance" to user.walletBalance,
                )
            )
        )
    }

    @RequireAdmin
    @GetMapping("/{id}/wallet/transactions")
    fun getUserWalletTransactions(
        @PathVariable id: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PagedData<*>>> {
        val safeSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(page, safeSize)
        val transactions = walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(id, pageable)

        return ResponseEntity.ok(
            ApiResponse.ok(
                PagedData(
                    items = transactions.content,
                    total = transactions.totalElements,
                    page = page,
                    size = safeSize,
                )
            )
        )
    }

    @RequireAdmin
    @PostMapping("/{id}/wallet/adjust")
    fun adjustWalletBalance(
        request: HttpServletRequest,
        @PathVariable id: String,
        @Valid @RequestBody body: AdminAdjustBalanceRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val adminId = request.getAttribute("adminId") as String
        val adminUsername = request.getAttribute("adminUsername") as? String ?: ""

        if (body.type !in listOf("INCREASE", "DECREASE")) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "类型必须为 INCREASE 或 DECREASE")
            )
        }

        val amount = try {
            BigDecimal(body.amount)
        } catch (e: NumberFormatException) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "金额格式不正确")
            )
        }

        if (amount <= BigDecimal.ZERO) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "金额必须大于0")
            )
        }

        return adminWalletService.adjustBalance(
            userId = id,
            type = body.type,
            amount = amount,
            remark = body.remark,
            adminId = adminId,
            adminUsername = adminUsername,
        )
    }

    @RequireAdmin
    @GetMapping("/{id}/wallet/transactions/export")
    fun exportUserTransactions(
        response: HttpServletResponse,
        @PathVariable id: String,
    ) {
        val user = userRepository.findById(id).orElse(null)
        if (user == null) {
            response.status = 404
            return
        }

        val pageable = PageRequest.of(
            0,
            ExcelExportService.MAX_EXPORT_ROWS,
            Sort.by(Sort.Direction.DESC, "createdAt"),
        )
        val transactions = walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(id, pageable)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val typeNames = mapOf(
            0 to "充值",
            1 to "提现",
            2 to "转账收入",
            3 to "转账支出",
            4 to "红包发出",
            5 to "红包领取",
            6 to "红包退款",
        )
        val statusNames = mapOf(0 to "处理中", 1 to "成功", 2 to "失败")
        val headers = listOf("交易ID", "类型", "金额", "对方ID", "对方昵称", "状态", "备注", "时间")
        val rows = transactions.content.map { tx ->
            listOf(
                tx.id,
                typeNames[tx.type] ?: tx.type.toString(),
                tx.amount,
                tx.counterpartyId ?: "",
                tx.counterpartyName ?: "",
                statusNames[tx.status] ?: tx.status.toString(),
                tx.remark,
                dateFormat.format(Date(tx.createdAt)),
            )
        }

        val excelBytes = excelExportService.exportToExcel(headers, rows, "用户流水")
        val displayName = user.displayName ?: user.username ?: id

        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.setHeader("Content-Disposition", "attachment; filename=\"transactions_${displayName}.xlsx\"")
        response.outputStream.write(excelBytes)
        response.outputStream.flush()
    }
}
