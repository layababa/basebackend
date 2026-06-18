package com.layababateam.xinxiwang_backend.service

interface WalletTransactionConsumerPort {
    fun persistWalletTransaction(event: WalletTransactionEvent)
}

data class WalletTransactionEvent(
    val userId: String,
    val type: Int,
    val amount: String,
    val counterpartyId: String?,
    val counterpartyName: String?,
    val txHash: String?,
    val address: String?,
    val redPacketId: String?,
    val status: Int,
    val remark: String,
)
