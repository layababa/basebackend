package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 宣讲参与者。每行表示一个用户在某场宣讲中的状态。
 * 用户离开/被踢时设 leftAt 时间戳（软删除），不做硬删除，以保留 Admin 后台观众审计记录。
 * leftAt == null 表示当前仍在房间内。
 */
@Document(collection = "broadcast_participants")
@CompoundIndex(name = "uniq_broadcast_user", def = "{'broadcastId': 1, 'userId': 1}", unique = true)
data class BroadcastParticipant(
    @Id
    val id: String? = null,
    @Indexed
    val broadcastId: String,
    @Indexed
    val userId: String,
    /** admin / speaker / viewer / linked_mic_viewer */
    val role: String = "viewer",
    val joinedAt: Long = System.currentTimeMillis(),
    /** 离开时间戳，null 表示仍在房间 */
    val leftAt: Long? = null,
    /** 被踢时间戳，null 表示未被踢 */
    val kickedAt: Long? = null,
    val isMuted: Boolean = false,
    val isOnMic: Boolean = false,
    /** 举手时间戳，null 表示未举手 */
    val raiseHandAt: Long? = null,
    /** 举手被拒绝时间戳，用于 5s 冷却 */
    val raiseHandRejectedAt: Long? = null
)
