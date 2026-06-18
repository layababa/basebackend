package com.layababateam.xinxiwang_backend.service

interface RedPacketClaimConsumerPort {
    fun claimRedPacket(event: RedPacketClaimEvent)
}

data class RedPacketClaimEvent(
    val redPacketId: String,
    val userId: String,
    val amount: String,
    val userName: String,
)
