package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.SetPaymentPasswordRequest
import com.layababateam.xinxiwang_backend.dto.VerifyPaymentPasswordRequest
import com.layababateam.xinxiwang_backend.dto.WithdrawRequest
import com.layababateam.xinxiwang_backend.service.WalletPort
import com.layababateam.xinxiwang_backend.service.WalletResult
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/wallet")
class WalletController(
    private val walletPort: WalletPort,
) {
    @PostMapping("/create-address")
    fun createAddress(request: HttpServletRequest): ResponseEntity<ApiResponse<Any>> {
        val userId = request.getAttribute("userId") as String
        return response(walletPort.createAddress(userId))
    }

    @GetMapping("/balance")
    fun getBalance(request: HttpServletRequest): ResponseEntity<ApiResponse<Any>> {
        val userId = request.getAttribute("userId") as String
        return response(walletPort.getBalance(userId))
    }

    @PostMapping("/withdraw")
    fun withdraw(
        request: HttpServletRequest,
        @RequestBody body: WithdrawRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val userId = request.getAttribute("userId") as String
        return response(walletPort.withdraw(userId, body))
    }

    @PostMapping("/set-payment-password")
    fun setPaymentPassword(
        request: HttpServletRequest,
        @RequestBody body: SetPaymentPasswordRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val userId = request.getAttribute("userId") as String
        return response(walletPort.setPaymentPassword(userId, body))
    }

    @PostMapping("/verify-payment-password")
    fun verifyPaymentPassword(
        request: HttpServletRequest,
        @RequestBody body: VerifyPaymentPasswordRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val userId = request.getAttribute("userId") as String
        return response(walletPort.verifyPaymentPassword(userId, body))
    }

    @GetMapping("/transactions")
    fun getTransactions(
        request: HttpServletRequest,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "all") type: String,
    ): ResponseEntity<ApiResponse<Any>> {
        val userId = request.getAttribute("userId") as String
        return response(walletPort.getTransactions(userId, page, limit.coerceIn(1, 100), type))
    }

    @PostMapping("/webhook/deposit")
    fun webhookDeposit(
        @RequestHeader("X-Timestamp") timestamp: String?,
        @RequestHeader("X-Signature") signature: String?,
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<String> {
        val result = walletPort.handleDepositWebhook(timestamp, signature, body)
        return ResponseEntity.status(result.status).body(result.body)
    }

    private fun response(result: WalletResult): ResponseEntity<ApiResponse<Any>> {
        return ResponseEntity.status(result.httpStatus)
            .body(ApiResponse(success = result.success, message = result.message, data = result.data))
    }
}
