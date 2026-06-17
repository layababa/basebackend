package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

object BroadcastLuckyBagStatus {
    const val ACTIVE = "active"
    const val DRAWN = "drawn"
    const val CANCELLED = "cancelled"
}

object BroadcastLuckyBagMode {
    const val FREE = "free"
    const val BARRAGE = "barrage"
}

/** 福袋中奖者快照。 */
data class LuckyBagWinner(
    val userId: String,
    val nickname: String = ""
)

/**
 * 宣讲福袋。契约 §1.6。
 */
@Document(collection = "broadcast_lucky_bags")
data class BroadcastLuckyBag(
    @Id
    val id: String? = null,
    @Indexed
    val broadcastId: String,
    val senderId: String,
    val senderNickname: String = "",
    val prizeName: String,
    val prizeCount: Int,
    val totalPoints: Long,
    val participationMode: String = BroadcastLuckyBagMode.FREE,
    val participationKeyword: String? = null,
    val durationSec: Int,
    @Indexed
    val status: String = BroadcastLuckyBagStatus.ACTIVE,
    val drawAt: Long,
    val participantIds: List<String> = emptyList(),
    val winners: List<LuckyBagWinner> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val refunded: Boolean = false,
    @Version
    val version: Long = 0
)
