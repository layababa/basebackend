package com.layababateam.xinxiwang_backend.service

data class WalletTransferCommand(
    val receiverId: String,
    val amount: String,
    val conversationId: String,
    val paymentPassword: String,
    val remark: String,
)

data class WalletRedPacketCommand(
    val conversationId: String,
    val totalAmount: String,
    val count: Int,
    val rpType: Int,
    val greeting: String,
    val targetUserId: String?,
    val paymentPassword: String,
)

data class WalletRealtimeResult(
    val type: String,
    val success: Boolean? = null,
    val data: Any? = null,
    val message: String? = null,
)

interface WalletRealtimePort {
    fun transfer(userId: String, command: WalletTransferCommand): WalletRealtimeResult

    fun sendRedPacket(userId: String, command: WalletRedPacketCommand): WalletRealtimeResult

    fun claimRedPacket(userId: String, redPacketId: String): WalletRealtimeResult

    fun getRedPacketInfo(userId: String, redPacketId: String): WalletRealtimeResult
}
