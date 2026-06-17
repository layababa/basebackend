package com.layababateam.xinxiwang_backend.dto

import com.layababateam.xinxiwang_backend.model.BroadcastMeeting

/**
 * 宣讲大会全套 DTO。与契约 §1.3 / §1.4 / §2.x 对齐。
 *
 * 时间字段：契约写 ISO 8601；MVP 阶段为简化，统一返回毫秒时间戳 epochMs，
 * 客户端已熟悉这种格式（与 Meeting/Message 一致）。
 */

// ─── 创建/编辑请求 ──────────────────────────────────

data class CreateBroadcastRequest(
    val title: String,
    val description: String? = null,
    val coverUrl: String? = null,
    /** "immediate" | "scheduled" */
    val startMode: String = "immediate",
    val scheduledAt: Long? = null,
    val password: String? = null,
    val maxViewers: Int? = null
)

data class EditBroadcastRequest(
    val title: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val scheduledAt: Long? = null,
    val password: String? = null
)

data class JoinBroadcastRequest(
    val password: String? = null,
    val forceLeaveOther: Boolean = false
)

data class UserIdRequest(
    val userId: String,
    val reason: String? = null
)

data class TransferSpeakerRequest(
    val newSpeakerId: String
)

data class BarrageCheckRequest(
    val content: String,
    val clientMsgId: String? = null
)

data class LikeReportRequest(
    val count: Int = 1
)

// ─── 响应体 ──────────────────────────────────────

data class BroadcastSpeakerInfo(
    val userId: String,
    val nickname: String,
    val avatar: String
)

data class BroadcastPermissions(
    val canEnd: Boolean,
    val canKick: Boolean,
    val canMute: Boolean,
    val canMuteAll: Boolean,
    val canTransferSpeaker: Boolean,
    val canSendRedPacket: Boolean,
    val canStartScreenShare: Boolean,
    val canSendBarrage: Boolean,
    val canRaiseHand: Boolean
)

/** 完整宣讲 DTO，对应契约 §1.3。 */
data class BroadcastDto(
    val id: String,
    val title: String,
    val description: String?,
    val coverUrl: String?,
    val status: String,
    val creatorId: String,
    val speakerId: String,
    val speakerInfo: BroadcastSpeakerInfo?,
    val adminIds: List<String>,
    val scheduledAt: Long?,
    val startedAt: Long?,
    val endedAt: Long?,
    val hasPassword: Boolean,
    val maxViewers: Int,
    val viewerCount: Int,
    val peakViewerCount: Int,
    val likeCount: Long,
    val allMuted: Boolean,
    val mutedUserIds: List<String>,
    val bannedUserIds: List<String>,
    val trtcRoomId: String,
    val trtcSdkAppId: Long,
    val userSig: String?,
    val myRole: String,
    val myPermissions: BroadcastPermissions,
    val createdAt: Long,
    val updatedAt: Long,
    /** 服务端时间，供客户端做倒计时校准 */
    val serverTime: Long
)

/** 列表简版 DTO。 */
data class BroadcastListItemDto(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val status: String,
    val speakerInfo: BroadcastSpeakerInfo?,
    val viewerCount: Int,
    val scheduledAt: Long?,
    val startedAt: Long?,
    val hasPassword: Boolean
)

/** 加入宣讲响应。 */
data class JoinBroadcastResponse(
    val broadcast: BroadcastDto,
    val kickBanned: Boolean = false
)

/** 观众/参与者 DTO，对应契约 §1.4。 */
data class BroadcastViewerDto(
    val userId: String,
    val nickname: String,
    val avatar: String,
    val role: String,
    val joinedAt: Long,
    val leftAt: Long? = null,
    val kickedAt: Long? = null,
    val isMuted: Boolean,
    val isOnMic: Boolean,
    val raiseHandAt: Long?
)

/** 连麦状态查询响应。 */
data class LinkMicStateDto(
    val activeUsers: List<BroadcastViewerDto>,
    val queue: List<BroadcastViewerDto>
)

/** 弹幕校验响应。 */
data class BarrageCheckResponse(
    val allowed: Boolean,
    val reason: String? = null
)

/** 气泡卡片状态快照。 */
data class BroadcastCardSnapshotDto(
    val id: String,
    val status: String,
    val viewerCount: Int,
    val speakerNickname: String,
    val scheduledAt: Long?
)
