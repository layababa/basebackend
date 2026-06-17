package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/** 宣讲红包状态。 */
object BroadcastRedPacketStatus {
    const val ACTIVE = "active"
    const val EMPTY = "empty"
    const val EXPIRED = "expired"
}

/** 红包类型。 */
object BroadcastRedPacketType {
    const val LUCKY = "lucky"
    const val EQUAL = "equal"
}

/**
 * 宣讲场内红包。契约 §1.5。
 */
@Document(collection = "broadcast_red_packets")
data class BroadcastRedPacket(
    @Id
    val id: String? = null,
    @Indexed
    val broadcastId: String,
    val senderId: String,
    val senderNickname: String = "",
    /** lucky | equal */
    val type: String = BroadcastRedPacketType.LUCKY,
    val totalPoints: Long,
    val totalCount: Int,
    val remainingPoints: Long,
    val remainingCount: Int,
    val grabbedCount: Int = 0,
    val blessing: String = "",
    @Indexed
    val status: String = BroadcastRedPacketStatus.ACTIVE,
    /** 过期时间，毫秒时间戳 */
    val expiresAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val refunded: Boolean = false,
    @Version
    val version: Long = 0
)
