package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.SetPaymentPasswordRequest
import com.layababateam.xinxiwang_backend.dto.VerifyPaymentPasswordRequest
import com.layababateam.xinxiwang_backend.dto.WithdrawRequest

/**
 * 钱包开放接口端口。
 *
 * SDK 复用钱包 HTTP 契约，链上地址、提现、支付密码、充值回调签名和通知由接入方实现。
 */
interface WalletPort {
    fun createAddress(userId: String): WalletResult

    fun getBalance(userId: String): WalletResult

    fun withdraw(userId: String, request: WithdrawRequest): WalletResult

    fun setPaymentPassword(userId: String, request: SetPaymentPasswordRequest): WalletResult

    fun verifyPaymentPassword(userId: String, request: VerifyPaymentPasswordRequest): WalletResult

    fun getTransactions(userId: String, page: Int, limit: Int, type: String): WalletResult

    fun handleDepositWebhook(timestamp: String?, signature: String?, body: Map<String, Any?>): WalletWebhookResult
}

data class WalletResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val httpStatus: Int = if (success) 200 else 400,
) {
    companion object {
        fun ok(message: String = "OK", data: Any? = null): WalletResult =
            WalletResult(success = true, message = message, data = data)

        fun error(message: String, httpStatus: Int = 400): WalletResult =
            WalletResult(success = false, message = message, httpStatus = httpStatus)
    }
}

data class WalletWebhookResult(
    val status: Int,
    val body: String,
)
