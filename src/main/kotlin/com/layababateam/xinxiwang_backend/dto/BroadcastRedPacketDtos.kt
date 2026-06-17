package com.layababateam.xinxiwang_backend.dto

// ─── 红包 ─────────────────────────────────────

data class CreateRedPacketRequest(
    val type: String = "lucky",       // lucky | equal
    val totalPoints: Long,
    val totalCount: Int,
    val blessing: String = "恭喜发财"
)

data class RedPacketDto(
    val id: String,
    val broadcastId: String,
    val senderId: String,
    val senderNickname: String,
    val type: String,
    val totalPoints: Long,
    val totalCount: Int,
    val remainingCount: Int,
    val grabbedCount: Int,
    val blessing: String,
    val status: String,
    val expiresAt: Long,
    val createdAt: Long,
    val myGrabResult: Long?
)

data class GrabRedPacketResponse(
    /** won | empty | duplicate | expired */
    val result: String,
    val points: Long? = null
)

// ─── 福袋 ─────────────────────────────────────

data class CreateLuckyBagRequest(
    val prizeName: String,
    val prizeCount: Int,
    val totalPoints: Long,
    val participationMode: String = "free",
    val participationKeyword: String? = null,
    val durationSec: Int = 60
)

data class LuckyBagWinnerDto(
    val userId: String,
    val nickname: String
)

data class LuckyBagDto(
    val id: String,
    val broadcastId: String,
    val senderId: String,
    val senderNickname: String,
    val prizeName: String,
    val prizeCount: Int,
    val totalPoints: Long,
    val participationMode: String,
    val participationKeyword: String?,
    val durationSec: Int,
    val status: String,
    val drawAt: Long,
    val participantCount: Int,
    val winners: List<LuckyBagWinnerDto>,
    val myParticipated: Boolean,
    val myWon: Boolean
)

data class OperatorBalanceDto(
    val balance: Long,
    val currency: String = "points"
)
