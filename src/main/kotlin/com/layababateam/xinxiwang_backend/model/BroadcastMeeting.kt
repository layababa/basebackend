package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 宣讲大会（Broadcast）状态枚举。对应契约 §1.1。
 * 注意：必须以字符串形式持久化，前端契约用字符串。
 */
enum class BroadcastStatus {
    draft,
    scheduled,
    waiting,
    live,
    ended,
    cancelled;

    companion object {
        /**
         * 合法状态转移表。返回 true 表示允许 from -> to。
         */
        fun canTransition(from: BroadcastStatus, to: BroadcastStatus): Boolean {
            return when (from) {
                draft -> to == live || to == waiting || to == scheduled || to == cancelled
                scheduled -> to == waiting || to == cancelled || to == live
                waiting -> to == live || to == cancelled || to == ended
                live -> to == ended
                ended -> false
                cancelled -> false
            }
        }
    }
}

/**
 * 宣讲大会主表。命名带 BroadcastMeeting 前缀以避免与现有
 * BroadcastService（RabbitMQ 全员推送）混淆。
 */
@Document(collection = "broadcast_meetings")
data class BroadcastMeeting(
    @Id
    val id: String? = null,
    val title: String,
    val description: String? = null,
    val coverUrl: String? = null,
    @Indexed
    val status: String = BroadcastStatus.draft.name,
    @Indexed
    val creatorId: String,
    val speakerId: String,
    val adminIds: List<String> = emptyList(),
    /** 预约时间，毫秒时间戳 */
    val scheduledAt: Long? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    /** BCrypt 哈希；null 表示不设密码 */
    val passwordHash: String? = null,
    val maxViewers: Int = 5000,
    val viewerCount: Int = 0,
    val peakViewerCount: Int = 0,
    val likeCount: Long = 0,
    val allMuted: Boolean = false,
    val mutedUserIds: List<String> = emptyList(),
    val bannedUserIds: List<String> = emptyList(),
    /** TRTC 房间号（数字字符串） */
    val trtcRoomId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 软归档标记，> 24h 已结束的宣讲会被置为 true */
    val archived: Boolean = false,
    @Version
    val version: Long = 0
)
