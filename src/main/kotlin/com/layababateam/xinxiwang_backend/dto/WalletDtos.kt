package com.layababateam.xinxiwang_backend.dto

data class WithdrawRequest(
    val toAddress: String,
    val amount: String,
    val paymentPassword: String,
)

data class SetPaymentPasswordRequest(
    val password: String,
    val oldPassword: String? = null,
)

data class VerifyPaymentPasswordRequest(
    val password: String,
)
